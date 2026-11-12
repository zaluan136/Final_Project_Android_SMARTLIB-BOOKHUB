'use strict';
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, FieldValue} = require('firebase-admin/firestore');
const {onCall, HttpsError} = require('firebase-functions/v2/https');
const {onSchedule} = require('firebase-functions/v2/scheduler');
const {createHash} = require('node:crypto');
const P = require('./policy');
initializeApp();
const db = getFirestore();
const DEFAULTS = {maxLoans: 3, loanDays: 14, renewalDays: 7, maxRenewals: 1, dailyFine: 3000};
const id = value => { P.ensure(typeof value === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(value), 'Mã định danh không hợp lệ.'); return value; };
const key = (uid, bookId) => createHash('sha256').update(uid + ':' + bookId).digest('hex');
const policyRef = db.doc('settings/library');

async function mutate(user, data) {
  const command = data.command;
  P.ensure(['reserve','checkout','renew','return','cancel','expire'].includes(command), 'Thao tác không hợp lệ.');
  const requestId = id(data.requestId);
  const receipt = db.doc(`receipts/${key(user.uid, requestId)}`);
  const loanRef = command === 'reserve' ? db.collection('loans').doc() : db.doc(`loans/${id(data.loanId)}`);
  return db.runTransaction(async tx => {
    const cached = await tx.get(receipt);
    if (cached.exists) { P.ensure(cached.data().command === command, 'Mã yêu cầu đã được dùng cho thao tác khác.'); return cached.data().result; }
    const today = P.day();
    const policySnap = await tx.get(policyRef);
    const policy = P.rules(policySnap.exists ? policySnap.data() : DEFAULTS);
    let before, next, bookId, readerId;
    if (command === 'reserve') { bookId = id(data.bookId); readerId = user.uid; }
    else {
      const snap = await tx.get(loanRef);P.ensure(snap.exists, 'Không tìm thấy phiếu.');
      before = snap.data();bookId = before.bookId;readerId = before.readerId;
      next = P.transition(command, user, before, today, policy);
    }
    const bookRef = db.doc(`books/${bookId}`), readerRef = db.doc(`readers/${readerId}`), activeRef = db.doc(`activeRequests/${key(readerId, bookId)}`);
    const [bookSnap, readerSnap, activeSnap] = await Promise.all([tx.get(bookRef), tx.get(readerRef), tx.get(activeRef)]);
    P.ensure(bookSnap.exists, 'Không tìm thấy sách.');
    const book = bookSnap.data(), active = readerSnap.exists ? readerSnap.data().activeCount : 0;
    P.integer(active,0,10000,'Số phiếu');P.integer(book.copies,1,999,'Số bản');P.integer(book.available,0,book.copies,'Tồn kho');
    let delta = 0;
    if (command === 'reserve') {
      const borrowed = await tx.get(db.collection('loans').where('readerId','==',readerId).where('status','==','BORROWED'));
      P.reserve(user,book,active,borrowed.docs.some(d=>d.data().dueDay<today),data.pickupDay,today,policy);
      P.ensure(!activeSnap.exists, 'Bạn đã có phiếu đang xử lý cho sách này.');
      next = {readerId,bookId,title:book.title,status:'RESERVED',pickupDay:data.pickupDay,dueDay:0,renewals:0,finePerDay:0,fine:0,createdAt:FieldValue.serverTimestamp()};
      delta=1;
    } else {
      P.ensure(activeSnap.exists && activeSnap.data().loanId === loanRef.id, 'Dữ liệu phiếu không nhất quán.');
      if (['cancel','return','expire'].includes(command))delta=-1;
    }
    P.ensure(active+delta>=0 && book.available-delta>=0 && book.available-delta<=book.copies,'Dữ liệu tồn kho không nhất quán.');
    tx.set(loanRef,{...next,updatedAt:FieldValue.serverTimestamp()});
    if(delta!==0){
      tx.set(readerRef,{activeCount:active+delta},{merge:true});tx.update(bookRef,{available:book.available-delta});
      if(delta===1)tx.create(activeRef,{loanId:loanRef.id});else tx.delete(activeRef);
    }
    const result={loanId:loanRef.id,status:next.status};
    tx.create(receipt,{command,result,createdAt:FieldValue.serverTimestamp()});
    tx.create(db.collection('audit').doc(),{actorId:user.uid,command,loanId:loanRef.id,at:FieldValue.serverTimestamp()});
    return result;
  });
}
exports.bookhub = onCall({region:'asia-southeast1',enforceAppCheck:true,maxInstances:10},async request=>{
  try {const user=P.actor(request.auth);P.ensure(request.data && typeof request.data==='object','Thiếu nội dung yêu cầu.');return await mutate(user,request.data);}
  catch(error){if(error instanceof P.PolicyError)throw new HttpsError('failed-precondition',error.message);throw new HttpsError('internal','Không thể xử lý. Thử lại với cùng mã yêu cầu.');}
});
// Keep expired reservations from holding stock forever. Each expiration is transactional and idempotent.
exports.expireReservations = onSchedule({schedule:'every 30 minutes',timeZone:'Asia/Ho_Chi_Minh',region:'asia-southeast1',maxInstances:1},async()=>{
  const overdue=await db.collection('loans').where('status','==','RESERVED').where('pickupDay','<',P.day()).limit(200).get();
  for(const doc of overdue.docs){
    try{await mutate({uid:'scheduler',role:'system'},{command:'expire',loanId:doc.id,requestId:`expire_${doc.id}`});}
    catch(error){if(!(error instanceof P.PolicyError))throw error;}
  }
});

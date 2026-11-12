'use strict';
// Run on a trusted administrator workstation with Application Default Credentials.
// Never package this script or service-account credentials into the Android app.
const {initializeApp, applicationDefault} = require('firebase-admin/app');
const {getAuth} = require('firebase-admin/auth');
const {getFirestore} = require('firebase-admin/firestore');
const projectId = process.env.GOOGLE_CLOUD_PROJECT;
if (!projectId) throw new Error('Set GOOGLE_CLOUD_PROJECT explicitly before running an admin operation.');
initializeApp({credential:applicationDefault(),projectId});
async function main() {
  const [command,uid,role]=process.argv.slice(2);
  if(command==='role') {
    if(!uid || !['reader','librarian','admin'].includes(role))throw new Error('Usage: node tools/admin.js role UID reader|librarian|admin');
    const user=await getAuth().getUser(uid);
    await getAuth().setCustomUserClaims(uid,{...user.customClaims,role});
    await getAuth().revokeRefreshTokens(uid);
    console.log('Role updated. The user must sign in again; existing ID tokens may remain valid until expiry.');
  } else if(command==='seed') {
    const db=getFirestore();const batch=db.batch();
    batch.create(db.doc('settings/library'),{maxLoans:3,loanDays:14,renewalDays:7,maxRenewals:1,dailyFine:3000});
    batch.create(db.doc('books/nha-gia-kim'),{title:'Nhà giả kim',author:'Paulo Coelho',isbn:'9780061122415',category:'Văn học',shelf:'A1 · Tầng 1',copies:3,available:3,archived:false});
    batch.create(db.doc('books/clean-code'),{title:'Clean Code',author:'Robert C. Martin',isbn:'9780132350884',category:'Công nghệ',shelf:'C3 · Tầng 2',copies:1,available:1,archived:false});
    await batch.commit();console.log('Created initial data. Existing data is never overwritten.');
  } else throw new Error('Supported operations: seed, role UID ROLE');
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});

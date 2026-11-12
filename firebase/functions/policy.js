'use strict';
class PolicyError extends Error {}
const ensure = (ok, message) => { if (!ok) throw new PolicyError(message); };
const integer = (v, min, max, label) => {
  ensure(Number.isSafeInteger(v) && v >= min && v <= max, `${label} không hợp lệ.`);
  return v;
};
function day(now = Date.now()) { return Math.floor((now + 7 * 3600000) / 86400000); }
function actor(auth) {
  ensure(auth && auth.uid && auth.token.email_verified === true, 'Cần đăng nhập và xác minh email.');
  return {uid: auth.uid, role: auth.token.role || 'reader'};
}
function staff(user) { ensure(['librarian', 'admin'].includes(user.role), 'Chỉ thủ thư hoặc quản trị được thao tác.'); }
function rules(input) {
  return {maxLoans: integer(input.maxLoans, 1, 20, 'Giới hạn phiếu'), loanDays: integer(input.loanDays, 1, 90, 'Số ngày mượn'), renewalDays: integer(input.renewalDays, 1, 30, 'Ngày gia hạn'), maxRenewals: integer(input.maxRenewals, 0, 5, 'Lần gia hạn'), dailyFine: integer(input.dailyFine, 0, 100000, 'Phí quá hạn')};
}
function reserve(user, book, active, overdue, pickupDay, today, policy) {
  ensure(user.role === 'reader', 'Chỉ độc giả được đặt sách.');
  integer(pickupDay, today, today + 7, 'Ngày nhận');
  ensure(!book.archived && book.available > 0, 'Sách chưa có bản sẵn sàng.');
  ensure(active < policy.maxLoans, 'Đã đạt giới hạn phiếu đang xử lý.');
  ensure(!overdue, 'Vui lòng trả sách quá hạn trước khi đặt thêm.');
}
function transition(command, user, loan, today, policy) {
  const next = {...loan};
  if (command === 'cancel') {
    ensure(user.uid === loan.readerId && user.role === 'reader', 'Không được hủy phiếu này.');
    ensure(loan.status === 'RESERVED', 'Chỉ được hủy phiếu chưa nhận sách.');
    next.status = 'CANCELLED';
  } else if (command === 'checkout') {
    staff(user); ensure(loan.status === 'RESERVED' && loan.pickupDay === today, 'Phiếu phải sẵn sàng và đúng ngày hẹn.');
    next.status = 'BORROWED'; next.dueDay = today + policy.loanDays; next.finePerDay = policy.dailyFine;
  } else if (command === 'renew') {
    ensure(user.role === 'reader' && loan.readerId === user.uid, 'Không được gia hạn phiếu này.');
    ensure(loan.status === 'BORROWED' && loan.dueDay >= today && loan.renewals < policy.maxRenewals, 'Phiếu không đủ điều kiện gia hạn.');
    next.dueDay += policy.renewalDays; next.renewals++;
  } else if (command === 'return') {
    staff(user); ensure(loan.status === 'BORROWED', 'Phiếu không ở trạng thái đang mượn.');
    next.status = 'RETURNED'; next.returnDay = today; next.fine = Math.max(0, today - loan.dueDay) * loan.finePerDay;
  } else if (command === 'expire') {
    ensure(user.role === 'system', 'Chỉ tác vụ máy chủ được xử lý hết hạn.');
    ensure(loan.status === 'RESERVED' && loan.pickupDay < today, 'Phiếu chưa hết hạn.'); next.status = 'EXPIRED';
  } else throw new PolicyError('Thao tác không được hỗ trợ.');
  return next;
}
module.exports = {PolicyError, ensure, integer, day, actor, staff, rules, reserve, transition};

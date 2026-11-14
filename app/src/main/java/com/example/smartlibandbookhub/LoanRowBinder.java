package com.example.smartlibandbookhub;

import android.view.View;
import android.widget.TextView;
import com.example.smartlibandbookhub.domain.Library;
import com.example.smartlibandbookhub.domain.Library.Book;
import com.example.smartlibandbookhub.domain.Library.Loan;
import com.example.smartlibandbookhub.domain.Library.Role;
import com.example.smartlibandbookhub.domain.Library.Status;
import com.google.android.material.button.MaterialButton;

final class LoanRowBinder {
  interface Confirmation {
    void confirm(String message, Runnable action);
  }

  private final Library library;
  private final Role role;
  private final LibraryViewModel viewModel;
  private final Confirmation confirmation;

  LoanRowBinder(Library library, Role role, LibraryViewModel viewModel, Confirmation confirmation) {
    this.library = library;
    this.role = role;
    this.viewModel = viewModel;
    this.confirmation = confirmation;
  }

  void bindLoan(View row, Loan loan, boolean desk) {
    Book book = library.book(loan.bookId);
    ((TextView) row.findViewById(R.id.loan_status)).setText(status(loan));
    ((TextView) row.findViewById(R.id.loan_title)).setText(book.title);
    ((TextView) row.findViewById(R.id.loan_reader))
        .setText("Phiếu #" + loan.id.substring(0, 8) + " · " + loan.readerId);
    ((TextView) row.findViewById(R.id.loan_date))
        .setText(loan.status == Status.BORROWED || loan.status == Status.RETURNED
            ? "Hạn trả: " + LibraryFormat.date(loan.dueDay) + " · Gia hạn: " + loan.renewals + " lần"
            : "Hẹn nhận: " + LibraryFormat.date(loan.pickupDay) + " · " + book.shelf);
    TextView returned = row.findViewById(R.id.loan_return);
    returned.setVisibility(loan.status == Status.RETURNED ? View.VISIBLE : View.GONE);
    returned.setText(loan.status == Status.RETURNED ? "Đã trả: " + LibraryFormat.date(loan.returnDay) : "");
    long fine = library.fine(loan, LibraryFormat.today());
    TextView fineView = row.findViewById(R.id.loan_fine);
    fineView.setVisibility(fine > 0 ? View.VISIBLE : View.GONE);
    fineView.setText(fine > 0 ? "Phí quá hạn "
        + (loan.status == Status.RETURNED ? "ghi nhận: " : "tạm tính: ") + LibraryFormat.money(fine) : "");
    MaterialButton action = row.findViewById(R.id.loan_action);
    action.setVisibility(View.VISIBLE);
    if (desk && loan.status == Status.RESERVED) {
      action.setText("Xác nhận bàn giao");
      action.setOnClickListener(v -> confirmation.confirm("Bàn giao “" + book.title + "” cho độc giả?",
          () -> viewModel.checkout(role, loan.id, LibraryFormat.today())));
    } else if (desk && loan.status == Status.BORROWED) {
      action.setText("Xác nhận nhận trả");
      action.setOnClickListener(v -> confirmation.confirm("Đã nhận lại “" + book.title
          + "”? Phí quá hạn được ghi nhận, chưa phải thanh toán.",
          () -> viewModel.returnBook(role, loan.id, LibraryFormat.today())));
    } else if (!desk && role == Role.READER && loan.status == Status.BORROWED) {
      action.setText("Gia hạn thêm " + library.renewalDays + " ngày");
      action.setOnClickListener(v -> confirmation.confirm("Gia hạn “" + book.title + "”?",
          () -> viewModel.renew(role, loan.id, LibraryFormat.today())));
    } else if (!desk && role == Role.READER
        && (loan.status == Status.RESERVED || loan.status == Status.WAITING)) {
      action.setText("Hủy yêu cầu");
      action.setOnClickListener(v -> confirmation.confirm("Hủy yêu cầu “" + book.title + "”?",
          () -> viewModel.cancel(role, loan.id, LibraryFormat.today())));
    } else {
      action.setVisibility(View.GONE);
      action.setOnClickListener(null);
    }
  }


  private String status(Loan l) {
    switch (l.status) {
      case RESERVED:
        return "SẴN SÀNG NHẬN";
      case WAITING:
        return "ĐANG CHỜ CÓ SÁCH";
      case BORROWED:
        return l.dueDay < LibraryFormat.today() ? "QUÁ HẠN · " + (LibraryFormat.today() - l.dueDay) + " NGÀY" : "ĐANG MƯỢN";
      case RETURNED:
        return "ĐÃ TRẢ";
      case CANCELLED:
        return "ĐÃ HỦY";
      default:
        return "HẾT HẠN NHẬN";
    }
  }
}

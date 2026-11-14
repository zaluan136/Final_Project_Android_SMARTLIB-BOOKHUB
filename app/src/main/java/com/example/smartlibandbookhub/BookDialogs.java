package com.example.smartlibandbookhub;

import android.app.DatePickerDialog;
import android.content.Context;
import com.example.smartlibandbookhub.domain.Library;
import com.example.smartlibandbookhub.domain.Library.Book;
import com.example.smartlibandbookhub.domain.Library.Role;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Calendar;
import java.util.function.Consumer;

final class BookDialogs {
  private final Context context;
  private final Library library;
  private final Role role;
  private final LibraryViewModel viewModel;
  private final Consumer<Book> editBook;
  private final LoanRowBinder.Confirmation confirmation;

  BookDialogs(Context context, Library library, Role role, LibraryViewModel viewModel,
      Consumer<Book> editBook, LoanRowBinder.Confirmation confirmation) {
    this.context = context;
    this.library = library;
    this.role = role;
    this.viewModel = viewModel;
    this.editBook = editBook;
    this.confirmation = confirmation;
  }

  void showDetails(Book b) {
    String message =
        b.author
            + "\n\n"
            + b.description
            + "\n\nThể loại: "
            + b.category
            + "\nVị trí: "
            + b.shelf
            + "\nISBN: "
            + b.isbn
            + "\nSẵn sàng: "
            + library.available(b.id)
            + " / "
            + b.copies
            + " bản\n\nMượn "
            + library.loanDays
            + " ngày · Phí quá hạn "
            + LibraryFormat.money(library.dailyFine)
            + "/ngày.\nBìa và danh mục dùng cho bản demo.";
    MaterialAlertDialogBuilder dialog =
        new MaterialAlertDialogBuilder(context)
            .setTitle(b.title)
            .setMessage(message)
            .setNegativeButton("Đóng", null);
    if (role == Role.READER)
      dialog.setPositiveButton(
          library.available(b.id) > 0 ? "Đặt lịch nhận" : "Đăng ký chờ", (d, w) -> pickup(b));
    else
      dialog
          .setPositiveButton("Sửa thông tin", (d, w) -> editBook.accept(b))
          .setNeutralButton(
              "Ẩn sách",
              (d, w) ->
                  confirmation.confirm(
                      "Ẩn “" + b.title + "” khỏi danh mục?",
                      () -> viewModel.archive(role, b.id, LibraryFormat.today())));
    dialog.show();
  }

  private void pickup(Book b) {
    Calendar now = Calendar.getInstance();
    DatePickerDialog picker =
        new DatePickerDialog(
            context,
            (view, y, m, d) -> {
              long day = LibraryFormat.epochDay(y, m, d);
              confirmation.confirm(
                  "Đặt “"
                      + b.title
                      + "” vào "
                      + LibraryFormat.date(day)
                      + "?\n\n"
                      + "Nhận trong ngày hẹn. Nếu hết bản, yêu cầu vào hàng chờ theo thứ tự. Phiếu"
                      + " hết hạn khi qua ngày hẹn.",
                  () ->
                      viewModel.reserve(role, b.id, day, LibraryFormat.today()));
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH));
    Calendar min = (Calendar) now.clone();
    min.set(Calendar.HOUR_OF_DAY, 0);
    min.set(Calendar.MINUTE, 0);
    min.set(Calendar.SECOND, 0);
    min.set(Calendar.MILLISECOND, 0);
    picker.getDatePicker().setMinDate(min.getTimeInMillis());
    min.add(Calendar.DAY_OF_MONTH, 7);
    picker.getDatePicker().setMaxDate(min.getTimeInMillis());
    picker.show();
  }

}

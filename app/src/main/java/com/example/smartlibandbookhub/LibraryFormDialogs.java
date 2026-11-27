package com.example.smartlibandbookhub;

import static com.example.smartlibandbookhub.LibraryFormat.today;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.example.smartlibandbookhub.domain.Library;
import com.example.smartlibandbookhub.domain.Library.Book;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.UUID;

final class LibraryFormDialogs {
  private final Context context;
  private final LibraryViewModel viewModel;
  private AlertDialog activeForm;
  private EditText[] formFields;
  private String formType;
  private String formBookId;
  private Bundle pendingForm;

  LibraryFormDialogs(Context context, LibraryViewModel viewModel, Bundle saved) {
    this.context = context;
    this.viewModel = viewModel;
    pendingForm = saved == null ? null : saved.getBundle("active_form");
  }

  void setBusy(boolean busy) {
    if (activeForm != null && activeForm.isShowing()) activeForm.getButton(-1).setEnabled(!busy);
  }

  void dismiss() {
    if (activeForm != null) activeForm.dismiss();
  }

  void saveState(Bundle out) {
    if (activeForm == null || !activeForm.isShowing() || formFields == null) return;
    Bundle state = new Bundle();
    state.putString("type", formType);
    state.putString("book_id", formBookId);
    String[] values = new String[formFields.length];
    for (int i = 0; i < formFields.length; i++) values[i] = formFields[i].getText().toString();
    state.putStringArray("values", values);
    out.putBundle("active_form", state);
  }

  void editBook(Book existing) {
    LinearLayout form = form();
    EditText title = field(form, "Tên sách", existing == null ? "" : existing.title, false),
        author = field(form, "Tác giả", existing == null ? "" : existing.author, false),
        categoryInput =
            field(form, "Thể loại", existing == null ? "Văn học" : existing.category, false),
        isbn = field(form, "ISBN-13", existing == null ? "" : existing.isbn, true),
        shelf = field(form, "Vị trí kệ", existing == null ? "" : existing.shelf, false),
        copies =
            field(
                form,
                "Số bản (1–999)",
                existing == null ? "1" : String.valueOf(existing.copies),
                true),
        description = field(form, "Mô tả", existing == null ? "" : existing.description, false);
    if (existing == null) {
      com.google.android.material.button.MaterialButton choose =
          new com.google.android.material.button.MaterialButton(context, null,
              com.google.android.material.R.attr.materialButtonTonalStyle);
      choose.setText("Chọn sách từ danh mục mẫu");
      choose.setOnClickListener(v -> BookPicker.show(context, BookPicker.demoSource(), book -> {
        title.setText(book.title);
        author.setText(book.author);
        categoryInput.setText(book.category);
        isbn.setText(book.isbn);
        description.setText(book.description);
      }));
      form.addView(choose, 0, new LinearLayout.LayoutParams(-1, -2));
    }
    androidx.appcompat.app.AlertDialog d =
        new MaterialAlertDialogBuilder(context)
            .setTitle(existing == null ? "Thêm đầu sách" : "Sửa đầu sách")
            .setView(scrollForm(form))
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      try {
                        Book b =
                            new Book(
                                existing == null ? UUID.randomUUID().toString() : existing.id,
                                value(title),
                                value(author),
                                value(categoryInput),
                                value(isbn),
                                value(shelf),
                                value(description),
                                Integer.parseInt(value(copies)),
                                existing == null ? 0xFF20695C : existing.color);
                        // Validate using a fresh isolated state inside the transaction; errors keep
                        // the form open.
                        activeForm = d;
                        viewModel.saveBook(viewModel.getState().getValue().role, b, today());
                      } catch (NumberFormatException e) {
                        copies.setError("Nhập số nguyên từ 1 đến 999.");
                      }
                    }));
    d.show();
    trackForm(d, "book", existing == null ? null : existing.id,
        new EditText[] {title, author, categoryInput, isbn, shelf, copies, description});
  }

  void editRules() {
    Library library = viewModel.getState().getValue().library;
    if (library == null) return;
    LinearLayout form = form();
    EditText max = field(form, "Số phiếu tối đa (1–20)", "" + library.maxLoans, true),
        days = field(form, "Thời hạn mượn (1–90 ngày)", "" + library.loanDays, true),
        renewal = field(form, "Số ngày gia hạn (1–30)", "" + library.renewalDays, true),
        count = field(form, "Số lần gia hạn (0–5)", "" + library.maxRenewals, true),
        fine = field(form, "Phí mỗi ngày (0–100000 đồng)", "" + library.dailyFine, true);
    form.addView(
        text(
            "Thời hạn và đơn giá đã chốt của phiếu đang mượn được giữ nguyên. Giới hạn gia hạn mới"
                + " áp dụng cho yêu cầu tiếp theo.",
            12,
            false));
    androidx.appcompat.app.AlertDialog d =
        new MaterialAlertDialogBuilder(context)
            .setTitle("Quy định thư viện")
            .setView(scrollForm(form))
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      try {
                        int a = Integer.parseInt(value(max)),
                            b = Integer.parseInt(value(days)),
                            c = Integer.parseInt(value(renewal)),
                            e = Integer.parseInt(value(count));
                        long f = Long.parseLong(value(fine));
                        activeForm = d;
                        viewModel.saveRules(viewModel.getState().getValue().role, a, b, c, e, f, today());
                      } catch (NumberFormatException e) {
                        fine.setError("Các trường cần là số nguyên trong giới hạn ghi ở nhãn.");
                      }
                    }));
    d.show();
    trackForm(d, "rules", null, new EditText[] {max, days, renewal, count, fine});
  }

  private void trackForm(androidx.appcompat.app.AlertDialog dialog, String type,
      String bookId, EditText[] fields) {
    activeForm = dialog;
    formType = type;
    formBookId = bookId;
    formFields = fields;
    dialog.getButton(-1).setEnabled(!viewModel.getState().getValue().loading);
    dialog.setOnDismissListener(d -> {
      if (activeForm == dialog) {
        activeForm = null;
        formFields = null;
      }
    });
  }

  void restoreForm() {
    Library library = viewModel.getState().getValue().library;
    if (pendingForm == null || library == null) return;
    Bundle form = pendingForm;
    pendingForm = null;
    if ("rules".equals(form.getString("type"))) {
      editRules();
    } else if ("book".equals(form.getString("type"))) {
      String id = form.getString("book_id");
      Book book = null;
      if (id != null) {
        for (Book candidate : library.books) {
          if (id.equals(candidate.id)) {
            book = candidate;
            break;
          }
        }
        if (book == null) return;
      }
      editBook(book);
    }
    String[] values = form.getStringArray("values");
    if (values != null && formFields != null && values.length == formFields.length) {
      for (int i = 0; i < values.length; i++) formFields[i].setText(values[i]);
    }
  }


  private LinearLayout column() {
    LinearLayout v = new LinearLayout(context);
    v.setOrientation(LinearLayout.VERTICAL);
    v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
    return v;
  }


  private TextView text(String value, int size, boolean bold) {
    TextView t = new TextView(context);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(context.getColor(bold ? R.color.ink : R.color.muted));
    t.setPadding(0, dp(6), 0, dp(8));
    t.setLineSpacing(dp(3), 1);
    if (bold) t.setTypeface(null, Typeface.BOLD);
    return t;
  }


  private LinearLayout form() {
    LinearLayout f = column();
    f.setPadding(dp(24), dp(8), dp(24), dp(8));
    return f;
  }

  private View scrollForm(LinearLayout form) {
    android.widget.ScrollView s = new android.widget.ScrollView(context);
    s.addView(form);
    return s;
  }

  private EditText field(LinearLayout parent, String label, String initial, boolean numeric) {
    TextInputLayout wrap = new TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
    wrap.setHint(label);
    wrap.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
    int r = dp(16);
    wrap.setBoxCornerRadii(r, r, r, r);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(6);
    lp.bottomMargin = dp(6);
    wrap.setLayoutParams(lp);
    TextInputEditText input = new TextInputEditText(context);
    input.setText(initial);
    input.setInputType(
        numeric
            ? InputType.TYPE_CLASS_NUMBER
            : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    input.setMaxLines(4);
    wrap.addView(input);
    parent.addView(wrap);
    return input;
  }

  private String value(EditText e) {
    return e.getText().toString().trim();
  }

  private int dp(int n) {
    return Math.round(n * context.getResources().getDisplayMetrics().density);
  }
}

package com.example.smartlibandbookhub;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.smartlibandbookhub.domain.Library;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.List;
import java.util.function.Consumer;

final class BookPicker {
  interface Source {
    void search(String query, Consumer<List<Library.Book>> callback);
  }

  static Source demoSource() {
    return (query, callback) -> callback.accept(Library.demo().search(query, "Tất cả"));
  }

  static void show(Context context, Source source, Consumer<Library.Book> selected) {
    int pad = Math.round(20 * context.getResources().getDisplayMetrics().density);
    LinearLayout content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(pad, 0, pad, 0);
    TextInputLayout input = new TextInputLayout(context, null,
        com.google.android.material.R.attr.textInputOutlinedStyle);
    input.setHint("Tên sách, tác giả hoặc ISBN");
    TextInputEditText query = new TextInputEditText(context);
    query.setSingleLine(true);
    input.addView(query);
    content.addView(input);
    TextView hint = new TextView(context);
    hint.setText("Dữ liệu mẫu · Chọn sách để điền thông tin");
    hint.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    hint.setPadding(0, pad / 2, 0, pad / 2);
    content.addView(hint);
    android.widget.ScrollView scroll = new android.widget.ScrollView(context);
    LinearLayout results = new LinearLayout(context);
    results.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(results);
    content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
        .setTitle("Chọn sách")
        .setView(content)
        .setNegativeButton("Đóng", null)
        .create();
    final int[] generation = {0};
    Runnable search = () -> {
      int request = ++generation[0];
      source.search(query.getText().toString(), books -> {
        if (request != generation[0] || !dialog.isShowing()) return;
        results.removeAllViews();
        if (books.isEmpty()) {
          TextView empty = new TextView(context);
          empty.setText("Không tìm thấy sách. Hãy thử từ khóa khác.");
          empty.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
          empty.setPadding(0, pad, 0, pad);
          results.addView(empty);
        }
        for (Library.Book book : books) {
          MaterialButton item = new MaterialButton(context, null,
              com.google.android.material.R.attr.materialButtonTonalStyle);
          item.setText(book.title + "\n" + book.author + " · " + book.isbn);
          item.setAllCaps(false);
          item.setMinHeight(Math.round(64 * context.getResources().getDisplayMetrics().density));
          item.setOnClickListener(v -> {
            selected.accept(book);
            dialog.dismiss();
          });
          results.addView(item, new LinearLayout.LayoutParams(-1, -2));
        }
      });
    };
    query.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) { search.run(); }
      public void afterTextChanged(Editable s) {}
    });
    dialog.setOnShowListener(v -> {
      dialog.getWindow().setLayout(-1, Math.round(560 * context.getResources().getDisplayMetrics().density));
      search.run();
    });
    dialog.setOnDismissListener(v -> generation[0]++);
    dialog.show();
  }
}

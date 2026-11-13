package com.example.smartlibandbookhub;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseApp;
import com.example.smartlibandbookhub.data.FirebaseRepository;
import com.example.smartlibandbookhub.data.OnlineBook;
import com.example.smartlibandbookhub.data.OnlineLoan;
import java.util.HashMap;
import java.util.Map;

public final class FirebaseActivity extends AppCompatActivity {
  private LinearLayout content;
  private FirebaseViewModel viewModel;
  private boolean busy;
  private String renderedScreen;
  private EditText emailField;
  private String restoredEmail;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_firebase);
    restoredEmail = state == null ? "" : state.getString("email", "");
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.firebase_root),
        (v, i) -> {
          Insets b =
              i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          return i;
        });
    content = findViewById(R.id.firebase_content);
    findViewById(R.id.firebase_back).setOnClickListener(v -> finish());
    if (FirebaseApp.getApps(this).isEmpty()) {
      label("Thư viện trực tuyến", 28);
      label("Chưa kết nối thư viện", 20);
      label("Thư viện trực tuyến chưa sẵn sàng. Vui lòng liên hệ quản trị viên.", 16);
      return;
    }
    viewModel = new ViewModelProvider(this,
        new FirebaseViewModelFactory(new FirebaseRepository())).get(FirebaseViewModel.class);
    viewModel.getState().observe(this, this::render);
    viewModel.getEvent().observe(this, event -> {
      if (event == null) return;
      if (event.retry == null) {
        viewModel.consumeEvent();
        message(event.text);
      } else {
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle("Chưa hoàn tất").setMessage(event.text)
            .setNegativeButton("Đóng", (d, w) -> viewModel.consumeEvent())
            .setPositiveButton("Thử lại", (d, w) -> {
              viewModel.consumeEvent();
              viewModel.send(event.retry);
            }).create();
        dialog.setOnCancelListener(d -> viewModel.consumeEvent());
        dialog.show();
      }
    });
  }

  private void render(FirebaseViewModel.State state) {
    busy = state.busy;
    findViewById(R.id.firebase_loading).setVisibility(busy ? View.VISIBLE : View.GONE);
    if ("login".equals(state.screen) && "login".equals(renderedScreen)) return;
    renderedScreen = state.screen;
    content.removeAllViews();
    label("BookHub · Trực tuyến", 27);
    if ("login".equals(state.screen)) {
      login();
      return;
    }
    button("Đăng xuất", viewModel::signOut);
    if ("verify".equals(state.screen)) {
      label("Xác minh địa chỉ email để tiếp tục. Kiểm tra hộp thư và cả mục thư rác.", 16);
      button("Gửi email xác minh", viewModel::sendVerification);
      button("Tôi đã xác minh · Kiểm tra lại", viewModel::checkVerification);
      return;
    }
    if (state.busy) {
      label("Đang tải dữ liệu từ máy chủ…", 14);
      return;
    }
    if (state.error != null) {
      label(state.error, 15);
      button("Thử lại", viewModel::refresh);
      return;
    }
    label("Vai trò được cấp: " + state.role, 14);
    button("Làm mới dữ liệu", viewModel::refresh);
    label("Danh mục sách", 22);
    if (state.books.isEmpty()) label("Thư viện chưa có sách. Quản trị cần nhập danh mục ban đầu.", 15);
    for (OnlineBook book : state.books) {
      label(book.title + "\n" + book.author + " · Kệ "
          + book.shelf + "\nSẵn sàng: " + book.available, 16);
      if ("reader".equals(state.role)) {
        button("Đặt nhận hôm nay · " + book.title, () -> {
          Map<String, Object> data = new HashMap<>();
          data.put("bookId", book.id);
          data.put("pickupDay", (System.currentTimeMillis() + 7 * 3600000L) / 86400000L);
          confirmCommand("reserve", data);
        });
      }
    }
    boolean staff = state.role.equals("admin") || state.role.equals("librarian");
    label(staff ? "Quầy mượn–trả (tối đa 100 phiếu)" : "Phiếu của bạn (tối đa 100 phiếu)", 22);
    if (state.loans.isEmpty()) label("Chưa có phiếu mượn nào.", 15);
    for (OnlineLoan loan : state.loans) {
      String status = loan.status;
      label(loan.title + " · " + status, 16);
      Long due = loan.dueDay;
      if (due != null && due > 0) {
        label("Hạn trả: " + new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ROOT)
            .format(new java.util.Date(due * 86400000L)), 14);
      }
      if ("BORROWED".equals(status)) {
        button(staff ? "Xác nhận trả sách" : "Gia hạn",
            () -> loanCommand(staff ? "return" : "renew", loan.id));
      }
      if ("RESERVED".equals(status)) {
        button(staff ? "Xác nhận bàn giao" : "Hủy đặt sách",
            () -> loanCommand(staff ? "checkout" : "cancel", loan.id));
      }
    }
  }

  private void login() {
    label("Tài khoản dùng chung với thư viện. Quyền thủ thư được cấp từ máy chủ.", 15);
    EditText email = input("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    emailField = email;
    email.setText(restoredEmail);
    EditText password = input("Mật khẩu", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    button("Đăng nhập", () -> {
      String e = email.getText().toString().trim();
      String p = password.getText().toString();
      if (e.isEmpty() || p.isEmpty()) message("Nhập email và mật khẩu.");
      else viewModel.signIn(e, p);
    });
    button("Tạo tài khoản độc giả", () -> {
      String e = email.getText().toString().trim();
      String p = password.getText().toString();
      if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() || p.length() < 12)
        message("Nhập email hợp lệ và mật khẩu ít nhất 12 ký tự.");
      else viewModel.register(e, p);
    });
    button("Quên mật khẩu", () -> {
      String e = email.getText().toString().trim();
      if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches())
        message("Nhập email hợp lệ trước.");
      else viewModel.resetPassword(e);
    });
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    if (emailField != null) out.putString("email", emailField.getText().toString());
    super.onSaveInstanceState(out);
  }

  private void loanCommand(String command, String loanId) {
    Map<String, Object> data = new HashMap<>();
    data.put("loanId", loanId);
    confirmCommand(command, data);
  }

  private void confirmCommand(String command, Map<String, Object> data) {
    new MaterialAlertDialogBuilder(this)
        .setTitle("Xác nhận thao tác trực tuyến")
        .setMessage("Thao tác sẽ cập nhật dữ liệu chung của thư viện.")
        .setNegativeButton("Hủy", null)
        .setPositiveButton("Xác nhận", (d, w) -> viewModel.command(command, data))
        .show();
  }

  private void message(String text) {
    if (!isDestroyed())
      new MaterialAlertDialogBuilder(this).setMessage(text).setPositiveButton("Đóng", null).show();
  }

  private TextView label(String text, int size) {
    TextView v = new TextView(this);
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(getColor(R.color.ink));
    int spacing = Math.round(8 * getResources().getDisplayMetrics().density);
    v.setPadding(0, spacing, 0, spacing);
    if (size >= 20) v.setTypeface(null, android.graphics.Typeface.BOLD);
    content.addView(v);
    return v;
  }

  private void button(String text, Runnable action) {
    MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonTonalStyle);
    b.setText(text);
    b.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
    b.setCornerRadius(Math.round(20 * getResources().getDisplayMetrics().density));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    int margin = Math.round(6 * getResources().getDisplayMetrics().density);
    lp.topMargin = margin;
    lp.bottomMargin = margin;
    b.setLayoutParams(lp);
    b.setOnClickListener(
        v -> {
          if (!busy) action.run();
        });
    content.addView(b);
  }

  private EditText input(String hint, int type) {
    TextInputLayout layout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
    layout.setHint(hint);
    layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
    int r = Math.round(16 * getResources().getDisplayMetrics().density);
    layout.setBoxCornerRadii(r, r, r, r);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    int margin = Math.round(6 * getResources().getDisplayMetrics().density);
    lp.topMargin = margin;
    lp.bottomMargin = margin;
    layout.setLayoutParams(lp);
    TextInputEditText e = new TextInputEditText(this);
    e.setInputType(type);
    e.setSingleLine();
    layout.addView(e);
    content.addView(layout);
    return e;
  }
}

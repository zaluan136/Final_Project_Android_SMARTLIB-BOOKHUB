package com.example.smartlibandbookhub;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Online mode has no role picker. Authorization is enforced again by every server endpoint. */
public final class FirebaseActivity extends AppCompatActivity {
  private LinearLayout content;
  private FirebaseAuth auth;
  private boolean busy;
  private String role = "reader";
  private int generation;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_firebase);
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
      label("Chưa kết nối Firebase", 20);
      label(
          "Bản này chưa có cấu hình project. Tạo Firebase project, đăng ký ứng dụng"
              + " com.example.smartlibandbookhub và thêm google-services.json vào thư mục app/, sau"
              + " đó build lại.\n\n"
              + "Cần bật Email/Password, triển khai Firestore Rules và Cloud Functions, cấu hình"
              + " App Check. Hướng dẫn chi tiết nằm trong docs/FIREBASE.md.",
          16);
      return;
    }
    auth = FirebaseAuth.getInstance();
    screen();
  }

  private void screen() {
    if (isDestroyed()) return;
    int current = ++generation;
    content.removeAllViews();
    label("BookHub · Trực tuyến", 27);
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) {
      login();
      return;
    }
    button(
        "Đăng xuất",
        () -> {
          auth.signOut();
          screen();
        });
    if (!user.isEmailVerified()) {
      label("Xác minh địa chỉ email để tiếp tục. Kiểm tra hộp thư và cả mục thư rác.", 16);
      button(
          "Gửi email xác minh",
          () -> {
            busy = true;
            user.sendEmailVerification()
                .addOnCompleteListener(
                    this,
                    t -> {
                      busy = false;
                      message(
                          t.isSuccessful()
                              ? "Đã gửi email xác minh."
                              : "Chưa gửi được email. Vui lòng thử lại sau.");
                    });
          });
      button(
          "Tôi đã xác minh · Kiểm tra lại",
          () -> {
            busy = true;
            user.reload()
                .continueWithTask(
                    t -> {
                      if (!t.isSuccessful()) throw t.getException();
                      return user.getIdToken(true);
                    })
                .addOnCompleteListener(
                    this,
                    t -> {
                      busy = false;
                      if (t.isSuccessful()) screen();
                      else message("Không thể cập nhật phiên đăng nhập.");
                    });
          });
      return;
    }
    TextView loading = label("Đang tải dữ liệu từ máy chủ…", 14);
    user.getIdToken(true)
        .addOnSuccessListener(
            this,
            token -> {
              if (current != generation) return;
              content.removeView(loading);
              Object claim = token.getClaims().get("role");
              role = claim instanceof String ? (String) claim : "reader";
              label("Vai trò được cấp: " + role, 14);
              catalogue(current);
            })
        .addOnFailureListener(this, e -> {
          if (current != generation) return;
          loading.setText("Không thể kết nối hoặc xác thực phiên. Kiểm tra mạng và thử lại.");
          button("Thử lại", this::screen);
        });
  }

  private void login() {
    label("Tài khoản dùng chung với thư viện. Quyền thủ thư được cấp từ máy chủ.", 15);
    EditText
        email =
            input("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
        password =
            input("Mật khẩu", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    button(
        "Đăng nhập",
        () -> {
          String e = email.getText().toString().trim(), p = password.getText().toString();
          if (e.isEmpty() || p.isEmpty()) {
            message("Nhập email và mật khẩu.");
            return;
          }
          busy = true;
          auth.signInWithEmailAndPassword(e, p)
              .addOnCompleteListener(
                  this,
                  t -> {
                    busy = false;
                    if (t.isSuccessful()) screen();
                    else message("Không thể đăng nhập. Kiểm tra thông tin hoặc thử lại sau.");
                  });
        });
    button(
        "Tạo tài khoản độc giả",
        () -> {
          String e = email.getText().toString().trim(), p = password.getText().toString();
          if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() || p.length() < 12) {
            message("Nhập email hợp lệ và mật khẩu ít nhất 12 ký tự.");
            return;
          }
          busy = true;
          auth.createUserWithEmailAndPassword(e, p)
              .addOnCompleteListener(
                  this,
                  t -> {
                    busy = false;
                    if (t.isSuccessful()) screen();
                    else
                      message(
                          "Không thể tạo tài khoản. Kiểm tra email, chính sách mật khẩu hoặc thử"
                              + " lại sau.");
                  });
        });
    button(
        "Quên mật khẩu",
        () -> {
          String e = email.getText().toString().trim();
          if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
            message("Nhập email hợp lệ trước.");
            return;
          }
          busy = true;
          auth.sendPasswordResetEmail(e)
              .addOnCompleteListener(
                  this,
                  t -> {
                    busy = false;
                    message(
                        "Nếu email đủ điều kiện, hướng dẫn khôi phục sẽ được gửi. Hãy kiểm tra hộp"
                            + " thư.");
                  });
        });
  }

  private void catalogue(int current) {
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    button("Làm mới dữ liệu", this::screen);
    label("Danh mục sách", 22);
    db.collection("books")
        .whereEqualTo("archived", false)
        .limit(100)
        .get(Source.SERVER)
        .addOnSuccessListener(
            this,
            snapshot -> {
              if (current != generation) return;
              if (snapshot.isEmpty())
                label("Thư viện chưa có sách. Quản trị cần nhập danh mục ban đầu.", 15);
              for (DocumentSnapshot book : snapshot.getDocuments()) {
                label(
                    book.getString("title")
                        + "\n"
                        + book.getString("author")
                        + " · Kệ "
                        + book.getString("shelf")
                        + "\nSẵn sàng: "
                        + book.getLong("available"),
                    16);
                if (role.equals("reader"))
                  button(
                      "Đặt nhận hôm nay · " + book.getString("title"),
                      () -> {
                        Map<String, Object> data = new HashMap<>();
                        data.put("bookId", book.getId());
                        data.put(
                            "pickupDay", (System.currentTimeMillis() + 7 * 3600000L) / 86400000L);
                        command("reserve", data);
                      });
              }
              loadLoans(db, current);
            })
        .addOnFailureListener(
            this,
            e ->
                message(
                    "Không đọc được danh mục. Kiểm tra mạng, email xác minh, App Check và Firestore"
                        + " Rules."));
  }

  private void loadLoans(FirebaseFirestore db, int current) {
    boolean staff = role.equals("admin") || role.equals("librarian");
    Query query =
        staff
            ? db.collection("loans")
            : db.collection("loans").whereEqualTo("readerId", auth.getCurrentUser().getUid());
    query
        .limit(100)
        .get(Source.SERVER)
        .addOnSuccessListener(
            this,
            snapshot -> {
              if (current != generation) return;
              label(
                  staff ? "Quầy mượn–trả (tối đa 100 phiếu)" : "Phiếu của bạn (tối đa 100 phiếu)",
                  22);
              if (snapshot.isEmpty()) label("Chưa có phiếu mượn nào.", 15);
              for (DocumentSnapshot loan : snapshot.getDocuments()) {
                String status = loan.getString("status");
                label(loan.getString("title") + " · " + status, 16);
                Long due = loan.getLong("dueDay");
                if (due != null && due > 0)
                  label(
                      "Hạn trả: "
                          + new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ROOT)
                              .format(new java.util.Date(due * 86400000L)),
                      14);
                if ("BORROWED".equals(status)) {
                  button(
                      staff ? "Xác nhận trả sách" : "Gia hạn",
                      () -> loanCommand(staff ? "return" : "renew", loan.getId()));
                }
                if ("RESERVED".equals(status)) {
                  button(
                      staff ? "Xác nhận bàn giao" : "Hủy đặt sách",
                      () -> loanCommand(staff ? "checkout" : "cancel", loan.getId()));
                }
              }
            })
        .addOnFailureListener(
            this, e -> message("Không đọc được phiếu mượn. Kiểm tra quyền hoặc kết nối."));
  }

  private void loanCommand(String command, String loanId) {
    Map<String, Object> data = new HashMap<>();
    data.put("loanId", loanId);
    command(command, data);
  }

  private void command(String command, Map<String, Object> data) {
    data.put("command", command);
    data.put("requestId", UUID.randomUUID().toString());
    new MaterialAlertDialogBuilder(this)
        .setTitle("Xác nhận thao tác trực tuyến")
        .setMessage("Thao tác sẽ cập nhật dữ liệu chung của thư viện.")
        .setNegativeButton("Hủy", null)
        .setPositiveButton("Xác nhận", (d, w) -> send(data))
        .show();
  }

  private void send(Map<String, Object> data) {
    if (busy) return;
    busy = true;
    FirebaseFunctions.getInstance("asia-southeast1")
        .getHttpsCallable("bookhub")
        .call(data)
        .addOnCompleteListener(
            this,
            t -> {
              busy = false;
              if (t.isSuccessful()) {
                screen();
                message("Máy chủ đã xác nhận thao tác.");
                return;
              }
              Exception e = t.getException();
              String detail =
                  e instanceof FirebaseFunctionsException
                          && ((FirebaseFunctionsException) e).getCode()
                              == FirebaseFunctionsException.Code.FAILED_PRECONDITION
                      ? e.getMessage()
                      : "Chưa xác định được kết quả. Có thể thử lại an toàn bằng cùng mã yêu cầu.";
              new MaterialAlertDialogBuilder(this)
                  .setTitle("Chưa hoàn tất")
                  .setMessage(detail)
                  .setNegativeButton("Đóng", null)
                  .setPositiveButton("Thử lại", (d, w) -> send(data))
                  .show();
            });
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
    MaterialButton b = new MaterialButton(this);
    b.setText(text);
    b.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
    b.setCornerRadius(Math.round(8 * getResources().getDisplayMetrics().density));
    b.setOnClickListener(
        v -> {
          if (!busy) action.run();
        });
    content.addView(b);
  }

  private EditText input(String hint, int type) {
    TextInputLayout layout = new TextInputLayout(this);
    layout.setHint(hint);
    TextInputEditText e = new TextInputEditText(this);
    e.setInputType(type);
    e.setSingleLine();
    layout.addView(e);
    content.addView(layout);
    return e;
  }
}

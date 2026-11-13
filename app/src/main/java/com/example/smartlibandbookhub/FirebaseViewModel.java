package com.example.smartlibandbookhub;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.smartlibandbookhub.data.FirebaseRepository;
import com.example.smartlibandbookhub.data.FirebaseDataSource;
import com.example.smartlibandbookhub.data.OnlineBook;
import com.example.smartlibandbookhub.data.OnlineLoan;
import com.google.android.gms.tasks.Task;
import com.example.smartlibandbookhub.data.OnlineSession;
import com.example.smartlibandbookhub.data.OnlineCommandException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FirebaseViewModel extends ViewModel {
  public static final class State {
    public final String screen;
    public final String role;
    public final boolean busy;
    public final List<OnlineBook> books;
    public final List<OnlineLoan> loans;
    public final String error;

    State(String screen, String role, boolean busy, List<OnlineBook> books,
        List<OnlineLoan> loans, String error) {
      this.screen = screen;
      this.role = role;
      this.busy = busy;
      this.books = Collections.unmodifiableList(new ArrayList<>(books));
      this.loans = Collections.unmodifiableList(new ArrayList<>(loans));
      this.error = error;
    }
  }

  public static final class Event {
    public final String text;
    public final Map<String, Object> retry;
    Event(String text, Map<String, Object> retry) {
      this.text = text;
      this.retry = retry;
    }
  }

  private final FirebaseDataSource repository;
  private final MutableLiveData<State> state = new MutableLiveData<>();
  private final MutableLiveData<Event> event = new MutableLiveData<>();
  private String screen = "login";
  private String role = "reader";
  private boolean busy;
  private String error;
  private List<OnlineBook> books = Collections.emptyList();
  private List<OnlineLoan> loans = Collections.emptyList();
  private int generation;

  public FirebaseViewModel() { this(new FirebaseRepository()); }

  FirebaseViewModel(FirebaseDataSource repository) {
    this.repository = repository;
    refresh();
  }
  public LiveData<State> getState() { return state; }
  public LiveData<Event> getEvent() { return event; }
  public void consumeEvent() { event.setValue(null); }

  private void publish() {
    state.setValue(new State(screen, role, busy, books, loans, error));
  }

  public void refresh() {
    int request = ++generation;
    OnlineSession user = repository.user();
    error = null;
    books = Collections.emptyList();
    loans = Collections.emptyList();
    if (user == null) {
      busy = false;
      screen = "login";
      publish();
      return;
    }
    if (!user.emailVerified) {
      busy = false;
      screen = "verify";
      publish();
      return;
    }
    screen = "catalogue";
    busy = true;
    publish();
    repository.token().addOnCompleteListener(token -> {
      if (request != generation) return;
      if (!token.isSuccessful()) {
        failLoad("Không thể kết nối hoặc xác thực phiên. Kiểm tra mạng và thử lại.");
        return;
      }
      role = token.getResult();
      repository.books().addOnCompleteListener(result -> {
        if (request != generation) return;
        if (!result.isSuccessful()) {
          failLoad("Không đọc được danh mục. Kiểm tra mạng, email xác minh, App Check và Firestore Rules.");
          return;
        }
        books = result.getResult();
        boolean staff = role.equals("admin") || role.equals("librarian");
        repository.loans(staff, user.userId).addOnCompleteListener(loanResult -> {
          if (request != generation) return;
          if (loanResult.isSuccessful()) {
            loans = loanResult.getResult();
            busy = false;
            publish();
          } else {
            failLoad("Không đọc được phiếu mượn. Kiểm tra quyền hoặc kết nối.");
          }
        });
      });
    });
  }

  private void failLoad(String message) {
    busy = false;
    error = message;
    publish();
  }

  public void signOut() {
    generation++;
    event.setValue(null);
    repository.signOut();
    refresh();
  }

  private void authAction(Task<?> task, String success, String failure, boolean reload) {
    int request = generation;
    busy = true;
    publish();
    task.addOnCompleteListener(result -> {
      if (request != generation) return;
      busy = false;
      if (result.isSuccessful() && reload) refresh();
      else publish();
      if (result.isSuccessful() && success != null) event.setValue(new Event(success, null));
      else if (!result.isSuccessful()) event.setValue(new Event(failure, null));
    });
  }

  public void signIn(String email, String password) {
    if (!busy) authAction(repository.signIn(email, password), null,
        "Không thể đăng nhập. Kiểm tra thông tin hoặc thử lại sau.", true);
  }
  public void register(String email, String password) {
    if (!busy) authAction(repository.register(email, password), null,
        "Không thể tạo tài khoản. Kiểm tra email, chính sách mật khẩu hoặc thử lại sau.", true);
  }
  public void resetPassword(String email) {
    if (!busy) authAction(repository.resetPassword(email),
        "Nếu email đủ điều kiện, hướng dẫn khôi phục sẽ được gửi. Hãy kiểm tra hộp thư.",
        "Nếu email đủ điều kiện, hướng dẫn khôi phục sẽ được gửi. Hãy kiểm tra hộp thư.", false);
  }
  public void sendVerification() {
    if (!busy) authAction(repository.sendVerification(), "Đã gửi email xác minh.",
        "Chưa gửi được email. Vui lòng thử lại sau.", false);
  }
  public void checkVerification() {
    if (!busy) authAction(repository.refreshUser(), null, "Không thể cập nhật phiên đăng nhập.", true);
  }

  public void command(String command, Map<String, Object> values) {
    Map<String, Object> data = new HashMap<>(values);
    data.put("command", command);
    data.put("requestId", UUID.randomUUID().toString());
    send(data);
  }

  public void send(Map<String, Object> data) {
    if (busy) return;
    int request = generation;
    busy = true;
    publish();
    repository.command(data).addOnCompleteListener(result -> {
      if (request != generation) return;
      busy = false;
      if (result.isSuccessful()) {
        refresh();
        event.setValue(new Event("Máy chủ đã xác nhận thao tác.", null));
      } else {
        publish();
        Exception exception = result.getException();
        String detail = exception instanceof OnlineCommandException
            && ((OnlineCommandException) exception).preconditionFailed
            ? exception.getMessage()
            : "Chưa xác định được kết quả. Có thể thử lại an toàn bằng cùng mã yêu cầu.";
        event.setValue(new Event(detail, new HashMap<>(data)));
      }
    });
  }
}

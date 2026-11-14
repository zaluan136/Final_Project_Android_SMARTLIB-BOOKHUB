package com.example.smartlibandbookhub;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import com.example.smartlibandbookhub.data.LibraryRepository;
import com.example.smartlibandbookhub.data.LibraryDataSource;
import com.example.smartlibandbookhub.domain.Library;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LibraryViewModel extends AndroidViewModel {
  public enum SortBy {
    DEFAULT("Mặc định"),
    TITLE_AZ("Tên (A → Z)"),
    TITLE_ZA("Tên (Z → A)"),
    AVAILABLE_FIRST("Còn sẵn trước"),
    COPIES_DESC("Nhiều bản nhất");

    final String label;
    SortBy(String label) { this.label = label; }
  }

  private interface Operation {
    void apply(Library library, Library.Role role, long today);
  }

  public static final class UiState {
    public final Library library;
    public final Library.Role role;
    public final int tab;
    public final String query;
    public final String category;
    public final boolean loading;
    public final Exception error;

    UiState(Library library, Library.Role role, int tab, String query, String category,
        boolean loading, Exception error) {
      this.library = library;
      this.role = role;
      this.tab = tab;
      this.query = query;
      this.category = category;
      this.loading = loading;
      this.error = error;
    }
  }

  public static final class Result {
    public final String message;
    public final Exception error;
    public final boolean form;

    Result(String message, Exception error, boolean form) {
      this.message = message;
      this.error = error;
      this.form = form;
    }
  }

  private final LibraryDataSource repository;
  private final SavedStateHandle savedState;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final MutableLiveData<UiState> state = new MutableLiveData<>();
  private Library library;
  private Exception error;
  private final MutableLiveData<Result> result = new MutableLiveData<>();
  private boolean busy;

  public LibraryViewModel(@NonNull Application application, SavedStateHandle savedState) {
    super(application);
    this.savedState = savedState;
    repository = new LibraryRepository(application);
    publish();
  }

  LibraryViewModel(@NonNull Application application, SavedStateHandle savedState, LibraryDataSource repository) {
    super(application);
    this.savedState = savedState;
    this.repository = repository;
    publish();
  }

  public <T> T getSaved(String key, T fallback) {
    T value = savedState.get(key);
    return value == null ? fallback : value;
  }

  public void setRole(Library.Role role) { savedState.set("role", role.name()); publish(); }
  public void setTab(int tab) { savedState.set("tab", tab); publish(); }
  public void setQuery(String query) { savedState.set("query", query); publish(); }
  public void setCategory(String category) { savedState.set("category", category); publish(); }
  public void setSortBy(SortBy sortBy) { savedState.set("sort_by", sortBy.name()); publish(); }
  public void setOnlyAvailable(boolean onlyAvailable) { savedState.set("only_available", onlyAvailable); publish(); }


  public java.util.List<Library.Book> getFilteredBooks(String query, String category,
      boolean onlyAvailable, SortBy sortBy) {
    if (library == null) return java.util.Collections.emptyList();
    java.util.List<Library.Book> books = new java.util.ArrayList<>(library.search(query, category));
    if (onlyAvailable) books.removeIf(book -> library.available(book.id) <= 0);
    switch (sortBy) {
      case TITLE_AZ:
        books.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        break;
      case TITLE_ZA:
        books.sort((a, b) -> b.title.compareToIgnoreCase(a.title));
        break;
      case AVAILABLE_FIRST:
        books.sort((a, b) -> Integer.compare(library.available(b.id), library.available(a.id)));
        break;
      case COPIES_DESC:
        books.sort((a, b) -> Integer.compare(b.copies, a.copies));
        break;
      default:
        break;
    }
    return books;
  }

  public LiveData<UiState> getState() { return state; }

  private void publish() {
    state.setValue(new UiState(library,
        Library.Role.valueOf(getSaved("role", Library.Role.READER.name())),
        getSaved("tab", R.id.nav_explore), getSaved("query", ""),
        getSaved("category", "Tất cả"), busy, error));
  }
  public LiveData<Result> getResult() { return result; }

  public void consumeResult() { result.setValue(null); }

  public void load(long today) {
    execute(null, Library.Role.READER, today, null, false);
  }

  public void archive(Library.Role role, String bookId, long today) {
    execute((library, r, day) -> library.archive(r, bookId), role, today, "Đã ẩn sách.", false);
  }

  public void reserve(Library.Role role, String bookId, long pickupDay, long today) {
    execute((library, r, day) -> library.reserve(r, Library.READER_ID, bookId, pickupDay, day),
        role, today, "Đã ghi nhận yêu cầu. Xem trạng thái tại Phiếu mượn.", false);
  }

  public void checkout(Library.Role role, String loanId, long today) {
    execute((library, r, day) -> library.checkout(r, loanId, day), role, today, "Đã bàn giao sách.", false);
  }

  public void returnBook(Library.Role role, String loanId, long today) {
    execute((library, r, day) -> library.returnBook(r, loanId, day), role, today, "Đã nhận trả sách.", false);
  }

  public void renew(Library.Role role, String loanId, long today) {
    execute((library, r, day) -> library.renew(r, Library.READER_ID, loanId, day),
        role, today, "Đã gia hạn phiếu.", false);
  }

  public void cancel(Library.Role role, String loanId, long today) {
    execute((library, r, day) -> library.cancel(r, Library.READER_ID, loanId, day),
        role, today, "Đã hủy yêu cầu.", false);
  }

  public void saveBook(Library.Role role, Library.Book book, long today) {
    execute((library, r, day) -> library.saveBook(r, book, day), role, today, "Đã lưu đầu sách.", true);
  }

  public void saveRules(Library.Role role, int max, int days, int renewal, int count, long fine, long today) {
    execute((library, r, day) -> library.rules(r, max, days, renewal, count, fine),
        role, today, "Đã cập nhật quy định.", true);
  }

  private void execute(Operation operation, Library.Role role, long today, String message, boolean form) {
    if (busy) return;
    busy = true;
    error = null;
    publish();
    executor.execute(() -> {
      Library next = null;
      Exception failure = null;
      try {
        next = operation == null
            ? repository.read(today)
            : repository.change(today, state -> operation.apply(state, role, today));
      } catch (Exception e) {
        failure = e;
      }
      Library updated = next;
      Exception error = failure;
      main.post(() -> {
        busy = false;
        if (updated != null) library = updated;
        this.error = error;
        publish();
        if (error != null || message != null) result.setValue(new Result(message, error, form));
      });
    });
  }

  @Override
  protected void onCleared() {
    executor.shutdown();
  }
}

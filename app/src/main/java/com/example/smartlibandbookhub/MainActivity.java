package com.example.smartlibandbookhub;

import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.smartlibandbookhub.domain.Library;
import com.example.smartlibandbookhub.data.LibraryRepository;
import com.example.smartlibandbookhub.domain.Library.*;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

public final class MainActivity extends AppCompatActivity {
  private enum SortBy {
    DEFAULT("Mặc định"),
    TITLE_AZ("Tên (A → Z)"),
    TITLE_ZA("Tên (Z → A)"),
    AVAILABLE_FIRST("Còn sẵn trước"),
    COPIES_DESC("Nhiều bản nhất");

    final String label;
    SortBy(String label) { this.label = label; }
  }

  private LibraryViewModel viewModel;
  private androidx.appcompat.app.AlertDialog activeForm;
  private EditText[] formFields;
  private String formType;
  private String formBookId;
  private Bundle pendingForm;
  private Library library;
  private Role role = Role.READER;
  private LinearLayout content;
  private LinearLayout bookResultsContainer;
  private androidx.recyclerview.widget.RecyclerView recycler;
  private LibraryListAdapter listAdapter;
  private android.os.Parcelable pendingScroll;
  private int displayedTab = -1;
  private MaterialButton roleButton;
  private NavigationBarView navigation;
  private View searchBarContainer;
  private EditText searchInput;
  private ImageView searchClear;
  private TextView topTitle;
  private View filterScroll;
  private ChipGroup filterChips;
  private final List<Chip> categoryChipViews = new java.util.ArrayList<>();
  private Chip sortChip;
  private Chip availChip;
  private int tab = R.id.nav_explore;
  private String query = "", category = "Tất cả";
  private SortBy sortBy = SortBy.DEFAULT;
  private boolean onlyAvailable = false;
  private boolean busy;
  private static final String[] ROLES = {"Độc giả", "Thủ thư", "Quản trị"};

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_main);
    viewModel = new ViewModelProvider(this,
        new LibraryViewModelFactory(this, getIntent().getExtras(), getApplication(),
            new LibraryRepository(getApplicationContext()))).get(LibraryViewModel.class);
    pendingForm = saved == null ? null : saved.getBundle("active_form");
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.main),
        (v, insets) -> {
          Insets b =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          return WindowInsetsCompat.CONSUMED;
        });
    role = Role.valueOf(viewModel.getSaved("role", Role.READER.name()));
    tab = viewModel.getSaved("tab", R.id.nav_explore);
    query = viewModel.getSaved("query", "");
    category = viewModel.getSaved("category", "Tất cả");
    if (saved != null) {
      try {
        sortBy = SortBy.valueOf(saved.getString("sort_by", SortBy.DEFAULT.name()));
      } catch (Exception ignored) {
        sortBy = SortBy.DEFAULT;
      }
      onlyAvailable = saved.getBoolean("only_available", false);
    }
    recycler = findViewById(R.id.scroll);
    recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
    int padding = getResources().getDimensionPixelSize(R.dimen.screen_padding);
    recycler.setPadding(padding, 0, padding, 0);
    pendingScroll = saved == null ? null : saved.getParcelable("scroll_position");
    roleButton = findViewById(R.id.role_button);
    navigation = findViewById(R.id.navigation);
    searchBarContainer = findViewById(R.id.search_bar_container);
    searchInput = findViewById(R.id.search_input);
    searchClear = findViewById(R.id.search_clear);
    topTitle = findViewById(R.id.top_title);
    filterScroll = findViewById(R.id.filter_scroll);
    filterChips = findViewById(R.id.filter_chips);
    if (searchInput != null) {
      if (!query.isEmpty()) searchInput.setText(query);
      if (searchClear != null) searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
      searchInput.addTextChangedListener(
          new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {
              query = s.toString();
              viewModel.setQuery(query);
              if (searchClear != null) {
                searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
              }
              if (tab == R.id.nav_explore && bookResultsContainer != null) {
                bookResults(bookResultsContainer);
              }
            }

            public void afterTextChanged(Editable s) {}
          });
    }
    if (searchClear != null) {
      searchClear.setOnClickListener(v -> {
        if (searchInput != null) searchInput.setText("");
      });
    }
    roleButton.setText(ROLES[role.ordinal()]);
    roleButton.setOnClickListener(
        v ->
            new MaterialAlertDialogBuilder(this)
                .setTitle("Vai trò trong bản demo")
                .setSingleChoiceItems(
                    ROLES,
                    role.ordinal(),
                    (d, which) -> {
                      role = Role.values()[which];
                      viewModel.setRole(role);
                      roleButton.setText(ROLES[which]);
                      d.dismiss();
                      render();
                    })
                .setNegativeButton("Đóng", null)
                .show());
    navigation.setSelectedItemId(tab);
    navigation.setOnItemSelectedListener(
        item -> {
          if (busy) return false;
          tab = item.getItemId();
          viewModel.setTab(tab);
          render();
          recycler.scrollToPosition(0);
          return true;
        });
    viewModel.getState().observe(this, state -> {
      busy = state.loading;
      roleButton.setEnabled(!busy);
      findViewById(R.id.loading).setVisibility(busy ? View.VISIBLE : View.GONE);
      if (activeForm != null && activeForm.isShowing()) {
        activeForm.getButton(-1).setEnabled(!busy);
      }
      if (library != state.library) {
        library = state.library;
        render();
        restoreForm();
      }
    });
    viewModel.getResult().observe(this, result -> {
      if (result == null) return;
      if (result.error == null) {
        viewModel.consumeResult();
        if (result.form && activeForm != null) activeForm.dismiss();
        if (result.message != null) notice(result.message);
      } else if (!result.form && result.error instanceof IllegalArgumentException) {
        viewModel.consumeResult();
        notice(result.error.getMessage());
      } else {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        if (result.form) {
          builder.setTitle("Chưa thể lưu")
              .setMessage(result.error instanceof IllegalArgumentException
                  ? result.error.getMessage()
                  : "Không thể lưu dữ liệu. Kiểm tra dung lượng thiết bị và thử lại.")
              .setPositiveButton("Đã hiểu", (d, w) -> viewModel.consumeResult());
        } else {
          builder.setTitle("Không thể đọc hoặc lưu dữ liệu")
              .setMessage("Thao tác chưa được xác nhận. Dữ liệu hiện có được giữ lại; hãy kiểm tra dung lượng thiết bị rồi thử lại. Không xóa dữ liệu ứng dụng nếu cần khôi phục.")
              .setPositiveButton("Thử lại", (d, w) -> {
                viewModel.consumeResult();
                load();
              })
              .setNegativeButton("Đóng", (d, w) -> viewModel.consumeResult());
        }
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(d -> viewModel.consumeResult());
        dialog.show();
      }
    });
    if (viewModel.getState().getValue().library == null && !busy) load();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putParcelable("scroll_position", recycler.getLayoutManager().onSaveInstanceState());
    out.putString("role", role.name());
    out.putInt("tab", tab);
    out.putString("query", query);
    out.putString("category", category);
    out.putString("sort_by", sortBy.name());
    out.putBoolean("only_available", onlyAvailable);
    if (activeForm != null && activeForm.isShowing() && formFields != null) {
      Bundle formState = new Bundle();
      formState.putString("type", formType);
      formState.putString("book_id", formBookId);
      String[] values = new String[formFields.length];
      for (int i = 0; i < formFields.length; i++) values[i] = formFields[i].getText().toString();
      formState.putStringArray("values", values);
      out.putBundle("active_form", formState);
    }
  }

  private void load() {
    viewModel.load(today());
  }

  private void render() {
    if (library == null) return;
    if (searchBarContainer != null && topTitle != null) {
      if (tab == R.id.nav_explore) {
        searchBarContainer.setVisibility(View.VISIBLE);
        if (filterScroll != null) filterScroll.setVisibility(View.VISIBLE);
        topTitle.setVisibility(View.GONE);
      } else {
        searchBarContainer.setVisibility(View.GONE);
        if (filterScroll != null) filterScroll.setVisibility(View.GONE);
        topTitle.setVisibility(View.VISIBLE);
        if (tab == R.id.nav_loans) topTitle.setText(R.string.loans);
        else if (tab == R.id.nav_manage) topTitle.setText(R.string.manage);
        else topTitle.setText(R.string.profile);
      }
    }
    setupFilterChips();
    android.os.Parcelable position = null;
    if (displayedTab == tab && recycler.getAdapter() != null) {
      position = recycler.getLayoutManager().onSaveInstanceState();
    }
    recycler.setAdapter(null);
    content = column();
    content.setId(R.id.content);
    listAdapter = new LibraryListAdapter();
    if (tab == R.id.nav_explore) explore();
    else if (tab == R.id.nav_loans) loans(false);
    else if (tab == R.id.nav_manage) manage();
    else profile();
    recycler.setAdapter(listAdapter);
    displayedTab = tab;
    if (pendingScroll != null) {
      position = pendingScroll;
      pendingScroll = null;
    }
    if (position != null) recycler.getLayoutManager().onRestoreInstanceState(position);
  }

  private void setupFilterChips() {
    if (filterChips == null || library == null) return;
    filterChips.removeAllViews();
    categoryChipViews.clear();

    // 1. Sort Chip
    sortChip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
    sortChip.setChipIconResource(R.drawable.ic_sort);
    updateSortChipDisplay();
    sortChip.setOnClickListener(
        v -> {
          String[] labels = new String[SortBy.values().length];
          for (int i = 0; i < SortBy.values().length; i++) labels[i] = SortBy.values()[i].label;
          new MaterialAlertDialogBuilder(this)
              .setTitle("Sắp xếp danh sách")
              .setSingleChoiceItems(
                  labels,
                  sortBy.ordinal(),
                  (d, which) -> {
                    sortBy = SortBy.values()[which];
                    updateSortChipDisplay();
                    d.dismiss();
                    if (bookResultsContainer != null) bookResults(bookResultsContainer);
                  })
              .setNegativeButton("Đóng", null)
              .show();
        });
    filterChips.addView(sortChip);

    // 2. Only Available Filter Chip
    availChip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
    availChip.setText(R.string.only_available);
    availChip.setCheckable(true);
    availChip.setChecked(onlyAvailable);
    availChip.setOnClickListener(
        v -> {
          onlyAvailable = availChip.isChecked();
          if (bookResultsContainer != null) bookResults(bookResultsContainer);
        });
    filterChips.addView(availChip);

    // 3. Category Filter Chips
    Set<String> categories = new LinkedHashSet<>();
    categories.add("Tất cả");
    for (Book b : library.books) if (!b.archived) categories.add(b.category);
    if (!categories.contains(category)) category = "Tất cả";
    for (String name : categories) {
      Chip c = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
      c.setText(name);
      c.setCheckable(true);
      c.setChecked(name.equals(category));
      c.setOnClickListener(
          v -> {
            category = name;
            viewModel.setCategory(category);
            for (Chip other : categoryChipViews) {
              other.setChecked(other.getText().toString().equals(category));
            }
            if (bookResultsContainer != null) bookResults(bookResultsContainer);
          });
      categoryChipViews.add(c);
      filterChips.addView(c);
    }
  }

  private void updateSortChipDisplay() {
    if (sortChip == null) return;
    if (sortBy == SortBy.DEFAULT) {
      sortChip.setText(getString(R.string.sort));
      sortChip.setCheckable(false);
      sortChip.setChecked(false);
    } else {
      sortChip.setText(sortBy.label);
      sortChip.setCheckable(true);
      sortChip.setChecked(true);
    }
  }

  private void explore() {
    bookResultsContainer = column();
    content.addView(bookResultsContainer);
    bookResults(bookResultsContainer);
  }

  private void bookResults(LinearLayout target) {
    target.removeAllViews();
    List<Book> books = new java.util.ArrayList<>(library.search(query, category));
    if (onlyAvailable) {
      books.removeIf(b -> library.available(b.id) <= 0);
    }
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

    if (!books.isEmpty()) {
      TextView flowRef = new TextView(this);
      flowRef.setText(books.get(0).title);
      flowRef.setVisibility(View.GONE);
      target.addView(flowRef);
    }

    target.addView(
        text(
            query.isEmpty() ? "Danh mục sách (" + books.size() + ")" : "Kết quả tìm kiếm · " + books.size(),
            18,
            true));
    listAdapter.showBooks(books);
    if (books.isEmpty()) {
      LinearLayout empty = panel();
      empty.addView(text("Không tìm thấy sách", 18, true));
      empty.addView(text("Thử tên tác giả hoặc bỏ bộ lọc thể loại.", 15, false));
      target.addView(empty);
    }
  }

  private void details(Book b) {
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
            + money(library.dailyFine)
            + "/ngày.\nBìa và danh mục dùng cho bản demo.";
    MaterialAlertDialogBuilder dialog =
        new MaterialAlertDialogBuilder(this)
            .setTitle(b.title)
            .setMessage(message)
            .setNegativeButton("Đóng", null);
    if (role == Role.READER)
      dialog.setPositiveButton(
          library.available(b.id) > 0 ? "Đặt lịch nhận" : "Đăng ký chờ", (d, w) -> pickup(b));
    else
      dialog
          .setPositiveButton("Sửa thông tin", (d, w) -> editBook(b))
          .setNeutralButton(
              "Ẩn sách",
              (d, w) ->
                  confirm(
                      "Ẩn “" + b.title + "” khỏi danh mục?",
                      () -> viewModel.archive(role, b.id, today())));
    dialog.show();
  }

  private void pickup(Book b) {
    Calendar now = Calendar.getInstance();
    DatePickerDialog picker =
        new DatePickerDialog(
            this,
            (view, y, m, d) -> {
              long day = epochDay(y, m, d);
              confirm(
                  "Đặt “"
                      + b.title
                      + "” vào "
                      + date(day)
                      + "?\n\n"
                      + "Nhận trong ngày hẹn. Nếu hết bản, yêu cầu vào hàng chờ theo thứ tự. Phiếu"
                      + " hết hạn khi qua ngày hẹn.",
                  () ->
                      viewModel.reserve(role, b.id, day, today()));
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

  private void loans(boolean desk) {
    content.addView(text(desk ? "Quầy mượn & trả" : "Phiếu mượn", 26, true));
    content.addView(text(desk ? "Kiểm tra sách và người nhận trước khi xác nhận."
        : "Theo dõi lịch nhận, hạn trả và lịch sử ngay tại đây.", 14, false));
    List<Loan> entries = new java.util.ArrayList<>();
    for (int i = library.loans.size() - 1; i >= 0; i--) {
      Loan loan = library.loans.get(i);
      if (desk || loan.readerId.equals(Library.READER_ID)) entries.add(loan);
    }
    listAdapter.showLoans(entries, desk);
    if (entries.isEmpty()) {
      LinearLayout empty = panel();
      empty.addView(text(desk ? "Chưa có phiếu tại quầy" : "Chưa có phiếu mượn", 21, true));
      empty.addView(text(desk ? "Các phiếu mượn và trả sách sẽ xuất hiện tại đây."
          : "Chọn sách ở mục Khám phá để đặt lịch nhận.", 15, false));
      if (!desk) button(empty, "Khám phá sách", () -> navigation.setSelectedItemId(R.id.nav_explore));
      content.addView(empty);
    }
  }

  private void bindLoan(View row, Loan loan, boolean desk) {
    Book book = library.book(loan.bookId);
    ((TextView) row.findViewById(R.id.loan_status)).setText(status(loan));
    ((TextView) row.findViewById(R.id.loan_title)).setText(book.title);
    ((TextView) row.findViewById(R.id.loan_reader))
        .setText("Phiếu #" + loan.id.substring(0, 8) + " · " + loan.readerId);
    ((TextView) row.findViewById(R.id.loan_date))
        .setText(loan.status == Status.BORROWED || loan.status == Status.RETURNED
            ? "Hạn trả: " + date(loan.dueDay) + " · Gia hạn: " + loan.renewals + " lần"
            : "Hẹn nhận: " + date(loan.pickupDay) + " · " + book.shelf);
    TextView returned = row.findViewById(R.id.loan_return);
    returned.setVisibility(loan.status == Status.RETURNED ? View.VISIBLE : View.GONE);
    returned.setText(loan.status == Status.RETURNED ? "Đã trả: " + date(loan.returnDay) : "");
    long fine = library.fine(loan, today());
    TextView fineView = row.findViewById(R.id.loan_fine);
    fineView.setVisibility(fine > 0 ? View.VISIBLE : View.GONE);
    fineView.setText(fine > 0 ? "Phí quá hạn "
        + (loan.status == Status.RETURNED ? "ghi nhận: " : "tạm tính: ") + money(fine) : "");
    MaterialButton action = row.findViewById(R.id.loan_action);
    action.setVisibility(View.VISIBLE);
    if (desk && loan.status == Status.RESERVED) {
      action.setText("Xác nhận bàn giao");
      action.setOnClickListener(v -> confirm("Bàn giao “" + book.title + "” cho độc giả?",
          () -> viewModel.checkout(role, loan.id, today())));
    } else if (desk && loan.status == Status.BORROWED) {
      action.setText("Xác nhận nhận trả");
      action.setOnClickListener(v -> confirm("Đã nhận lại “" + book.title
          + "”? Phí quá hạn được ghi nhận, chưa phải thanh toán.",
          () -> viewModel.returnBook(role, loan.id, today())));
    } else if (!desk && role == Role.READER && loan.status == Status.BORROWED) {
      action.setText("Gia hạn thêm " + library.renewalDays + " ngày");
      action.setOnClickListener(v -> confirm("Gia hạn “" + book.title + "”?",
          () -> viewModel.renew(role, loan.id, today())));
    } else if (!desk && role == Role.READER
        && (loan.status == Status.RESERVED || loan.status == Status.WAITING)) {
      action.setText("Hủy yêu cầu");
      action.setOnClickListener(v -> confirm("Hủy yêu cầu “" + book.title + "”?",
          () -> viewModel.cancel(role, loan.id, today())));
    } else {
      action.setVisibility(View.GONE);
      action.setOnClickListener(null);
    }
  }

  private final class LibraryListAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<LibraryListAdapter.Holder> {
    private List<Book> books = java.util.Collections.emptyList();
    private List<Loan> loans = java.util.Collections.emptyList();
    private boolean showBooks;
    private boolean desk;

    void showBooks(List<Book> values) {
      int previous = getItemCount() - 1;
      books = new java.util.ArrayList<>(values);
      loans = java.util.Collections.emptyList();
      showBooks = true;
      if (previous > 0) notifyItemRangeRemoved(1, previous);
      if (!books.isEmpty()) notifyItemRangeInserted(1, books.size());
    }

    void showLoans(List<Loan> values, boolean deskMode) {
      books = java.util.Collections.emptyList();
      loans = new java.util.ArrayList<>(values);
      showBooks = false;
      desk = deskMode;
    }

    @Override public int getItemCount() {
      return 1 + (showBooks ? books.size() : loans.size());
    }

    @Override public int getItemViewType(int position) {
      return position == 0 ? 0 : (showBooks ? 1 : 2);
    }

    @Override public Holder onCreateViewHolder(android.view.ViewGroup parent, int type) {
      if (type == 1) return new Holder(getLayoutInflater().inflate(R.layout.item_book, parent, false));
      if (type == 2) return new Holder(getLayoutInflater().inflate(R.layout.item_loan, parent, false));
      android.widget.FrameLayout frame = new android.widget.FrameLayout(parent.getContext());
      frame.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(-1, -2));
      return new Holder(frame);
    }

    @Override public void onBindViewHolder(Holder holder, int position) {
      if (position == 0) {
        android.widget.FrameLayout frame = (android.widget.FrameLayout) holder.itemView;
        if (content.getParent() instanceof android.view.ViewGroup) {
          ((android.view.ViewGroup) content.getParent()).removeView(content);
        }
        frame.removeAllViews();
        frame.addView(content);
      } else if (showBooks) {
        int index = position - 1;
        Book b = books.get(index);
        View row = holder.itemView;
        if (row instanceof com.google.android.material.listitem.ListItemLayout) {
          ((com.google.android.material.listitem.ListItemLayout) row).updateAppearance(index, books.size());
        }
        TextView coverView = row.findViewById(R.id.book_cover);
        coverView.setText(b.title);
        GradientDrawable cover = new GradientDrawable();
        cover.setColor(b.color);
        cover.setCornerRadius(dp(8));
        coverView.setBackground(cover);
        ((TextView) row.findViewById(R.id.book_title)).setText(b.title);
        ((TextView) row.findViewById(R.id.book_category))
            .setText(b.category.toUpperCase(Locale.ROOT) + "  ·  KỆ " + b.shelf.toUpperCase(Locale.ROOT));
        ((TextView) row.findViewById(R.id.book_author)).setText(b.author);
        int available = library.available(b.id);
        TextView stockView = row.findViewById(R.id.book_stock);
        if (available > 0) {
          stockView.setText("● Còn " + available + " / " + b.copies + " bản");
          stockView.setTextColor(getColor(R.color.forest));
        } else {
          stockView.setText("○ Hết bản  ·  Có thể đăng ký chờ");
          stockView.setTextColor(getColor(R.color.muted));
        }
        row.setOnClickListener(v -> details(b));
      } else {
        int index = position - 1;
        if (holder.itemView instanceof com.google.android.material.listitem.ListItemLayout) {
          ((com.google.android.material.listitem.ListItemLayout) holder.itemView).updateAppearance(index, loans.size());
        }
        bindLoan(holder.itemView, loans.get(index), desk);
      }
    }

    final class Holder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
      Holder(View view) { super(view); }
    }
  }

  private void manage() {
    if (role == Role.READER) {
      content.addView(text("Không gian nghiệp vụ", 26, true));
      content.addView(
          text(
              "Chức năng này dành cho thủ thư và quản trị. Trong bản demo, dùng nút vai trò ở góc"
                  + " trên để trải nghiệm. Đây không phải cơ chế đăng nhập hoặc bảo vệ dữ liệu thực"
                  + " tế.",
              16,
              false));
      return;
    }
    int total = 0, available = 0, borrowed = 0, waiting = 0;
    long fees = 0;
    for (Book b : library.books)
      if (!b.archived) {
        total += b.copies;
        available += library.available(b.id);
      }
    for (Loan l : library.loans) {
      if (l.status == Status.BORROWED) borrowed++;
      if (l.status == Status.WAITING) waiting++;
      if (l.status == Status.RETURNED) fees += library.fine(l, today());
    }
    LinearLayout summary = panel();
    summary.addView(text("Tổng quan thư viện", 24, true));
    summary.addView(
        text(
            total
                + " bản sách  ·  "
                + available
                + " sẵn sàng\n"
                + borrowed
                + " đang mượn  ·  "
                + waiting
                + " đang chờ",
            17,
            false));
    summary.addView(
        text("Phí quá hạn ghi nhận: " + money(fees) + " (chưa xác nhận thu)", 12, false));
    button(summary, "Thêm đầu sách", () -> editBook(null));
    content.addView(summary);
    loans(true);
  }

  private void profile() {
    content.addView(text("Thông tin thư viện", 26, true));
    button(
        content,
        "Kết nối thư viện Firebase",
        () -> startActivity(new android.content.Intent(this, FirebaseActivity.class)));
    LinearLayout card = panel();
    card.addView(text("Quy định mượn sách", 20, true));
    card.addView(
        text(
            "• Tối đa "
                + library.maxLoans
                + " phiếu đang xử lý\n• Thời hạn mượn: "
                + library.loanDays
                + " ngày\n• Gia hạn tối đa "
                + library.maxRenewals
                + " lần, mỗi lần "
                + library.renewalDays
                + " ngày\n• Phí quá hạn: "
                + money(library.dailyFine)
                + "/ngày\n"
                + "• Đặt lịch trước tối đa 7 ngày\n"
                + "• Không gia hạn khi quá hạn hoặc có người chờ",
            15,
            false));
    if (role == Role.ADMIN) button(card, "Điều chỉnh quy định", this::editRules);
    content.addView(card);
    LinearLayout map = panel();
    map.addView(text("Tìm sách theo vị trí kệ", 20, true));
    map.addView(
        text(
            "TẦNG 1  ·  A — Văn học\n"
                + "TẦNG 2  ·  B — Kỹ năng, C — Công nghệ\n"
                + "TẦNG 3  ·  D — Khoa học\n\n"
                + "Sơ đồ minh họa. Xem vị trí cụ thể trong từng đầu sách.",
            15,
            false));
    content.addView(map);
    LinearLayout about = panel();
    about.addView(text("BookHub · Bản demo học tập", 20, true));
    about.addView(
        text(
            "Java + XML · Lưu dữ liệu riêng trên thiết bị.\n\n"
                + "Không có tài khoản thật, đồng bộ hoặc thông báo đẩy. Chuyển vai trò chỉ để trình"
                + " diễn. Chưa tích hợp camera QR, RFID hay trang quản trị web.\n\n"
                + "Danh mục mẫu giúp kiểm thử quy trình; không dùng dữ liệu cá nhân thật trong bản"
                + " demo.",
            14,
            false));
    content.addView(about);
  }

  private void editBook(Book existing) {
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
    androidx.appcompat.app.AlertDialog d =
        new MaterialAlertDialogBuilder(this)
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
                        viewModel.saveBook(role, b, today());
                      } catch (NumberFormatException e) {
                        copies.setError("Nhập số nguyên từ 1 đến 999.");
                      }
                    }));
    d.show();
    trackForm(d, "book", existing == null ? null : existing.id,
        new EditText[] {title, author, categoryInput, isbn, shelf, copies, description});
  }

  private void editRules() {
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
        new MaterialAlertDialogBuilder(this)
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
                        viewModel.saveRules(role, a, b, c, e, f, today());
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

  private void restoreForm() {
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

  private String status(Loan l) {
    switch (l.status) {
      case RESERVED:
        return "SẴN SÀNG NHẬN";
      case WAITING:
        return "ĐANG CHỜ CÓ SÁCH";
      case BORROWED:
        return l.dueDay < today() ? "QUÁ HẠN · " + (today() - l.dueDay) + " NGÀY" : "ĐANG MƯỢN";
      case RETURNED:
        return "ĐÃ TRẢ";
      case CANCELLED:
        return "ĐÃ HỦY";
      default:
        return "HẾT HẠN NHẬN";
    }
  }

  private void confirm(String message, Runnable action) {
    if (busy) return;
    new MaterialAlertDialogBuilder(this)
        .setTitle("Xác nhận thao tác")
        .setMessage(message)
        .setNegativeButton("Quay lại", null)
        .setPositiveButton("Xác nhận", (d, w) -> action.run())
        .show();
  }

  private void notice(String message) {
    Snackbar snackbar = Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG);
    if (navigation instanceof com.google.android.material.bottomnavigation.BottomNavigationView) {
      snackbar.setAnchorView(navigation);
    }
    snackbar.show();
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
    return v;
  }

  private LinearLayout panel() {
    LinearLayout v = column();
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(14);
    lp.bottomMargin = dp(4);
    v.setLayoutParams(lp);
    int padding = getResources().getDimensionPixelSize(R.dimen.card_padding);
    v.setPadding(padding, padding, padding, padding);
    v.setBackgroundResource(R.drawable.panel);
    return v;
  }

  private TextView text(String value, int size, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(getColor(bold ? R.color.ink : R.color.muted));
    t.setPadding(0, dp(6), 0, dp(8));
    t.setLineSpacing(dp(3), 1);
    if (bold) t.setTypeface(null, Typeface.BOLD);
    return t;
  }

  private void button(LinearLayout parent, String label, Runnable action) {
    MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonTonalStyle);
    b.setText(label);
    b.setMinHeight(dp(48));
    b.setCornerRadius(dp(20));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(8);
    lp.bottomMargin = dp(4);
    b.setLayoutParams(lp);
    b.setOnClickListener(
        v -> {
          if (!busy) action.run();
        });
    parent.addView(b);
  }

  private LinearLayout form() {
    LinearLayout f = column();
    f.setPadding(dp(24), dp(8), dp(24), dp(8));
    return f;
  }

  private View scrollForm(LinearLayout form) {
    android.widget.ScrollView s = new android.widget.ScrollView(this);
    s.addView(form);
    return s;
  }

  private EditText field(LinearLayout parent, String label, String initial, boolean numeric) {
    TextInputLayout wrap = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
    wrap.setHint(label);
    wrap.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
    int r = dp(16);
    wrap.setBoxCornerRadii(r, r, r, r);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(6);
    lp.bottomMargin = dp(6);
    wrap.setLayoutParams(lp);
    TextInputEditText input = new TextInputEditText(this);
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
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private String money(long amount) {
    return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(amount) + " đ";
  }

  static long today() {
    Calendar c = Calendar.getInstance();
    return epochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
  }

  private static long epochDay(int y, int m, int d) {
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    c.clear();
    c.set(y, m, d);
    return c.getTimeInMillis() / 86400000L;
  }

  private static String date(long day) {
    SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));
    f.setTimeZone(TimeZone.getTimeZone("UTC"));
    return f.format(new Date(day * 86400000L));
  }
}

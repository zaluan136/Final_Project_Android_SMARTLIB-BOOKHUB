package com.example.smartlibandbookhub;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.smartlibandbookhub.data.LibraryStore;
import com.example.smartlibandbookhub.domain.Library;
import com.example.smartlibandbookhub.domain.Library.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
  private LibraryStore store;
  private Library library;
  private Role role = Role.READER;
  private LinearLayout content;
  private MaterialButton roleButton;
  private BottomNavigationView navigation;
  private int tab = R.id.nav_explore;
  private String query = "", category = "Tất cả";
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private boolean busy;
  private static final String[] ROLES = {"Độc giả", "Thủ thư", "Quản trị"};

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_main);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.main),
        (v, insets) -> {
          Insets b =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          return WindowInsetsCompat.CONSUMED;
        });
    if (saved != null) {
      role = Role.valueOf(saved.getString("role", Role.READER.name()));
      tab = saved.getInt("tab", R.id.nav_explore);
      query = saved.getString("query", "");
      category = saved.getString("category", "Tất cả");
    }
    content = findViewById(R.id.content);
    roleButton = findViewById(R.id.role_button);
    navigation = findViewById(R.id.navigation);
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
          render();
          findViewById(R.id.scroll).scrollTo(0, 0);
          return true;
        });
    store = new LibraryStore(getApplicationContext());
    load();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("role", role.name());
    out.putInt("tab", tab);
    out.putString("query", query);
    out.putString("category", category);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (store != null && !busy) load();
  }

  @Override
  protected void onDestroy() {
    io.shutdown();
    super.onDestroy();
  }

  private void load() {
    run(null, null);
  }

  private void run(LibraryStore.Change change, String message) {
    if (busy) return;
    busy = true;
    roleButton.setEnabled(false);
    findViewById(R.id.loading).setVisibility(View.VISIBLE);
    io.execute(
        () -> {
          try {
            Library next = change == null ? store.read(today()) : store.change(today(), change);
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  library = next;
                  finishWork();
                  render();
                  if (message != null) notice(message);
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  finishWork();
                  if (e instanceof IllegalArgumentException) notice(e.getMessage());
                  else
                    new MaterialAlertDialogBuilder(this)
                        .setTitle("Không thể đọc hoặc lưu dữ liệu")
                        .setMessage(
                            "Thao tác chưa được xác nhận. Dữ liệu hiện có được giữ lại; hãy kiểm"
                                + " tra dung lượng thiết bị rồi thử lại. Không xóa dữ liệu ứng dụng"
                                + " nếu cần khôi phục.")
                        .setPositiveButton("Thử lại", (d, w) -> load())
                        .setNegativeButton("Đóng", null)
                        .show();
                });
          }
        });
  }

  private void finishWork() {
    busy = false;
    roleButton.setEnabled(true);
    findViewById(R.id.loading).setVisibility(View.GONE);
  }

  private void render() {
    if (library == null) return;
    content.removeAllViews();
    if (tab == R.id.nav_explore) explore();
    else if (tab == R.id.nav_loans) loans(false);
    else if (tab == R.id.nav_manage) manage();
    else profile();
  }

  private void explore() {
    LinearLayout hero = column();
    hero.setBackgroundResource(R.drawable.hero);
    hero.setPadding(dp(20), dp(20), dp(20), dp(20));
    TextView eyebrow = text("KHÔNG GIAN CỦA TRI THỨC", 11, true);
    eyebrow.setTextColor(0xFFB9DECD);
    hero.addView(eyebrow);
    TextView title = text("Cuốn sách tiếp theo,\ncảm hứng tiếp theo.", 24, true);
    title.setTextColor(Color.WHITE);
    hero.addView(title);
    TextView subtitle = text("Khám phá · Đặt lịch · Đọc theo cách của bạn", 12, false);
    subtitle.setTextColor(0xFFD5EADD);
    hero.addView(subtitle);
    content.addView(hero);
    TextView hint = text("Thư viện mở cửa 08:00–17:00 · Lịch minh họa", 12, false);
    hint.setPadding(0, dp(12), 0, dp(12));
    content.addView(hint);
    TextInputLayout input = new TextInputLayout(this);
    input.setHint(getString(R.string.search_hint));
    input.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
    input.setBoxCornerRadii(dp(14), dp(14), dp(14), dp(14));
    TextInputEditText search = new TextInputEditText(this);
    search.setSingleLine(true);
    search.setText(query);
    search.setTextSize(14);
    input.addView(search);
    content.addView(input);
    ChipGroup chips = new ChipGroup(this);
    chips.setSingleLine(true);
    chips.setSingleSelection(true);
    chips.setSelectionRequired(true);
    Set<String> categories = new LinkedHashSet<>();
    categories.add("Tất cả");
    for (Book b : library.books) if (!b.archived) categories.add(b.category);
    if (!categories.contains(category)) category = "Tất cả";
    LinearLayout results = column();
    for (String name : categories) {
      Chip c = new Chip(this);
      c.setText(name);
      c.setCheckable(true);
      c.setChecked(name.equals(category));
      c.setOnClickListener(
          v -> {
            category = name;
            bookResults(results);
          });
      chips.addView(c);
    }
    android.widget.HorizontalScrollView chipScroll = new android.widget.HorizontalScrollView(this);
    chipScroll.setHorizontalScrollBarEnabled(false);
    chipScroll.addView(chips);
    content.addView(chipScroll);
    content.addView(results);
    bookResults(results);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            query = s.toString();
            bookResults(results);
          }

          public void afterTextChanged(Editable e) {}
        });
  }

  private void bookResults(LinearLayout target) {
    target.removeAllViews();
    List<Book> books = library.search(query, category);
    target.addView(
        text(
            query.isEmpty() ? "Tủ sách dành cho bạn" : "Kết quả tìm kiếm · " + books.size(),
            20,
            true));
    if (books.isEmpty()) {
      target.addView(
          text("Chưa tìm thấy sách phù hợp. Thử tên tác giả hoặc bỏ bộ lọc thể loại.", 15, false));
      return;
    }
    for (Book b : books) {
      View row = getLayoutInflater().inflate(R.layout.item_book, target, false);
      ((TextView) row.findViewById(R.id.book_cover)).setText(b.title);
      GradientDrawable cover =
          new GradientDrawable(
              GradientDrawable.Orientation.TL_BR, new int[] {b.color, darken(b.color)});
      cover.setCornerRadius(dp(8));
      row.findViewById(R.id.book_cover).setBackground(cover);
      ((TextView) row.findViewById(R.id.book_title)).setText(b.title);
      ((TextView) row.findViewById(R.id.book_category))
          .setText(b.category.toUpperCase(Locale.ROOT));
      ((TextView) row.findViewById(R.id.book_author)).setText(b.author);
      int available = library.available(b.id);
      ((TextView) row.findViewById(R.id.book_stock))
          .setText(
              (available > 0 ? "Còn " + available + " bản" : "Hết bản · Có thể đăng ký chờ")
                  + "  •  "
                  + b.shelf);
      row.setOnClickListener(v -> details(b));
      target.addView(row);
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
                      () -> run(s -> s.archive(role, b.id), "Đã ẩn sách.")));
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
                      run(
                          s -> s.reserve(role, Library.READER_ID, b.id, day, today()),
                          "Đã ghi nhận yêu cầu. Xem trạng thái tại Phiếu mượn."));
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
    content.addView(text(desk ? "Quầy mượn & trả" : "Hành trình đọc của bạn", 26, true));
    content.addView(
        text(
            desk
                ? "Kiểm tra sách và người nhận trước khi xác nhận."
                : "Theo dõi lịch nhận, hạn trả và lịch sử ngay tại đây.",
            14,
            false));
    boolean any = false;
    for (int i = library.loans.size() - 1; i >= 0; i--) {
      Loan l = library.loans.get(i);
      if (!desk && !l.readerId.equals(Library.READER_ID)) continue;
      any = true;
      LinearLayout card = panel();
      Book b = library.book(l.bookId);
      card.addView(text(status(l), 12, true));
      card.addView(text(b.title, 19, true));
      card.addView(text("Phiếu #" + l.id.substring(0, 8) + " · " + l.readerId, 11, false));
      card.addView(
          text(
              l.status == Status.BORROWED || l.status == Status.RETURNED
                  ? "Hạn trả: " + date(l.dueDay) + " · Gia hạn: " + l.renewals + " lần"
                  : "Hẹn nhận: " + date(l.pickupDay) + " · " + b.shelf,
              14,
              false));
      if (l.status == Status.RETURNED)
        card.addView(text("Đã trả: " + date(l.returnDay), 13, false));
      long fine = library.fine(l, today());
      if (fine > 0)
        card.addView(
            text(
                "Phí quá hạn "
                    + (l.status == Status.RETURNED ? "ghi nhận: " : "tạm tính: ")
                    + money(fine),
                14,
                true));
      if (desk && l.status == Status.RESERVED)
        button(
            card,
            "Xác nhận bàn giao",
            () ->
                confirm(
                    "Bàn giao “" + b.title + "” cho độc giả?",
                    () -> run(s -> s.checkout(role, l.id, today()), "Đã bàn giao sách.")));
      if (desk && l.status == Status.BORROWED)
        button(
            card,
            "Xác nhận nhận trả",
            () ->
                confirm(
                    "Đã nhận lại “"
                        + b.title
                        + "”? Phí quá hạn được ghi nhận, chưa phải thanh toán.",
                    () -> run(s -> s.returnBook(role, l.id, today()), "Đã nhận trả sách.")));
      if (!desk && role == Role.READER && l.status == Status.BORROWED)
        button(
            card,
            "Gia hạn thêm " + library.renewalDays + " ngày",
            () ->
                confirm(
                    "Gia hạn “" + b.title + "”?",
                    () ->
                        run(
                            s -> s.renew(role, Library.READER_ID, l.id, today()),
                            "Đã gia hạn phiếu.")));
      if (!desk
          && role == Role.READER
          && (l.status == Status.RESERVED || l.status == Status.WAITING))
        button(
            card,
            "Hủy yêu cầu",
            () ->
                confirm(
                    "Hủy yêu cầu “" + b.title + "”?",
                    () ->
                        run(
                            s -> s.cancel(role, Library.READER_ID, l.id, today()),
                            "Đã hủy yêu cầu.")));
      content.addView(card);
    }
    if (!any) {
      LinearLayout empty = panel();
      empty.addView(text("Một hành trình mới đang chờ", 21, true));
      empty.addView(
          text(
              "Chọn một cuốn sách ở mục Khám phá và đặt ngày nhận. Phiếu mượn sẽ xuất hiện tại"
                  + " đây.",
              15,
              false));
      button(empty, "Khám phá sách", () -> navigation.setSelectedItemId(R.id.nav_explore));
      content.addView(empty);
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
    content.addView(text("Thư viện của chúng mình", 26, true));
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
                        saveForm(d, s -> s.saveBook(role, b, today()), "Đã lưu đầu sách.");
                      } catch (NumberFormatException e) {
                        copies.setError("Nhập số nguyên từ 1 đến 999.");
                      }
                    }));
    d.show();
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
                        saveForm(d, s -> s.rules(role, a, b, c, e, f), "Đã cập nhật quy định.");
                      } catch (NumberFormatException e) {
                        fine.setError("Các trường cần là số nguyên trong giới hạn ghi ở nhãn.");
                      }
                    }));
    d.show();
  }

  private void saveForm(
      androidx.appcompat.app.AlertDialog dialog, LibraryStore.Change change, String message) {
    if (busy) return;
    busy = true;
    dialog.getButton(-1).setEnabled(false);
    io.execute(
        () -> {
          try {
            Library next = store.change(today(), change);
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  busy = false;
                  library = next;
                  dialog.dismiss();
                  render();
                  notice(message);
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  busy = false;
                  dialog.getButton(-1).setEnabled(true);
                  new MaterialAlertDialogBuilder(this)
                      .setTitle("Chưa thể lưu")
                      .setMessage(
                          e instanceof IllegalArgumentException
                              ? e.getMessage()
                              : "Không thể lưu dữ liệu. Kiểm tra dung lượng thiết bị và thử lại.")
                      .setPositiveButton("Đã hiểu", null)
                      .show();
                });
          }
        });
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
    Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG)
        .setAnchorView(navigation)
        .show();
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
    v.setPadding(dp(20), dp(16), dp(20), dp(16));
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
    MaterialButton b = new MaterialButton(this);
    b.setText(label);
    b.setMinHeight(dp(48));
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
    TextInputLayout wrap = new TextInputLayout(this);
    wrap.setHint(label);
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

  private int darken(int c) {
    return Color.rgb(
        (int) (Color.red(c) * .68), (int) (Color.green(c) * .68), (int) (Color.blue(c) * .68));
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

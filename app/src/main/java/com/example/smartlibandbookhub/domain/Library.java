package com.example.smartlibandbookhub.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Local demonstration rules. Role selection is NOT authentication. Dates are epoch days. */
public final class Library {
  public enum Role {
    READER,
    LIBRARIAN,
    ADMIN
  }

  public enum Status {
    WAITING,
    RESERVED,
    BORROWED,
    RETURNED,
    CANCELLED,
    EXPIRED
  }

  public static final String READER_ID = "reader-demo";
  public final List<Book> books = new ArrayList<>();
  public final List<Loan> loans = new ArrayList<>();
  public int maxLoans = 3, loanDays = 14, renewalDays = 7, maxRenewals = 1;
  public long dailyFine = 3000;

  public static final class Book {
    public String id, title, author, category, isbn, shelf, description;
    public int copies, color;
    public boolean archived;

    public Book(
        String id,
        String title,
        String author,
        String category,
        String isbn,
        String shelf,
        String description,
        int copies,
        int color) {
      this.id = id;
      this.title = title;
      this.author = author;
      this.category = category;
      this.isbn = isbn;
      this.shelf = shelf;
      this.description = description;
      this.copies = copies;
      this.color = color;
    }
  }

  public static final class Loan {
    public String id, bookId, readerId;
    public Status status;
    public long pickupDay, dueDay, returnDay, finePerDay;
    public int renewals;

    public boolean active() {
      return status == Status.WAITING || status == Status.RESERVED || status == Status.BORROWED;
    }
  }

  public Book book(String id) {
    for (Book b : books) if (b.id.equals(id)) return b;
    throw new IllegalArgumentException("Không tìm thấy sách.");
  }

  public Loan loan(String id) {
    for (Loan l : loans) if (l.id.equals(id)) return l;
    throw new IllegalArgumentException("Không tìm thấy phiếu mượn.");
  }

  public int available(String id) {
    int used = 0;
    for (Loan l : loans)
      if (l.bookId.equals(id) && (l.status == Status.RESERVED || l.status == Status.BORROWED))
        used++;
    return book(id).copies - used;
  }

  public List<Book> search(String query, String category) {
    List<Book> result = new ArrayList<>();
    String q = normalize(query);
    for (Book b : books)
      if (!b.archived
          && (category.equals("Tất cả") || b.category.equals(category))
          && normalize(b.title + " " + b.author + " " + b.isbn).contains(q)) result.add(b);
    return result;
  }

  public static String normalize(String s) {
    return Normalizer.normalize(s, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replace('đ', 'd')
        .trim();
  }

  public Loan reserve(Role role, String reader, String bookId, long pickup, long today) {
    require(role == Role.READER, "Chỉ độc giả được đặt sách.");
    require(reader != null && !reader.trim().isEmpty(), "Thiếu thông tin độc giả.");
    Book b = book(bookId);
    require(!b.archived, "Sách đã ngừng phục vụ.");
    require(pickup >= today && pickup <= today + 7, "Ngày nhận phải trong 7 ngày tới.");
    int active = 0;
    for (Loan l : loans)
      if (l.readerId.equals(reader) && l.active()) {
        active++;
        require(!l.bookId.equals(bookId), "Bạn đã có một yêu cầu đang xử lý cho sách này.");
        require(
            l.status != Status.BORROWED || l.dueDay >= today,
            "Vui lòng trả sách quá hạn trước khi đặt thêm.");
      }
    require(active < maxLoans, "Bạn đã đạt giới hạn " + maxLoans + " phiếu đang xử lý.");
    Loan l = new Loan();
    l.id = UUID.randomUUID().toString();
    l.readerId = reader;
    l.bookId = bookId;
    l.pickupDay = pickup;
    l.status = available(bookId) > 0 ? Status.RESERVED : Status.WAITING;
    loans.add(l);
    return l;
  }

  public void cancel(Role role, String reader, String id, long today) {
    Loan l = loan(id);
    require(role == Role.READER && l.readerId.equals(reader), "Bạn không được hủy phiếu này.");
    require(
        l.status == Status.RESERVED || l.status == Status.WAITING,
        "Chỉ được hủy yêu cầu chưa nhận sách.");
    l.status = Status.CANCELLED;
    promote(l.bookId, today);
  }

  public void checkout(Role role, String id, long today) {
    staff(role);
    Loan l = loan(id);
    require(l.status == Status.RESERVED, "Phiếu chưa sẵn sàng để bàn giao.");
    require(l.pickupDay == today, "Chỉ bàn giao vào ngày hẹn nhận sách.");
    l.status = Status.BORROWED;
    l.dueDay = today + loanDays;
    l.finePerDay = dailyFine;
  }

  public void renew(Role role, String reader, String id, long today) {
    Loan l = loan(id);
    require(role == Role.READER && l.readerId.equals(reader), "Bạn không được gia hạn phiếu này.");
    require(l.status == Status.BORROWED, "Phiếu chưa ở trạng thái đang mượn.");
    require(today <= l.dueDay, "Sách quá hạn không được gia hạn.");
    require(l.renewals < maxRenewals, "Đã hết số lần gia hạn.");
    for (Loan other : loans)
      require(
          !(other.bookId.equals(l.bookId) && other.status == Status.WAITING),
          "Có độc giả đang chờ sách này.");
    l.dueDay += renewalDays;
    l.renewals++;
  }

  public void returnBook(Role role, String id, long today) {
    staff(role);
    Loan l = loan(id);
    require(l.status == Status.BORROWED, "Chỉ nhận trả phiếu đang mượn.");
    l.returnDay = today;
    l.status = Status.RETURNED;
    promote(l.bookId, today);
  }

  public long fine(Loan l, long today) {
    if (l.status != Status.BORROWED && l.status != Status.RETURNED) return 0;
    return Math.max(0, (l.status == Status.RETURNED ? l.returnDay : today) - l.dueDay)
        * l.finePerDay;
  }

  public void expire(long today) {
    for (Loan l : loans)
      if ((l.status == Status.RESERVED || l.status == Status.WAITING) && l.pickupDay < today)
        l.status = Status.EXPIRED;
    for (Book b : books) promote(b.id, today);
  }

  private void promote(String bookId, long today) {
    for (Loan l : loans)
      if (l.bookId.equals(bookId)
          && l.status == Status.WAITING
          && l.pickupDay >= today
          && available(bookId) > 0) l.status = Status.RESERVED;
  }

  public void saveBook(Role role, Book b, long today) {
    staff(role);
    require(
        b.title != null && !b.title.trim().isEmpty() && b.title.length() <= 150,
        "Tên sách cần từ 1 đến 150 ký tự.");
    require(
        b.author != null && !b.author.trim().isEmpty() && b.author.length() <= 100,
        "Tác giả cần từ 1 đến 100 ký tự.");
    require(
        b.shelf != null && !b.shelf.trim().isEmpty() && b.shelf.length() <= 50,
        "Vui lòng nhập vị trí kệ (tối đa 50 ký tự).");
    require(
        b.category != null && !b.category.trim().isEmpty() && b.category.length() <= 50,
        "Vui lòng nhập thể loại (tối đa 50 ký tự).");
    require(b.description != null && b.description.length() <= 2000, "Mô tả tối đa 2.000 ký tự.");
    require(validIsbn(b.isbn), "ISBN-13 không hợp lệ (cần đúng cả số kiểm tra).");
    require(b.copies >= 1 && b.copies <= 999, "Số bản phải từ 1 đến 999.");
    for (Book other : books)
      require(
          other.id.equals(b.id) || !other.isbn.equals(b.isbn), "ISBN đã tồn tại trong danh mục.");
    int index = -1;
    for (int i = 0; i < books.size(); i++) if (books.get(i).id.equals(b.id)) index = i;
    if (index >= 0) {
      int used = books.get(index).copies - available(b.id);
      require(b.copies >= used, "Số bản không được ít hơn số đang giữ/mượn.");
      books.set(index, b);
    } else books.add(b);
    promote(b.id, today);
  }

  public void archive(Role role, String id) {
    staff(role);
    for (Loan l : loans)
      require(!(l.bookId.equals(id) && l.active()), "Không thể ẩn sách còn phiếu đang xử lý.");
    book(id).archived = true;
  }

  public void rules(Role role, int max, int days, int renewal, int count, long fine) {
    require(role == Role.ADMIN, "Chỉ quản trị được sửa quy định.");
    require(
        max >= 1
            && max <= 20
            && days >= 1
            && days <= 90
            && renewal >= 1
            && renewal <= 30
            && count >= 0
            && count <= 5
            && fine >= 0
            && fine <= 100000,
        "Quy định vượt giới hạn cho phép.");
    maxLoans = max;
    loanDays = days;
    renewalDays = renewal;
    maxRenewals = count;
    dailyFine = fine;
  }

  public static boolean validIsbn(String s) {
    if (s == null || !s.matches("97[89][0-9]{10}")) return false;
    int sum = 0;
    for (int i = 0; i < 13; i++) sum += (s.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
    return sum % 10 == 0;
  }

  private static void staff(Role role) {
    require(
        role == Role.LIBRARIAN || role == Role.ADMIN, "Chỉ thủ thư hoặc quản trị được thao tác.");
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new IllegalArgumentException(message);
  }

  public static Library demo() {
    Library s = new Library();
    s.books.add(
        new Book(
            "1",
            "Nhà giả kim",
            "Paulo Coelho",
            "Văn học",
            "9780061122415",
            "A1 · Tầng 1",
            "Hành trình theo đuổi giấc mơ của chàng chăn cừu Santiago. Một câu chuyện về lòng can"
                + " đảm và việc lắng nghe chính mình.",
            3,
            0xFF20695C));
    s.books.add(
        new Book(
            "2",
            "Hoàng tử bé",
            "Antoine de Saint-Exupéry",
            "Văn học",
            "9780156012195",
            "A2 · Tầng 1",
            "Một chuyến du hành qua các hành tinh, tình bạn và những điều chỉ có thể nhìn thấy bằng"
                + " trái tim.",
            2,
            0xFFAB613B));
    s.books.add(
        new Book(
            "3",
            "Atomic Habits",
            "James Clear",
            "Kỹ năng",
            "9780735211292",
            "B1 · Tầng 2",
            "Khám phá cách những thay đổi nhỏ, được duy trì mỗi ngày, tạo nên kết quả bền vững.",
            2,
            0xFF6C658C));
    s.books.add(
        new Book(
            "4",
            "Clean Code",
            "Robert C. Martin",
            "Công nghệ",
            "9780132350884",
            "C3 · Tầng 2",
            "Các nguyên tắc viết mã dễ đọc, dễ kiểm thử và dễ bảo trì dành cho người phát triển"
                + " phần mềm.",
            1,
            0xFF355E77));
    s.books.add(
        new Book(
            "5",
            "Sapiens",
            "Yuval Noah Harari",
            "Khoa học",
            "9780062316097",
            "D2 · Tầng 3",
            "Một góc nhìn tổng quan về lịch sử loài người, xã hội và những ý tưởng định hình thế"
                + " giới.",
            2,
            0xFF95604D));
    s.books.add(
        new Book(
            "6",
            "The Pragmatic Programmer",
            "David Thomas · Andrew Hunt",
            "Công nghệ",
            "9780135957059",
            "C1 · Tầng 2",
            "Tư duy và thói quen thực hành để xây dựng phần mềm đáng tin cậy.",
            2,
            0xFF796B3C));
    return s;
  }
}

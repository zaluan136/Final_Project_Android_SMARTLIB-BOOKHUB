package com.example.smartlibandbookhub.data;

import android.content.Context;
import android.util.AtomicFile;
import com.example.smartlibandbookhub.domain.Library;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Versioned, atomic local persistence. Never replaces an unreadable file with seed data. */
public final class LibraryStore {
  private static final Object LOCK = new Object();
  private final AtomicFile file;

  public interface Change {
    void apply(Library library);
  }

  public LibraryStore(Context context) {
    file = new AtomicFile(new File(context.getFilesDir(), "bookhub-v1.json"));
  }

  public Library read(long today) throws Exception {
    synchronized (LOCK) {
      Library s = load();
      String before = encode(s).toString();
      s.expire(today);
      if (!before.equals(encode(s).toString())) save(s);
      return s;
    }
  }

  public Library change(long today, Change change) throws Exception {
    synchronized (LOCK) {
      Library s = load();
      s.expire(today);
      change.apply(s);
      save(s);
      return s;
    }
  }

  private Library load() throws Exception {
    if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists())
      return Library.demo();
    JSONObject root = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
    if (root.getInt("version") != 1)
      throw new IllegalStateException("Phiên bản dữ liệu chưa được hỗ trợ.");
    Library s = new Library();
    JSONObject r = root.getJSONObject("rules");
    s.rules(
        Library.Role.ADMIN,
        r.getInt("max"),
        r.getInt("days"),
        r.getInt("renewal"),
        r.getInt("count"),
        r.getLong("fine"));
    JSONArray books = root.getJSONArray("books");
    for (int i = 0; i < books.length(); i++) {
      JSONObject b = books.getJSONObject(i);
      Library.Book item =
          new Library.Book(
              b.getString("id"),
              b.getString("title"),
              b.getString("author"),
              b.getString("category"),
              b.getString("isbn"),
              b.getString("shelf"),
              b.getString("description"),
              b.getInt("copies"),
              b.getInt("color"));
      item.archived = b.getBoolean("archived");
      s.books.add(item);
    }
    JSONArray loans = root.getJSONArray("loans");
    for (int i = 0; i < loans.length(); i++) {
      JSONObject j = loans.getJSONObject(i);
      Library.Loan l = new Library.Loan();
      l.id = j.getString("id");
      l.bookId = j.getString("book");
      s.book(l.bookId);
      l.readerId = j.getString("reader");
      l.status = Library.Status.valueOf(j.getString("status"));
      l.pickupDay = j.getLong("pickup");
      l.dueDay = j.getLong("due");
      l.returnDay = j.getLong("returned");
      l.finePerDay = j.getLong("fine");
      l.renewals = j.getInt("renewals");
      s.loans.add(l);
    }
    for (Library.Book b : s.books)
      if (s.available(b.id) < 0) throw new IllegalStateException("Dữ liệu tồn kho không hợp lệ.");
    return s;
  }

  private JSONObject encode(Library s) throws Exception {
    JSONObject r =
        new JSONObject()
            .put("max", s.maxLoans)
            .put("days", s.loanDays)
            .put("renewal", s.renewalDays)
            .put("count", s.maxRenewals)
            .put("fine", s.dailyFine);
    JSONArray books = new JSONArray(), loans = new JSONArray();
    for (Library.Book b : s.books)
      books.put(
          new JSONObject()
              .put("id", b.id)
              .put("title", b.title)
              .put("author", b.author)
              .put("category", b.category)
              .put("isbn", b.isbn)
              .put("shelf", b.shelf)
              .put("description", b.description)
              .put("copies", b.copies)
              .put("color", b.color)
              .put("archived", b.archived));
    for (Library.Loan l : s.loans)
      loans.put(
          new JSONObject()
              .put("id", l.id)
              .put("book", l.bookId)
              .put("reader", l.readerId)
              .put("status", l.status.name())
              .put("pickup", l.pickupDay)
              .put("due", l.dueDay)
              .put("returned", l.returnDay)
              .put("fine", l.finePerDay)
              .put("renewals", l.renewals));
    return new JSONObject()
        .put("version", 1)
        .put("rules", r)
        .put("books", books)
        .put("loans", loans);
  }

  private void save(Library s) throws Exception {
    byte[] bytes = encode(s).toString().getBytes(StandardCharsets.UTF_8);
    FileOutputStream out = null;
    try {
      out = file.startWrite();
      out.write(bytes);
      file.finishWrite(out);
    } catch (Exception e) {
      if (out != null) file.failWrite(out);
      throw e;
    }
  }
}

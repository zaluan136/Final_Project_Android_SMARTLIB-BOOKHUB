package com.example.smartlibandbookhub;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class LibraryFormat {
  private LibraryFormat() {}

  static String money(long amount) {
    return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(amount) + " đ";
  }

  static long today() {
    Calendar c = Calendar.getInstance();
    return epochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
  }

  static long epochDay(int y, int m, int d) {
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    c.clear();
    c.set(y, m, d);
    return c.getTimeInMillis() / 86400000L;
  }

  static String date(long day) {
    SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));
    f.setTimeZone(TimeZone.getTimeZone("UTC"));
    return f.format(new Date(day * 86400000L));
  }
}

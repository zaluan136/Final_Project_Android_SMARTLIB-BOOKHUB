package com.example.smartlibandbookhub;

import static org.junit.Assert.*;

import com.example.smartlibandbookhub.domain.Library;
import com.example.smartlibandbookhub.domain.Library.*;
import org.junit.Before;
import org.junit.Test;

public class LibraryTest {
  private Library s;
  private static final long DAY = 20000;

  @Before
  public void setup() {
    s = Library.demo();
  }

  private Loan reserve(String reader, String book) {
    return s.reserve(Role.READER, reader, book, DAY, DAY);
  }

  private void reject(Runnable action) {
    assertThrows(IllegalArgumentException.class, action::run);
  }

  @Test
  public void completeLifecyclePreservesInventory() {
    Loan l = reserve("a", "4");
    assertEquals(0, s.available("4"));
    s.checkout(Role.LIBRARIAN, l.id, DAY);
    assertEquals(DAY + 14, l.dueDay);
    s.renew(Role.READER, "a", l.id, DAY + 5);
    assertEquals(DAY + 21, l.dueDay);
    s.returnBook(Role.LIBRARIAN, l.id, DAY + 23);
    assertEquals(6000, s.fine(l, DAY + 40));
    assertEquals(1, s.available("4"));
  }

  @Test
  public void duplicateReservationDoesNotConsumeInventory() {
    reserve("a", "1");
    reject(() -> reserve("a", "1"));
    assertEquals(2, s.available("1"));
    assertEquals(1, s.loans.size());
  }

  @Test
  public void queueIsFifoAndBlocksRenewal() {
    Loan a = reserve("a", "4"), b = reserve("b", "4"), c = reserve("c", "4");
    assertEquals(Status.WAITING, b.status);
    s.checkout(Role.ADMIN, a.id, DAY);
    reject(() -> s.renew(Role.READER, "a", a.id, DAY));
    s.returnBook(Role.ADMIN, a.id, DAY);
    assertEquals(Status.RESERVED, b.status);
    assertEquals(Status.WAITING, c.status);
    assertEquals(0, s.available("4"));
  }

  @Test
  public void cancellationPromotesWaitingReader() {
    Loan a = reserve("a", "4"), b = reserve("b", "4");
    s.cancel(Role.READER, "a", a.id, DAY);
    assertEquals(Status.RESERVED, b.status);
    reject(() -> s.cancel(Role.READER, "a", a.id, DAY));
  }

  @Test
  public void expiredPickupReleasesStock() {
    Loan a = reserve("a", "4");
    Loan b = s.reserve(Role.READER, "b", "4", DAY + 1, DAY);
    s.expire(DAY + 1);
    assertEquals(Status.EXPIRED, a.status);
    assertEquals(Status.RESERVED, b.status);
    assertEquals(0, s.available("4"));
  }

  @Test
  public void maxActiveIncludesWaitingRequests() {
    reserve("a", "1");
    reserve("a", "2");
    reserve("a", "3");
    reject(() -> reserve("a", "4"));
    assertEquals(3, s.loans.size());
  }

  @Test
  public void validatesPickupWindow() {
    reject(() -> s.reserve(Role.READER, "a", "1", DAY - 1, DAY));
    reject(() -> s.reserve(Role.READER, "a", "1", DAY + 8, DAY));
    s.reserve(Role.READER, "a", "1", DAY + 7, DAY);
    assertEquals(1, s.loans.size());
  }

  @Test
  public void roleChecksProtectEveryStaffMutation() {
    Loan l = reserve("a", "1");
    reject(() -> s.checkout(Role.READER, l.id, DAY));
    reject(() -> s.returnBook(Role.READER, l.id, DAY));
    reject(() -> s.rules(Role.LIBRARIAN, 3, 14, 7, 1, 1000));
    reject(() -> s.archive(Role.READER, "2"));
    reject(() -> s.saveBook(Role.READER, s.book("2"), DAY));
    reject(() -> s.reserve(Role.ADMIN, "a", "2", DAY, DAY));
    assertEquals(Status.RESERVED, l.status);
  }

  @Test
  public void ownersCannotChangeAnotherReadersLoan() {
    Loan l = reserve("a", "1");
    reject(() -> s.cancel(Role.READER, "b", l.id, DAY));
    s.checkout(Role.ADMIN, l.id, DAY);
    reject(() -> s.renew(Role.READER, "b", l.id, DAY));
    assertEquals(0, l.renewals);
  }

  @Test
  public void doubleCheckoutOrReturnIsRejected() {
    Loan l = reserve("a", "1");
    s.checkout(Role.ADMIN, l.id, DAY);
    reject(() -> s.checkout(Role.ADMIN, l.id, DAY));
    s.returnBook(Role.ADMIN, l.id, DAY);
    reject(() -> s.returnBook(Role.ADMIN, l.id, DAY));
    assertEquals(3, s.available("1"));
  }

  @Test
  public void dueDayIsInclusiveAndOverdueBlocksMoreLoans() {
    Loan l = reserve("a", "1");
    s.checkout(Role.ADMIN, l.id, DAY);
    assertEquals(0, s.fine(l, DAY + 14));
    assertEquals(3000, s.fine(l, DAY + 15));
    reject(() -> s.renew(Role.READER, "a", l.id, DAY + 15));
    reject(() -> s.reserve(Role.READER, "a", "2", DAY + 15, DAY + 15));
  }

  @Test
  public void renewalLimitIsEnforced() {
    Loan l = reserve("a", "1");
    s.checkout(Role.ADMIN, l.id, DAY);
    s.renew(Role.READER, "a", l.id, DAY);
    reject(() -> s.renew(Role.READER, "a", l.id, DAY));
    assertEquals(DAY + 21, l.dueDay);
  }

  @Test
  public void ruleChangesDoNotRewriteExistingDueDateOrFee() {
    Loan l = reserve("a", "1");
    s.checkout(Role.ADMIN, l.id, DAY);
    s.rules(Role.ADMIN, 4, 30, 10, 2, 9999);
    assertEquals(DAY + 14, l.dueDay);
    assertEquals(3000, s.fine(l, DAY + 15));
  }

  @Test
  public void cannotCheckoutOutsidePickupDay() {
    Loan l = s.reserve(Role.READER, "a", "1", DAY + 1, DAY);
    reject(() -> s.checkout(Role.ADMIN, l.id, DAY));
    reject(() -> s.checkout(Role.ADMIN, l.id, DAY + 2));
    assertEquals(Status.RESERVED, l.status);
  }

  @Test
  public void archiveRejectsActiveLoansAndPreservesHistory() {
    Loan l = reserve("a", "1");
    reject(() -> s.archive(Role.ADMIN, "1"));
    s.cancel(Role.READER, "a", l.id, DAY);
    s.archive(Role.ADMIN, "1");
    assertTrue(s.search("Nhà giả kim", "Tất cả").isEmpty());
    assertNotNull(s.book(l.bookId));
    reject(() -> reserve("b", "1"));
  }

  @Test
  public void searchIgnoresVietnameseAccentsAndSupportsIsbn() {
    assertEquals("1", s.search("nha gia kim", "Tất cả").get(0).id);
    assertEquals("2", s.search("9780156012195", "Tất cả").get(0).id);
    assertEquals(2, s.search("", "Công nghệ").size());
  }

  @Test
  public void bookValidationChecksIsbnAndAllocatedCopies() {
    Book old = s.book("1");
    reserve("a", "1");
    reserve("b", "1");
    Book edit =
        new Book(
            old.id,
            old.title,
            old.author,
            old.category,
            old.isbn,
            old.shelf,
            old.description,
            1,
            old.color);
    reject(() -> s.saveBook(Role.ADMIN, edit, DAY));
    assertEquals(3, s.book("1").copies);
    edit.copies = 4;
    s.saveBook(Role.ADMIN, edit, DAY);
    assertEquals(2, s.available("1"));
    edit.isbn = "9780061122416";
    reject(() -> s.saveBook(Role.ADMIN, edit, DAY));
  }

  @Test
  public void stockIncreasePromotesWaitingRequests() {
    Loan a = reserve("a", "4"), b = reserve("b", "4");
    Book old = s.book("4");
    Book edit =
        new Book(
            old.id,
            old.title,
            old.author,
            old.category,
            old.isbn,
            old.shelf,
            old.description,
            2,
            old.color);
    s.saveBook(Role.ADMIN, edit, DAY);
    assertEquals(Status.RESERVED, b.status);
    assertEquals(0, s.available("4"));
  }

  @Test
  public void seedIsbnsHaveValidCheckDigits() {
    for (Book b : s.books) assertTrue(b.title, Library.validIsbn(b.isbn));
  }

  @Test
  public void invalidRulesLeaveCurrentRulesUntouched() {
    reject(() -> s.rules(Role.ADMIN, 0, 14, 7, 1, 3000));
    reject(() -> s.rules(Role.ADMIN, 3, 14, 7, 1, -1));
    assertEquals(3, s.maxLoans);
    assertEquals(3000, s.dailyFine);
  }
}

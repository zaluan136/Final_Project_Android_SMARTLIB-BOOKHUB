package com.example.smartlibandbookhub.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import com.google.firebase.firestore.Source;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import java.util.Map;

public final class FirebaseRepository implements FirebaseDataSource {
  private final FirebaseAuth auth = FirebaseAuth.getInstance();
  private final FirebaseFirestore database = FirebaseFirestore.getInstance();

  public OnlineSession user() {
    FirebaseUser user = auth.getCurrentUser();
    return user == null ? null : new OnlineSession(user.getUid(), user.isEmailVerified());
  }
  public void signOut() { auth.signOut(); }
  public Task<Void> signIn(String email, String password) {
    return asVoid(auth.signInWithEmailAndPassword(email, password));
  }
  public Task<Void> register(String email, String password) {
    return asVoid(auth.createUserWithEmailAndPassword(email, password));
  }
  public Task<Void> resetPassword(String email) { return auth.sendPasswordResetEmail(email); }
  public Task<Void> sendVerification() { return auth.getCurrentUser().sendEmailVerification(); }
  public Task<Void> refreshUser() {
    FirebaseUser current = auth.getCurrentUser();
    return asVoid(current.reload().continueWithTask(task -> {
      checkTask(task);
      return current.getIdToken(true);
    }));
  }
  public Task<String> token() {
    return auth.getCurrentUser().getIdToken(true).continueWith(task -> {
      checkTask(task);
      Object role = task.getResult().getClaims().get("role");
      return role instanceof String ? (String) role : "reader";
    });
  }

  private static void checkTask(Task<?> task) throws Exception {
    if (task.isCanceled()) throw new java.util.concurrent.CancellationException();
    if (!task.isSuccessful()) {
      Exception error = task.getException();
      if (error != null) throw error;
      throw new IllegalStateException("Firebase task failed");
    }
  }

  private Task<Void> asVoid(Task<?> source) {
    return source.continueWith(task -> {
      checkTask(task);
      return null;
    });
  }
  public Task<List<OnlineBook>> books() {
    return database.collection("books").whereEqualTo("archived", false).limit(100)
        .get(Source.SERVER).continueWith(task -> {
          checkTask(task);
          List<OnlineBook> books = new ArrayList<>();
          for (DocumentSnapshot book : task.getResult().getDocuments()) {
            books.add(new OnlineBook(book.getId(), book.getString("title"),
                book.getString("author"), book.getString("shelf"), book.getLong("available")));
          }
          return books;
        });
  }
  public Task<List<OnlineLoan>> loans(boolean staff, String userId) {
    Query query = staff ? database.collection("loans")
        : database.collection("loans").whereEqualTo("readerId", userId);
    return query.limit(100).get(Source.SERVER).continueWith(task -> {
      checkTask(task);
      List<OnlineLoan> loans = new ArrayList<>();
      for (DocumentSnapshot loan : task.getResult().getDocuments()) {
        loans.add(new OnlineLoan(loan.getId(), loan.getString("title"),
            loan.getString("status"), loan.getLong("dueDay")));
      }
      return loans;
    });
  }
  public Task<Void> command(Map<String, Object> data) {
    return FirebaseFunctions.getInstance("asia-southeast1").getHttpsCallable("bookhub").call(data)
        .continueWith(task -> {
          if (!task.isSuccessful()) {
            Exception cause = task.getException();
            boolean precondition = cause instanceof FirebaseFunctionsException
                && ((FirebaseFunctionsException) cause).getCode()
                    == FirebaseFunctionsException.Code.FAILED_PRECONDITION;
            throw new OnlineCommandException(cause == null ? null : cause.getMessage(), precondition, cause);
          }
          return null;
        });
  }
}

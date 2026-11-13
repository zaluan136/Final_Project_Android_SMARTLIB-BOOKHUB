package com.example.smartlibandbookhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.smartlibandbookhub.data.FirebaseDataSource;
import com.example.smartlibandbookhub.data.OnlineBook;
import com.example.smartlibandbookhub.data.OnlineLoan;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.example.smartlibandbookhub.data.OnlineSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FirebaseViewModelTest {
  private static final class FakeRepository implements FirebaseDataSource {
    final List<Map<String, Object>> requests = new ArrayList<>();
    TaskCompletionSource<Void> completion;
    public OnlineSession user() { return null; }
    public void signOut() {}
    public Task<Void> signIn(String email, String password) { return Tasks.forResult(null); }
    public Task<Void> register(String email, String password) { return Tasks.forResult(null); }
    public Task<Void> resetPassword(String email) { return Tasks.forResult(null); }
    public Task<Void> sendVerification() { return Tasks.forResult(null); }
    public Task<Void> refreshUser() { return Tasks.forResult(null); }
    public Task<String> token() { return Tasks.forResult(null); }
    public Task<List<OnlineBook>> books() { return Tasks.forResult(new ArrayList<>()); }
    public Task<List<OnlineLoan>> loans(boolean staff, String userId) {
      return Tasks.forResult(new ArrayList<>());
    }
    public Task<Void> command(Map<String, Object> data) {
      requests.add(new HashMap<>(data));
      completion = new TaskCompletionSource<>();
      return completion.getTask();
    }
  }

  @Test
  public void retryUsesSameIdAndSignOutInvalidatesPendingCommand() {
    FakeRepository repository = new FakeRepository();
    final FirebaseViewModel[] holder = new FirebaseViewModel[1];
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      holder[0] = new FirebaseViewModel(repository);
      holder[0].command("reserve", new HashMap<>());
      repository.completion.setException(new IllegalStateException("offline"));
    });
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      FirebaseViewModel.Event event = holder[0].getEvent().getValue();
      assertNotNull(event);
      assertNotNull(event.retry);
      Object requestId = event.retry.get("requestId");
      holder[0].consumeEvent();
      holder[0].send(event.retry);
      assertEquals(requestId, repository.requests.get(1).get("requestId"));
      holder[0].signOut();
      repository.completion.setException(new IllegalStateException("late"));
    });
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      assertNull(holder[0].getEvent().getValue());
      assertEquals("login", holder[0].getState().getValue().screen);
    });
  }
}

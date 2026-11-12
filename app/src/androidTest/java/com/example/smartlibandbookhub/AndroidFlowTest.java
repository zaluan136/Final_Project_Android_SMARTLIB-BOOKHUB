package com.example.smartlibandbookhub;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import com.example.smartlibandbookhub.data.LibraryStore;
import com.example.smartlibandbookhub.domain.Library;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 26)
public class AndroidFlowTest {
  private Context context;

  @Before
  public void clear() {
    context = ApplicationProvider.getApplicationContext();
    for (String suffix : new String[] {"", ".bak", ".new"})
      new File(context.getFilesDir(), "bookhub-v1.json" + suffix).delete();
  }

  private void ready(ActivityScenario<MainActivity> scenario) throws Exception {
    java.util.concurrent.atomic.AtomicBoolean ready =
        new java.util.concurrent.atomic.AtomicBoolean();
    long until = System.currentTimeMillis() + 20000;
    while (!ready.get() && System.currentTimeMillis() < until) {
      scenario.onActivity(
          a -> ready.set(a.findViewById(R.id.loading).getVisibility() == View.GONE));
      Thread.sleep(50);
    }
    assertTrue("Activity did not finish loading", ready.get());
  }

  private boolean contains(View v, String text) {
    if (v instanceof TextView && ((TextView) v).getText().toString().contains(text)) return true;
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++)
        if (contains(group.getChildAt(i), text)) return true;
    }
    return false;
  }

  @Test
  public void activityInflatesAndNavigationShowsReaderGuard() throws Exception {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      ready(scenario);
      scenario.onActivity(
          a -> {
            assertTrue(contains(a.findViewById(R.id.content), "Nhà giả kim"));
            BottomNavigationView nav = a.findViewById(R.id.navigation);
            nav.setSelectedItemId(R.id.nav_loans);
            assertTrue(contains(a.findViewById(R.id.content), "Một hành trình mới"));
            nav.setSelectedItemId(R.id.nav_manage);
            assertTrue(contains(a.findViewById(R.id.content), "không phải cơ chế đăng nhập"));
            nav.setSelectedItemId(R.id.nav_profile);
            assertTrue(contains(a.findViewById(R.id.content), "Quy định mượn sách"));
          });
    }
  }

  @Test
  public void persistedLoanSurvivesRepositoryRecreationAndFailedMutation() throws Exception {
    LibraryStore store = new LibraryStore(context);
    Library s =
        store.change(20000, l -> l.reserve(Library.Role.READER, "reader-demo", "4", 20000, 20000));
    String id = s.loans.get(0).id;
    LibraryStore reopened = new LibraryStore(context);
    assertEquals(id, reopened.read(20000).loans.get(0).id);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            reopened.change(
                20000, l -> l.reserve(Library.Role.READER, "reader-demo", "4", 20000, 20000)));
    assertEquals(1, reopened.read(20000).loans.size());
    assertEquals(0, reopened.read(20000).available("4"));
  }

  @Test
  public void unreadableFileIsNotSilentlyReset() throws Exception {
    File file = new File(context.getFilesDir(), "bookhub-v1.json");
    Files.write(file.toPath(), "broken data".getBytes(StandardCharsets.UTF_8));
    assertThrows(Exception.class, () -> new LibraryStore(context).read(20000));
    assertEquals(
        "broken data", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
  }

  @Test
  public void reopenedExpiredReservationsReleaseInventory() throws Exception {
    LibraryStore store = new LibraryStore(context);
    store.change(20000, l -> l.reserve(Library.Role.READER, "reader-demo", "4", 20000, 20000));
    Library s = new LibraryStore(context).read(20001);
    assertEquals(Library.Status.EXPIRED, s.loans.get(0).status);
    assertEquals(1, s.available("4"));
    assertEquals(Library.Status.EXPIRED, new LibraryStore(context).read(20001).loans.get(0).status);
  }
}

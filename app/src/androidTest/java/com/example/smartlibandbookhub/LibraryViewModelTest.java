package com.example.smartlibandbookhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.smartlibandbookhub.data.LibraryDataSource;
import com.example.smartlibandbookhub.data.LibraryStore;
import com.example.smartlibandbookhub.domain.Library;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LibraryViewModelTest {
  @Test
  public void loadRejectsDuplicateAndReportsFailure() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    CountDownLatch completed = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    LibraryDataSource repository = new LibraryDataSource() {
      public Library read(long today) throws Exception {
        calls.incrementAndGet();
        started.countDown();
        if (!finish.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
        throw new IllegalStateException("Read failed");
      }
      public Library change(long today, LibraryStore.Change change) {
        throw new AssertionError("Unexpected write");
      }
    };
    AtomicReference<LibraryViewModel> reference = new AtomicReference<>();
    Application app = ApplicationProvider.getApplicationContext();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      LibraryViewModel model = new LibraryViewModel(app, new SavedStateHandle(), repository);
      reference.set(model);
      model.getResult().observeForever(result -> {
        if (result != null) completed.countDown();
      });
      model.setQuery("Android");
      model.load(20000);
      model.load(20000);
      assertTrue(model.getState().getValue().loading);
    });
    assertTrue(started.await(10, TimeUnit.SECONDS));
    finish.countDown();
    assertTrue(completed.await(10, TimeUnit.SECONDS));
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      LibraryViewModel.UiState state = reference.get().getState().getValue();
      assertFalse(state.loading);
      assertEquals("Android", state.query);
      assertNotNull(state.error);
      assertEquals("Read failed", state.error.getMessage());
    });
    assertEquals(1, calls.get());
  }

  @Test
  public void selectedValuesRestoreFromSavedState() {
    Application app = ApplicationProvider.getApplicationContext();
    SavedStateHandle saved = new SavedStateHandle();
    LibraryDataSource repository = new LibraryDataSource() {
      public Library read(long today) { return Library.demo(); }
      public Library change(long today, LibraryStore.Change change) {
        Library library = Library.demo();
        change.apply(library);
        return library;
      }
    };
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      LibraryViewModel first = new LibraryViewModel(app, saved, repository);
      first.setRole(Library.Role.ADMIN);
      first.setTab(R.id.nav_profile);
      first.setQuery("Java");
      first.setCategory("Công nghệ");
      LibraryViewModel second = new LibraryViewModel(app, saved, repository);
      LibraryViewModel.UiState state = second.getState().getValue();
      assertEquals(Library.Role.ADMIN, state.role);
      assertEquals(R.id.nav_profile, state.tab);
      assertEquals("Java", state.query);
      assertEquals("Công nghệ", state.category);
    });
  }
}

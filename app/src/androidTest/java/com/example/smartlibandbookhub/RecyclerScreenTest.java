package com.example.smartlibandbookhub;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.material.navigation.NavigationBarView;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RecyclerScreenTest {
  @Test
  public void searchShowsEmptyAndNavigationStillWorks() throws Exception {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      AtomicBoolean ready = new AtomicBoolean();
      long deadline = System.currentTimeMillis() + 20000;
      while (!ready.get() && System.currentTimeMillis() < deadline) {
        scenario.onActivity(activity ->
            ready.set(activity.findViewById(R.id.loading).getVisibility() == View.GONE));
        Thread.sleep(50);
      }
      assertTrue("Library did not finish loading", ready.get());
      onView(withHint(R.string.search_hint)).perform(replaceText("zzzz-no-matching-book"));
      closeSoftKeyboard();
      onView(withText("Không tìm thấy sách")).check(matches(isDisplayed()));
      scenario.onActivity(activity -> {
        NavigationBarView navigation = activity.findViewById(R.id.navigation);
        navigation.setSelectedItemId(R.id.nav_loans);
      });
      onView(allOf(withText("Phiếu mượn"), isDescendantOfA(withId(R.id.content))))
          .check(matches(isDisplayed()));
    }
  }
}

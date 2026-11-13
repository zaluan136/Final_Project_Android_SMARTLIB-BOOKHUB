package com.example.smartlibandbookhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.material.navigation.NavigationBarView;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StateRestorationTest {
  @Test
  public void selectedTabSurvivesRecreation() throws Exception {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      AtomicBoolean ready = new AtomicBoolean();
      long deadline = System.currentTimeMillis() + 20000;
      while (!ready.get() && System.currentTimeMillis() < deadline) {
        scenario.onActivity(activity ->
            ready.set(activity.findViewById(R.id.loading).getVisibility() == View.GONE));
        Thread.sleep(50);
      }
      assertTrue("Library did not finish loading", ready.get());
      scenario.onActivity(activity -> {
        NavigationBarView navigation = activity.findViewById(R.id.navigation);
        navigation.setSelectedItemId(R.id.nav_profile);
      });
      scenario.recreate();
      scenario.onActivity(activity -> {
        NavigationBarView navigation = activity.findViewById(R.id.navigation);
        assertEquals(R.id.nav_profile, navigation.getSelectedItemId());
      });
    }
  }
}

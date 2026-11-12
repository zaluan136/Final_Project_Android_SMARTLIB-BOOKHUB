package com.example.smartlibandbookhub;

import android.app.Application;
import com.google.firebase.FirebaseApp;

public final class BookHubApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    if (!FirebaseApp.getApps(this).isEmpty()) FirebaseSecurity.install();
  }
}

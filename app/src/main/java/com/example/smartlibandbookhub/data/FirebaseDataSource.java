package com.example.smartlibandbookhub.data;

import com.google.android.gms.tasks.Task;
import java.util.List;
import java.util.Map;

public interface FirebaseDataSource {
  OnlineSession user();
  void signOut();
  Task<Void> signIn(String email, String password);
  Task<Void> register(String email, String password);
  Task<Void> resetPassword(String email);
  Task<Void> sendVerification();
  Task<Void> refreshUser();
  Task<String> token();
  Task<List<OnlineBook>> books();
  Task<List<OnlineLoan>> loans(boolean staff, String userId);
  Task<Void> command(Map<String, Object> data);
}

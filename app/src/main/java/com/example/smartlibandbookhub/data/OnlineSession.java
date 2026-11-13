package com.example.smartlibandbookhub.data;

public final class OnlineSession {
  public final String userId;
  public final boolean emailVerified;

  public OnlineSession(String userId, boolean emailVerified) {
    this.userId = userId;
    this.emailVerified = emailVerified;
  }
}

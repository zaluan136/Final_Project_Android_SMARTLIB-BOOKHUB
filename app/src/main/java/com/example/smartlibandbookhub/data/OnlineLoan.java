package com.example.smartlibandbookhub.data;

public final class OnlineLoan {
  public final String id;
  public final String title;
  public final String status;
  public final Long dueDay;

  public OnlineLoan(String id, String title, String status, Long dueDay) {
    this.id = id;
    this.title = title;
    this.status = status;
    this.dueDay = dueDay;
  }
}

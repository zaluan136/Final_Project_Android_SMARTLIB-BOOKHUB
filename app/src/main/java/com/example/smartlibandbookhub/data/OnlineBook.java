package com.example.smartlibandbookhub.data;

public final class OnlineBook {
  public final String id;
  public final String title;
  public final String author;
  public final String shelf;
  public final Long available;

  public OnlineBook(String id, String title, String author, String shelf, Long available) {
    this.id = id;
    this.title = title;
    this.author = author;
    this.shelf = shelf;
    this.available = available;
  }
}

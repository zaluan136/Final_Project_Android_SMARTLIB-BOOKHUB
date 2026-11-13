package com.example.smartlibandbookhub.data;

public final class OnlineCommandException extends Exception {
  public final boolean preconditionFailed;

  public OnlineCommandException(String message, boolean preconditionFailed, Throwable cause) {
    super(message, cause);
    this.preconditionFailed = preconditionFailed;
  }
}

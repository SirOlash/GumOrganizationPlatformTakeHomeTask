package com.Semicolon.exceptions.api;

public class EmailInUseException extends RuntimeException {
  public EmailInUseException(String message) {
    super(message);
  }
}

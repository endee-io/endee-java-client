package io.endee.client.exception;

/** Exception thrown when the API returns HTTP 409 — resource already exists. */
public class ConflictException extends EndeeApiException {

  public ConflictException(String message, int statusCode, String errorBody) {
    super(message, statusCode, errorBody);
  }
}

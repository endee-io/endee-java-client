package io.endee.client.exception;

/** Exception thrown when the API returns HTTP 404 — index or vector not found. */
public class NotFoundException extends EndeeApiException {

  public NotFoundException(String message, int statusCode, String errorBody) {
    super(message, statusCode, errorBody);
  }
}

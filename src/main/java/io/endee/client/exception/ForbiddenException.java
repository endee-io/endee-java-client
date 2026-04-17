package io.endee.client.exception;

/** Exception thrown when the API returns HTTP 403 — insufficient permissions. */
public class ForbiddenException extends EndeeApiException {

  public ForbiddenException(String message, int statusCode, String errorBody) {
    super(message, statusCode, errorBody);
  }
}

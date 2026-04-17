package io.endee.client.exception;

/** Exception thrown when the API returns HTTP 401 — invalid or expired token. */
public class AuthenticationException extends EndeeApiException {

  public AuthenticationException(String message, int statusCode, String errorBody) {
    super(message, statusCode, errorBody);
  }
}

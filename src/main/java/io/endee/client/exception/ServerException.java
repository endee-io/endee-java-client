package io.endee.client.exception;

/** Exception thrown when the API returns HTTP 5xx — internal server or service error. */
public class ServerException extends EndeeApiException {

  public ServerException(String message, int statusCode, String errorBody) {
    super(message, statusCode, errorBody);
  }
}

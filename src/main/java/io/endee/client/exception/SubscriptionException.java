package io.endee.client.exception;

/** Exception thrown when the API returns HTTP 402 — quota exceeded or tier limit reached. */
public class SubscriptionException extends EndeeApiException {

  public SubscriptionException(String message, int statusCode, String errorBody) {
    super(message, statusCode, errorBody);
  }
}

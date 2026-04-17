package io.endee.client.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Exception thrown when the Endee API returns an error response (HTTP 400 / catch-all). */
public class EndeeApiException extends EndeeException {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final int statusCode;
  private final String errorBody;

  public EndeeApiException(String message, int statusCode, String errorBody) {
    super(message);
    this.statusCode = statusCode;
    this.errorBody = errorBody;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getErrorBody() {
    return errorBody;
  }

  /** Raises the appropriate typed exception based on HTTP status code. */
  public static void raiseException(int statusCode, String errorBody) {
    String message = extractMessage(errorBody);

    if (statusCode == 400) {
      throw new EndeeApiException("API Error: " + message, statusCode, errorBody);
    } else if (statusCode == 401) {
      throw new AuthenticationException("Authentication Error: " + message, statusCode, errorBody);
    } else if (statusCode == 402) {
      throw new SubscriptionException("Subscription Error: " + message, statusCode, errorBody);
    } else if (statusCode == 403) {
      throw new ForbiddenException("Forbidden: " + message, statusCode, errorBody);
    } else if (statusCode == 404) {
      throw new NotFoundException("Resource Not Found: " + message, statusCode, errorBody);
    } else if (statusCode == 409) {
      throw new ConflictException("Conflict: " + message, statusCode, errorBody);
    } else if (statusCode >= 500) {
      throw new ServerException(
          "Server Busy: Server is busy. Please try again in sometime", statusCode, errorBody);
    } else {
      throw new EndeeApiException(
          "API Error: Unknown Error. Please try again in sometime", statusCode, errorBody);
    }
  }

  private static String extractMessage(String errorBody) {
    if (errorBody == null || errorBody.isBlank()) {
      return "Unknown error";
    }
    try {
      JsonNode node = OBJECT_MAPPER.readTree(errorBody);
      if (node.has("error")) {
        return node.get("error").asText();
      }
    } catch (Exception ignored) {
    }
    return errorBody;
  }
}

package io.endee.client.exception;

/**
 * Exception thrown when the Endee API returns an error response.
 */
public class EndeeApiException extends EndeeException {

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

    /**
     * Raises the appropriate exception based on status code.
     */
    public static void raiseException(int statusCode, String errorBody) {
        String message = switch (statusCode) {
            case 400 -> "Bad Request: " + errorBody;
            case 401 -> "Unauthorized: " + errorBody;
            case 403 -> "Forbidden: " + errorBody;
            case 404 -> "Not Found: " + errorBody;
            case 409 -> "Conflict: " + errorBody;
            case 500 -> "Internal Server Error: " + errorBody;
            default -> "API Error (" + statusCode + "): " + errorBody;
        };
        throw new EndeeApiException(message, statusCode, errorBody);
    }
}

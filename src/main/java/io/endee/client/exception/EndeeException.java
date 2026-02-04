package io.endee.client.exception;

/**
 * Base exception for all Endee client errors.
 */
public class EndeeException extends RuntimeException {

    public EndeeException(String message) {
        super(message);
    }

    public EndeeException(String message, Throwable cause) {
        super(message, cause);
    }

    public EndeeException(Throwable cause) {
        super(cause);
    }
}

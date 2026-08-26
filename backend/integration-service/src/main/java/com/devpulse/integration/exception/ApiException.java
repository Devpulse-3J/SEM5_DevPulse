package com.devpulse.integration.exception;

import org.springframework.http.HttpStatus;

/**
 * A failure with a deliberate HTTP status, so the controller layer never has to
 * translate service-layer problems into status codes by hand.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

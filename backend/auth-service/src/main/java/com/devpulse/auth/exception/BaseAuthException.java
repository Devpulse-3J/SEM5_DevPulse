package com.devpulse.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Base abstract exception for all auth-service domain exceptions.
 * Encapsulates the HTTP status code, enabling polymorphic exception handling.
 */
public abstract class BaseAuthException extends RuntimeException {

    private final HttpStatus status;

    protected BaseAuthException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    protected BaseAuthException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

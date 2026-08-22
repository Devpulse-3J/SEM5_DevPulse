package com.devpulse.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request collides with existing state — a duplicate project
 * name, or an email already registered to a different company.
 */
public class ConflictException extends BaseAuthException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}

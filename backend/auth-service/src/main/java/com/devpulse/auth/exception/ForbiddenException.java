package com.devpulse.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated caller lacks the role required for an action,
 * or reaches across a company boundary.
 */
public class ForbiddenException extends BaseAuthException {

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}

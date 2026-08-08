package com.devpulse.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when authentication fails due to invalid credentials.
 */
public class InvalidCredentialsException extends BaseAuthException {

    public InvalidCredentialsException() {
        super("Invalid email or password", HttpStatus.UNAUTHORIZED);
    }

    public InvalidCredentialsException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}

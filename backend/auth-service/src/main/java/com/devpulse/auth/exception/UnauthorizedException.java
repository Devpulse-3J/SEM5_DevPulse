package com.devpulse.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the caller's identity cannot be established — missing or
 * malformed gateway headers, or headers that disagree with the JWT.
 */
public class UnauthorizedException extends BaseAuthException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}

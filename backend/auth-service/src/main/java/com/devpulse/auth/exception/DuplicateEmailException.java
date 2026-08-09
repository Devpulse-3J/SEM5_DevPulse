package com.devpulse.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when attempting to register a user with an email that already exists.
 */
public class DuplicateEmailException extends BaseAuthException {

    public DuplicateEmailException(String email) {
        super(String.format("An account with email '%s' already exists", email), HttpStatus.CONFLICT);
    }
}

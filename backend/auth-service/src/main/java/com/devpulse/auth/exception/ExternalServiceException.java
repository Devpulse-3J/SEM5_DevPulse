package com.devpulse.auth.exception;

import org.springframework.http.HttpStatus;

/** A service this one depends on (e.g. GitHub's API) could not answer. */
public class ExternalServiceException extends BaseAuthException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, cause);
    }
}

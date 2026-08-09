package com.devpulse.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource (e.g. User, Company, Project) is not found.
 */
public class ResourceNotFoundException extends BaseAuthException {

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s not found with identifier: %s", resourceName, identifier), HttpStatus.NOT_FOUND);
    }
}

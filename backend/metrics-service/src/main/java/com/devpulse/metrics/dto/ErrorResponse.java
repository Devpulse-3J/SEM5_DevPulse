package com.devpulse.metrics.dto;

public record ErrorResponse(ErrorBody error) {
    public ErrorResponse(String code, String message) {
        this(new ErrorBody(code, message));
    }

    public record ErrorBody(String code, String message) {
    }
}

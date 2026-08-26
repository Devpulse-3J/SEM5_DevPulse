package com.devpulse.metrics.domain;

public enum DeploymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    ROLLED_BACK;

    public static DeploymentStatus fromDatabase(String value) {
        return value == null ? PENDING : valueOf(value.toUpperCase());
    }

    public String toDatabase() {
        return name().toLowerCase();
    }
}

package com.devpulse.notification.dto;

import java.util.List;

public record TeamMessageRequest(
        Long projectId,
        String channel,
        List<Recipient> recipients,
        String subject,
        String message,
        String slackChannel) {

    public record Recipient(Long userId, String email, String name) {}
}

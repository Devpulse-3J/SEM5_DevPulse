package com.devpulse.notification.controller;

import com.devpulse.notification.dto.TeamMessageRequest;
import com.devpulse.notification.email.EmailNotificationService;
import com.devpulse.notification.slack.SlackNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notifications")
public class TeamMessageController {

    private static final int MAX_RECIPIENTS = 100;
    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final EmailNotificationService emailService;
    private final SlackNotificationService slackService;
    private final JdbcTemplate jdbcTemplate;

    public TeamMessageController(
            EmailNotificationService emailService,
            SlackNotificationService slackService,
            JdbcTemplate jdbcTemplate) {
        this.emailService = emailService;
        this.slackService = slackService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/team-message")
    public ResponseEntity<Map<String, Object>> sendTeamMessage(
            @RequestHeader("X-User-Id") Long senderUserId,
            @RequestHeader("X-Company-Id") Long companyId,
            @RequestBody TeamMessageRequest request) {
        String validationError = validate(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("message", validationError));
        }

        if (!isProjectManager(request.projectId(), senderUserId, companyId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only this project's manager can message its team."));
        }

        List<ResolvedRecipient> recipients = resolveRecipients(request);
        if (recipients.size() != request.recipients().size()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Every selected recipient must be a member of this project."));
        }

        int attempted = recipients.size();
        int delivered = 0;
        if ("EMAIL".equalsIgnoreCase(request.channel())) {
            for (ResolvedRecipient recipient : recipients) {
                if (emailService.sendEmailNotification(recipient.email(), request.subject().trim(), request.message().trim())) {
                    delivered++;
                }
            }
        } else {
            String recipientNames = recipients.stream()
                    .map(ResolvedRecipient::name)
                    .distinct()
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("team members");
            String slackMessage = "For: " + recipientNames + "\n\n" + request.message().trim();
            if (slackService.sendSlackNotification(request.slackChannel(), slackMessage)) {
                delivered = attempted;
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("attempted", attempted);
        response.put("delivered", delivered);
        response.put("failed", attempted - delivered);
        response.put("senderUserId", senderUserId);
        response.put("companyId", companyId);
        return ResponseEntity.status(delivered > 0 ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(response);
    }

    private boolean isProjectManager(Long projectId, Long userId, Long companyId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM project_members pm
                JOIN projects p ON p.project_id = pm.project_id
                WHERE pm.project_id = ? AND pm.user_id = ? AND p.company_id = ? AND pm.role = 'manager'
                """,
                Integer.class,
                projectId,
                userId,
                companyId);
        return count != null && count > 0;
    }

    private List<ResolvedRecipient> resolveRecipients(TeamMessageRequest request) {
        List<Long> userIds = request.recipients().stream()
                .map(TeamMessageRequest.Recipient::userId)
                .distinct()
                .toList();
        String placeholders = userIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        Object[] parameters = new Object[userIds.size() + 1];
        parameters[0] = request.projectId();
        for (int index = 0; index < userIds.size(); index++) parameters[index + 1] = userIds.get(index);

        return jdbcTemplate.query(
                """
                SELECT u.user_id, u.email, u.full_name
                FROM users u
                JOIN project_members pm ON pm.user_id = u.user_id
                WHERE pm.project_id = ? AND u.user_id IN (%s)
                """.formatted(placeholders),
                (resultSet, rowNumber) -> new ResolvedRecipient(
                        resultSet.getLong("user_id"),
                        resultSet.getString("email"),
                        resultSet.getString("full_name") != null
                                ? resultSet.getString("full_name")
                                : resultSet.getString("email")),
                parameters);
    }

    private String validate(TeamMessageRequest request) {
        if (request == null || request.projectId() == null) return "projectId is required.";
        if (request.recipients() == null || request.recipients().isEmpty()) return "Select at least one recipient.";
        if (request.recipients().size() > MAX_RECIPIENTS) return "A message can have at most 100 recipients.";
        if (request.recipients().stream().anyMatch(recipient -> recipient == null || recipient.userId() == null)) return "Every recipient must have a userId.";
        if (request.recipients().stream().map(TeamMessageRequest.Recipient::userId).distinct().count() != request.recipients().size()) return "Recipients must be unique.";
        if (request.message() == null || request.message().isBlank()) return "Message is required.";
        if (request.message().length() > MAX_MESSAGE_LENGTH) return "Message must be 4000 characters or fewer.";
        if (!List.of("EMAIL", "SLACK").contains(request.channel())) return "channel must be EMAIL or SLACK.";
        if ("EMAIL".equals(request.channel())) {
            if (request.subject() == null || request.subject().isBlank()) return "Subject is required for email.";
            if (request.subject().length() > 160) return "Subject must be 160 characters or fewer.";
            if (request.recipients().stream().anyMatch(recipient -> recipient.email() == null || recipient.email().isBlank())) {
                return "Every email recipient must have an email address.";
            }
        }
        if ("SLACK".equals(request.channel()) && (request.slackChannel() == null || request.slackChannel().isBlank())) {
            return "Slack channel is required.";
        }
        return null;
    }

    private record ResolvedRecipient(Long userId, String email, String name) {}
}

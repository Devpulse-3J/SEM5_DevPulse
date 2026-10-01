package com.devpulse.notification.service;

import com.devpulse.contracts.events.DeploymentCreatedEvent;
import com.devpulse.notification.email.EmailNotificationService;
import com.devpulse.notification.entity.Alert;
import com.devpulse.notification.entity.Notification;
import com.devpulse.notification.repository.AlertRepository;
import com.devpulse.notification.repository.NotificationRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository.DeployedProject;
import com.devpulse.notification.repository.ProjectDirectoryRepository.Manager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Emails a project's managers when a deployment fails.
 *
 * <p>One real failure arrives as several deployment.created events: GitHub
 * reports it as both a workflow_job and a deployment_status (with different
 * ids), and deliveries can repeat. The alert's dedup key is the project plus
 * the deployed commit, so managers get one email per failed commit.
 */
@Service
public class DeploymentFailureNotifier {

    private static final Logger log = LoggerFactory.getLogger(DeploymentFailureNotifier.class);
    // "failure" from workflow_job / GitHub deployment_status; "error" is GitHub's other failed state.
    // rolled_back (a cancelled or timed-out run) is deliberately not a failure here.
    private static final Set<String> FAILED_STATUSES = Set.of("failure", "failed", "error");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final ProjectDirectoryRepository projectDirectory;
    private final AlertRepository alertRepository;
    private final NotificationRepository notificationRepository;
    private final EmailNotificationService emailNotificationService;

    public DeploymentFailureNotifier(ProjectDirectoryRepository projectDirectory,
                                     AlertRepository alertRepository,
                                     NotificationRepository notificationRepository,
                                     EmailNotificationService emailNotificationService) {
        this.projectDirectory = projectDirectory;
        this.alertRepository = alertRepository;
        this.notificationRepository = notificationRepository;
        this.emailNotificationService = emailNotificationService;
    }

    public void handle(DeploymentCreatedEvent event) {
        String status = event.getStatus() == null ? "" : event.getStatus().toLowerCase(Locale.ROOT);
        if (!FAILED_STATUSES.contains(status) || event.getCompanyId() == null) {
            return;
        }

        Optional<DeployedProject> resolved = projectDirectory.findProjectForDeployment(
                event.getCompanyId(), event.getProjectId(), event.getCommitSha());
        if (resolved.isEmpty()) {
            log.warn("Failed deployment {} in company {} matches no linked project; nobody to notify",
                    event.getDeploymentId(), event.getCompanyId());
            return;
        }
        DeployedProject project = resolved.get();

        String sha = event.getCommitSha() == null ? "" : event.getCommitSha().trim();
        String dedupKey = "deployment-failed:" + project.projectId() + ":"
                + (sha.isEmpty() ? "deployment-" + event.getDeploymentId() : sha);
        if (alertRepository.existsByDedupKey(dedupKey)) {
            log.info("Already alerted for {}; skipping repeat delivery", dedupKey);
            return;
        }

        String shortSha = sha.length() > 7 ? sha.substring(0, 7) : sha;
        Alert alert = new Alert(event.getCompanyId(), project.projectId(), null, "deployment",
                event.getDeploymentId(), "critical",
                "Deployment failed in " + project.projectName() + " (" + project.repoFullName() + " @ " + shortSha + ")");
        alert.setDedupKey(dedupKey);
        try {
            alert = alertRepository.saveAndFlush(alert);
        } catch (DataIntegrityViolationException e) {
            // Two deliveries raced past the exists check; the unique index let only one through.
            log.info("Already alerted for {} (concurrent delivery); skipping", dedupKey);
            return;
        }

        List<Manager> managers = projectDirectory.findManagers(project.projectId());
        if (managers.isEmpty()) {
            log.info("Deployment failed in project {} but it has no manager; alert {} recorded, no email sent",
                    project.projectId(), alert.getAlertId());
            return;
        }

        String subject = "Deployment failed in " + project.projectName() + (shortSha.isEmpty() ? "" : " (" + shortSha + ")");
        String body = buildBody(project, sha, shortSha, event);
        for (Manager manager : managers) {
            boolean sent = emailNotificationService.sendEmailNotification(manager.email(), subject, body);
            Notification notification = new Notification(
                    event.getCompanyId(), alert.getAlertId(), manager.userId(), "email", sent ? "sent" : "failed");
            if (sent) {
                notification.setSentAt(Instant.now());
            }
            notificationRepository.save(notification);
        }
        log.info("Deployment failure alert {} for project {} emailed to {} manager(s)",
                alert.getAlertId(), project.projectId(), managers.size());
    }

    private String buildBody(DeployedProject project, String sha, String shortSha, DeploymentCreatedEvent event) {
        Instant when = event.getDeployedAt() != null ? event.getDeployedAt()
                : event.getTimestamp() != null ? event.getTimestamp() : Instant.now();
        String repoUrl = "https://github.com/" + project.repoFullName();

        StringBuilder body = new StringBuilder();
        body.append("A deployment failed in project \"").append(project.projectName()).append("\".\n\n");
        body.append("Project:     ").append(project.projectName()).append('\n');
        body.append("Repository:  ").append(project.repoFullName()).append('\n');
        body.append("Commit:      ").append(shortSha.isEmpty() ? "unknown" : shortSha).append('\n');
        body.append("Environment: ").append(event.getEnvironment() == null ? "production" : event.getEnvironment()).append('\n');
        body.append("Time:        ").append(TIME_FORMAT.format(when)).append("\n\n");
        if (!sha.isEmpty()) {
            body.append("Commit:  ").append(repoUrl).append("/commit/").append(sha).append('\n');
        }
        body.append("Actions: ").append(repoUrl).append("/actions\n");
        return body.toString();
    }
}

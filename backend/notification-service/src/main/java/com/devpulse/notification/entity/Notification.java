package com.devpulse.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Integer notificationId;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "alert_id", nullable = false)
    private Integer alertId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "sent_at")
    private Instant sentAt;

    public Notification() {}

    public Notification(Integer companyId, Integer alertId, Integer userId, String channel, String status) {
        this.companyId = companyId;
        this.alertId = alertId;
        this.userId = userId;
        this.channel = channel;
        this.status = status != null ? status : "pending";
    }

    public Integer getNotificationId() { return notificationId; }
    public void setNotificationId(Integer notificationId) { this.notificationId = notificationId; }

    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }

    public Integer getAlertId() { return alertId; }
    public void setAlertId(Integer alertId) { this.alertId = alertId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}

package com.devpulse.metrics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "pr_reviews")
public class PrReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Integer id;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "pr_id", nullable = false)
    private Integer prId;

    @Column(name = "reviewer_id")
    private Integer reviewerId;

    @Column(name = "review_state", nullable = false, length = 30)
    private String reviewState;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    public PrReviewEntity() {
    }

    public PrReviewEntity(Integer companyId, Integer prId, Integer reviewerId, String reviewState, Instant reviewedAt) {
        this.companyId = companyId;
        this.prId = prId;
        this.reviewerId = reviewerId;
        this.reviewState = reviewState;
        this.reviewedAt = reviewedAt;
    }

    @PrePersist
    void onCreate() {
        if (reviewedAt == null) {
            reviewedAt = Instant.now();
        }
    }

    public Integer getId() { return id; }
    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }
    public Integer getPrId() { return prId; }
    public void setPrId(Integer prId) { this.prId = prId; }
    public Integer getReviewerId() { return reviewerId; }
    public void setReviewerId(Integer reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewState() { return reviewState; }
    public void setReviewState(String reviewState) { this.reviewState = reviewState; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}

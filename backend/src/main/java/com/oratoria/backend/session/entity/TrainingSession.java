package com.oratoria.backend.session.entity;

import com.oratoria.backend.training.TrainingType;
import com.oratoria.backend.user.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "training_sessions")
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 40)
    private TrainingType trainingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SessionStatus status = SessionStatus.CREATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "overall_score")
    private Double overallScore;

    protected TrainingSession() {
    }

    public TrainingSession(UserAccount user, TrainingType trainingType) {
        this.user = user;
        this.trainingType = trainingType;
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public TrainingType getTrainingType() { return trainingType; }
    public SessionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public Double getOverallScore() { return overallScore; }
}

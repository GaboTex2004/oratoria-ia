package com.oratoria.backend.session.entity;

import com.oratoria.backend.exercise.entity.Exercise;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 40)
    private TrainingType trainingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SessionStatus status = SessionStatus.CREATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(name = "audio_storage_path", length = 500)
    private String audioStoragePath;

    @Column(name = "audio_mime_type", length = 100)
    private String audioMimeType;

    @Column(name = "audio_size_bytes")
    private Long audioSizeBytes;

    @Column(name = "audio_original_filename", length = 255)
    private String audioOriginalFilename;

    protected TrainingSession() {
    }

    public TrainingSession(UserAccount user, TrainingType trainingType) {
        this.user = user;
        this.trainingType = trainingType;
    }

    public TrainingSession(UserAccount user, Exercise exercise, TrainingType trainingType) {
        this.user = user;
        this.exercise = exercise;
        this.trainingType = trainingType;
    }

    public void markRecording() {
        if (this.status == SessionStatus.CREATED) {
            this.status = SessionStatus.RECORDING;
        }
    }

    public void completeWithAudio(int durationSeconds, String storagePath, String mimeType,
                                  long sizeBytes, String originalFilename) {
        this.durationSeconds = durationSeconds;
        this.audioStoragePath = storagePath;
        this.audioMimeType = mimeType;
        this.audioSizeBytes = sizeBytes;
        this.audioOriginalFilename = originalFilename;
        this.completedAt = Instant.now();
        this.status = SessionStatus.COMPLETED;
    }

    public boolean hasAudio() {
        return audioStoragePath != null && !audioStoragePath.isBlank();
    }

    public void updateOverallScore(Double overallScore) {
        this.overallScore = overallScore;
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public Exercise getExercise() { return exercise; }
    public TrainingType getTrainingType() { return trainingType; }
    public SessionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public Double getOverallScore() { return overallScore; }
    public String getAudioStoragePath() { return audioStoragePath; }
    public String getAudioMimeType() { return audioMimeType; }
    public Long getAudioSizeBytes() { return audioSizeBytes; }
    public String getAudioOriginalFilename() { return audioOriginalFilename; }
}

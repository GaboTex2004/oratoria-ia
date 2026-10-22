package com.oratoria.backend.exercise.entity;

import com.oratoria.backend.training.TrainingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exercises")
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 400)
    private String description;

    @Column(nullable = false, length = 1000)
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 40)
    private TrainingType trainingType;

    @Column(name = "recommended_duration_seconds", nullable = false)
    private int recommendedDurationSeconds;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Exercise() {
    }

    public Exercise(String code, String title, String description, String prompt,
                    TrainingType trainingType, int recommendedDurationSeconds) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.prompt = prompt;
        this.trainingType = trainingType;
        this.recommendedDurationSeconds = recommendedDurationSeconds;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getPrompt() { return prompt; }
    public TrainingType getTrainingType() { return trainingType; }
    public int getRecommendedDurationSeconds() { return recommendedDurationSeconds; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}

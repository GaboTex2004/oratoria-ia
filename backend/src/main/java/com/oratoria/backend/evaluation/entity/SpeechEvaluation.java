package com.oratoria.backend.evaluation.entity;

import com.oratoria.backend.session.entity.TrainingSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "speech_evaluations")
public class SpeechEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private TrainingSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvaluationStatus status = EvaluationStatus.PENDING;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion = "v1";

    @Column(name = "rubric_version", length = 60)
    private String rubricVersion;

    @Column(length = 40)
    private String provider;

    @Column(length = 80)
    private String model;

    @Column(name = "sufficient_evidence")
    private Boolean sufficientEvidence;

    @Column(name = "insufficient_evidence_reason", columnDefinition = "TEXT")
    private String insufficientEvidenceReason;

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(name = "executive_summary", columnDefinition = "TEXT")
    private String executiveSummary;

    @Column(name = "measured_indicators_json", columnDefinition = "TEXT")
    private String measuredIndicatorsJson;

    @Column(name = "criteria_scores_json", columnDefinition = "TEXT")
    private String criteriaScoresJson;

    @Column(name = "strengths_json", columnDefinition = "TEXT")
    private String strengthsJson;

    @Column(name = "areas_for_improvement_json", columnDefinition = "TEXT")
    private String areasForImprovementJson;

    @Column(name = "recommendations_json", columnDefinition = "TEXT")
    private String recommendationsJson;

    @Column(name = "improved_examples_json", columnDefinition = "TEXT")
    private String improvedExamplesJson;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private boolean retryable = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SpeechEvaluation() {
    }

    public SpeechEvaluation(TrainingSession session) {
        this.session = session;
        this.status = EvaluationStatus.PENDING;
    }

    public void markProcessing() {
        this.status = EvaluationStatus.PROCESSING;
        this.attemptCount += 1;
        this.startedAt = Instant.now();
        this.updatedAt = Instant.now();
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markPendingForRetry() {
        this.status = EvaluationStatus.PENDING;
        this.updatedAt = Instant.now();
    }

    public void markCompleted(
            String schemaVersion,
            String rubricVersion,
            String provider,
            String model,
            boolean sufficientEvidence,
            String insufficientEvidenceReason,
            Double overallScore,
            String executiveSummary,
            String measuredIndicatorsJson,
            String criteriaScoresJson,
            String strengthsJson,
            String areasForImprovementJson,
            String recommendationsJson,
            String improvedExamplesJson) {
        this.schemaVersion = schemaVersion != null ? schemaVersion : "v1";
        this.rubricVersion = rubricVersion;
        this.provider = provider;
        this.model = model;
        this.sufficientEvidence = sufficientEvidence;
        this.insufficientEvidenceReason = insufficientEvidenceReason;
        this.overallScore = overallScore;
        this.executiveSummary = executiveSummary;
        this.measuredIndicatorsJson = measuredIndicatorsJson;
        this.criteriaScoresJson = criteriaScoresJson;
        this.strengthsJson = strengthsJson;
        this.areasForImprovementJson = areasForImprovementJson;
        this.recommendationsJson = recommendationsJson;
        this.improvedExamplesJson = improvedExamplesJson;
        this.status = EvaluationStatus.COMPLETED;
        this.retryable = false;
        this.errorCode = null;
        this.errorMessage = null;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markFailed(String errorCode, String errorMessage, boolean retryable) {
        this.status = EvaluationStatus.FAILED;
        this.errorCode = errorCode != null && errorCode.length() > 60 ? errorCode.substring(0, 60) : errorCode;
        this.errorMessage = errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500)
                : errorMessage;
        this.retryable = retryable;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TrainingSession getSession() { return session; }
    public EvaluationStatus getStatus() { return status; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getRubricVersion() { return rubricVersion; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public Boolean getSufficientEvidence() { return sufficientEvidence; }
    public String getInsufficientEvidenceReason() { return insufficientEvidenceReason; }
    public Double getOverallScore() { return overallScore; }
    public String getExecutiveSummary() { return executiveSummary; }
    public String getMeasuredIndicatorsJson() { return measuredIndicatorsJson; }
    public String getCriteriaScoresJson() { return criteriaScoresJson; }
    public String getStrengthsJson() { return strengthsJson; }
    public String getAreasForImprovementJson() { return areasForImprovementJson; }
    public String getRecommendationsJson() { return recommendationsJson; }
    public String getImprovedExamplesJson() { return improvedExamplesJson; }
    public int getAttemptCount() { return attemptCount; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isRetryable() { return retryable; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

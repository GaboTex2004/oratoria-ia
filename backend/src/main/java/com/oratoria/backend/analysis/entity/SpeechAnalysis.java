package com.oratoria.backend.analysis.entity;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "speech_analyses")
public class SpeechAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private TrainingSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalysisStatus status = AnalysisStatus.PENDING;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion = "v1";

    @Column(length = 40)
    private String provider;

    @Column(length = 80)
    private String model;

    @Column(length = 16)
    private String language;

    @Column(name = "transcript_text", columnDefinition = "TEXT")
    private String transcriptText;

    @Column(name = "segments_json", columnDefinition = "TEXT")
    private String segmentsJson;

    @Column(name = "words_json", columnDefinition = "TEXT")
    private String wordsJson;

    @Column(name = "filler_counts_json", columnDefinition = "TEXT")
    private String fillerCountsJson;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "recording_duration_seconds")
    private Double recordingDurationSeconds;

    @Column(name = "estimated_spoken_duration_seconds")
    private Double estimatedSpokenDurationSeconds;

    @Column(name = "spoken_time_ratio")
    private Double spokenTimeRatio;

    @Column(name = "words_per_minute")
    private Double wordsPerMinute;

    @Column(name = "spoken_words_per_minute")
    private Double spokenWordsPerMinute;

    @Column(name = "filler_words_total")
    private Integer fillerWordsTotal;

    @Column(name = "estimated_pause_count")
    private Integer estimatedPauseCount;

    @Column(name = "total_pause_duration_seconds")
    private Double totalPauseDurationSeconds;

    @Column(name = "longest_pause_seconds")
    private Double longestPauseSeconds;

    @Column(name = "timestamps_available")
    private Boolean timestampsAvailable;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private boolean retryable = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SpeechAnalysis() {
    }

    public SpeechAnalysis(TrainingSession session) {
        this.session = session;
        this.status = AnalysisStatus.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markPendingForRetry() {
        this.status = AnalysisStatus.PENDING;
        this.updatedAt = Instant.now();
    }

    public void markProcessing() {
        this.status = AnalysisStatus.PROCESSING;
        this.attemptCount += 1;
        this.startedAt = Instant.now();
        this.errorCode = null;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markCompleted(
            String schemaVersion,
            String provider,
            String model,
            String language,
            String transcriptText,
            String segmentsJson,
            String wordsJson,
            String fillerCountsJson,
            Integer wordCount,
            Double recordingDurationSeconds,
            Double estimatedSpokenDurationSeconds,
            Double spokenTimeRatio,
            Double wordsPerMinute,
            Double spokenWordsPerMinute,
            Integer fillerWordsTotal,
            Integer estimatedPauseCount,
            Double totalPauseDurationSeconds,
            Double longestPauseSeconds,
            Boolean timestampsAvailable) {
        this.schemaVersion = schemaVersion != null ? schemaVersion : "v1";
        this.provider = provider;
        this.model = model;
        this.language = language;
        this.transcriptText = transcriptText;
        this.segmentsJson = segmentsJson;
        this.wordsJson = wordsJson;
        this.fillerCountsJson = fillerCountsJson;
        this.wordCount = wordCount;
        this.recordingDurationSeconds = recordingDurationSeconds;
        this.estimatedSpokenDurationSeconds = estimatedSpokenDurationSeconds;
        this.spokenTimeRatio = spokenTimeRatio;
        this.wordsPerMinute = wordsPerMinute;
        this.spokenWordsPerMinute = spokenWordsPerMinute;
        this.fillerWordsTotal = fillerWordsTotal;
        this.estimatedPauseCount = estimatedPauseCount;
        this.totalPauseDurationSeconds = totalPauseDurationSeconds;
        this.longestPauseSeconds = longestPauseSeconds;
        this.timestampsAvailable = timestampsAvailable;
        this.status = AnalysisStatus.COMPLETED;
        this.errorCode = null;
        this.errorMessage = null;
        this.retryable = false;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markFailed(String errorCode, String errorMessage, boolean retryable) {
        this.status = AnalysisStatus.FAILED;
        this.errorCode = errorCode != null && errorCode.length() > 80
                ? errorCode.substring(0, 80)
                : errorCode;
        this.errorMessage = errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500)
                : errorMessage;
        this.retryable = retryable;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TrainingSession getSession() { return session; }
    public AnalysisStatus getStatus() { return status; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getLanguage() { return language; }
    public String getTranscriptText() { return transcriptText; }
    public String getSegmentsJson() { return segmentsJson; }
    public String getWordsJson() { return wordsJson; }
    public String getFillerCountsJson() { return fillerCountsJson; }
    public Integer getWordCount() { return wordCount; }
    public Double getRecordingDurationSeconds() { return recordingDurationSeconds; }
    public Double getEstimatedSpokenDurationSeconds() { return estimatedSpokenDurationSeconds; }
    public Double getSpokenTimeRatio() { return spokenTimeRatio; }
    public Double getWordsPerMinute() { return wordsPerMinute; }
    public Double getSpokenWordsPerMinute() { return spokenWordsPerMinute; }
    public Integer getFillerWordsTotal() { return fillerWordsTotal; }
    public Integer getEstimatedPauseCount() { return estimatedPauseCount; }
    public Double getTotalPauseDurationSeconds() { return totalPauseDurationSeconds; }
    public Double getLongestPauseSeconds() { return longestPauseSeconds; }
    public Boolean getTimestampsAvailable() { return timestampsAvailable; }
    public int getAttemptCount() { return attemptCount; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isRetryable() { return retryable; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

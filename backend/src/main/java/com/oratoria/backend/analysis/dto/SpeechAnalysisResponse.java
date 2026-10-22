package com.oratoria.backend.analysis.dto;

import com.oratoria.backend.analysis.entity.AnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpeechAnalysisResponse(
        UUID id,
        UUID sessionId,
        AnalysisStatus status,
        String schemaVersion,
        String provider,
        String model,
        String language,
        String transcriptText,
        List<TranscriptSegmentDto> segments,
        List<WordTimestampDto> words,
        VerbalMetricsDto metrics,
        int attemptCount,
        String errorCode,
        String errorMessage,
        boolean retryable,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt) {
}

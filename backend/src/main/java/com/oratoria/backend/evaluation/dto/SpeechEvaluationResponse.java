package com.oratoria.backend.evaluation.dto;

import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpeechEvaluationResponse(
        UUID id,
        UUID sessionId,
        EvaluationStatus status,
        String schemaVersion,
        String rubricVersion,
        String provider,
        String model,
        Boolean sufficientEvidence,
        String insufficientEvidenceReason,
        Double overallScore,
        String executiveSummary,
        List<MeasuredIndicatorDto> measuredIndicators,
        List<CriterionScoreDto> criteriaScores,
        List<String> strengths,
        List<String> areasForImprovement,
        List<RecommendationDto> recommendations,
        List<ImprovedExampleDto> improvedExamples,
        int attemptCount,
        String errorCode,
        String errorMessage,
        boolean retryable,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt) {
}

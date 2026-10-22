package com.oratoria.backend.evaluation.client;

import com.oratoria.backend.analysis.dto.VerbalMetricsDto;
import com.oratoria.backend.evaluation.dto.CriterionScoreDto;
import com.oratoria.backend.evaluation.dto.ImprovedExampleDto;
import com.oratoria.backend.evaluation.dto.MeasuredIndicatorDto;
import com.oratoria.backend.evaluation.dto.RecommendationDto;
import com.oratoria.backend.training.TrainingType;
import java.util.List;
import java.util.UUID;

public interface AiSpeechEvaluationClient {
    AiSpeechEvaluationPayload evaluateSpeech(
            UUID sessionId,
            TrainingType trainingType,
            String exerciseTitle,
            String exercisePrompt,
            String transcriptText,
            VerbalMetricsDto metrics);

    record AiSpeechEvaluationRequest(
            String sessionId,
            String trainingType,
            String exerciseTitle,
            String exercisePrompt,
            String transcriptText,
            VerbalMetricsDto metrics) {
    }

    record AiSpeechEvaluationPayload(
            String schemaVersion,
            String sessionId,
            String trainingType,
            String rubricVersion,
            String provider,
            String model,
            boolean sufficientEvidence,
            String insufficientEvidenceReason,
            Double overallScore,
            String executiveSummary,
            List<MeasuredIndicatorDto> measuredIndicators,
            List<CriterionScoreDto> criteriaScores,
            List<String> strengths,
            List<String> areasForImprovement,
            List<RecommendationDto> recommendations,
            List<ImprovedExampleDto> improvedExamples) {
    }

    class AiEvaluationClientException extends RuntimeException {
        private final String errorCode;
        private final boolean retryable;

        public AiEvaluationClientException(String errorCode, String message, boolean retryable) {
            super(message);
            this.errorCode = errorCode;
            this.retryable = retryable;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}

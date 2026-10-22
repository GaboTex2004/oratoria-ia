package com.oratoria.backend.analysis.client;

import com.oratoria.backend.analysis.dto.TranscriptSegmentDto;
import com.oratoria.backend.analysis.dto.VerbalMetricsDto;
import com.oratoria.backend.analysis.dto.WordTimestampDto;
import java.util.List;
import java.util.UUID;

public interface AiSpeechAnalysisClient {
    AiSpeechAnalysisPayload analyzeSpeech(
            UUID sessionId,
            byte[] audioBytes,
            String mimeType,
            int durationSeconds,
            String filename);

    record AiSpeechAnalysisPayload(
            String schemaVersion,
            String sessionId,
            String provider,
            String model,
            String language,
            String transcriptText,
            List<TranscriptSegmentDto> segments,
            List<WordTimestampDto> words,
            VerbalMetricsDto metrics) {
    }

    class AiAnalysisClientException extends RuntimeException {
        private final String errorCode;
        private final boolean retryable;

        public AiAnalysisClientException(String errorCode, String message, boolean retryable) {
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

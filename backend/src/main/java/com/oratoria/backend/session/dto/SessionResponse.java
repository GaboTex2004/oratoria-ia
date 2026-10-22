package com.oratoria.backend.session.dto;

import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.session.entity.SessionStatus;
import com.oratoria.backend.training.TrainingType;
import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        TrainingType trainingType,
        SessionStatus status,
        AnalysisStatus analysisStatus,
        EvaluationStatus evaluationStatus,
        UUID exerciseId,
        String exerciseTitle,
        Instant createdAt,
        Instant completedAt,
        Integer durationSeconds,
        Double overallScore,
        boolean hasAudio,
        String audioMimeType,
        Long audioSizeBytes) {
}

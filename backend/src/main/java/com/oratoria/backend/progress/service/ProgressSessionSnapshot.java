package com.oratoria.backend.progress.service;

import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.session.entity.SessionStatus;
import com.oratoria.backend.training.TrainingType;
import java.time.Instant;
import java.util.UUID;

record ProgressSessionSnapshot(
        UUID sessionId,
        String exerciseTitle,
        TrainingType trainingType,
        Instant createdAt,
        Instant completedAt,
        Integer durationSeconds,
        SessionStatus sessionStatus,
        AnalysisStatus analysisStatus,
        Integer wordCount,
        Double wordsPerMinute,
        Double spokenWordsPerMinute,
        Integer fillerWordsTotal,
        String fillerCountsJson,
        Integer estimatedPauseCount,
        Double spokenTimeRatio,
        EvaluationStatus evaluationStatus,
        Boolean sufficientEvidence,
        Double evaluationOverallScore,
        String criteriaScoresJson,
        String strengthsJson,
        String areasForImprovementJson) {
}

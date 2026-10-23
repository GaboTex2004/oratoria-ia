package com.oratoria.backend.progress.dto;

import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.session.entity.SessionStatus;
import com.oratoria.backend.training.TrainingType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProgressSummaryResponse(
        Overview overview,
        List<HistoryItem> history,
        List<CriterionEvolution> criteriaEvolution,
        Insights insights,
        SubscriptionSummary subscription,
        int historyLimit,
        boolean historyTruncated) {

    public record Overview(
            long totalSessions,
            long completedSessions,
            long evaluatedSessions,
            long totalPracticeSeconds,
            Double latestOverallScore,
            Double averageOverallScore,
            Double scoreDelta,
            Double latestWordsPerMinute,
            Double averageWordsPerMinute,
            Integer latestFillerWords,
            Double averageFillerWords,
            Integer fillerDelta) {
    }

    public record HistoryItem(
            UUID sessionId,
            String exerciseTitle,
            TrainingType trainingType,
            Instant createdAt,
            Instant completedAt,
            Integer durationSeconds,
            SessionStatus sessionStatus,
            AnalysisStatus analysisStatus,
            EvaluationStatus evaluationStatus,
            Boolean sufficientEvidence,
            Double overallScore,
            Integer wordCount,
            Double wordsPerMinute,
            Double spokenWordsPerMinute,
            Integer fillerWordsTotal,
            Integer estimatedPauseCount,
            Double spokenTimeRatio,
            List<CriterionScore> criteriaScores) {
    }

    public record CriterionScore(
            String code,
            String label,
            Double score,
            String level) {
    }

    public record CriterionEvolution(
            String code,
            Double latestValue,
            Double averageValue,
            Double delta,
            List<CriterionPoint> series) {
    }

    public record CriterionPoint(
            UUID sessionId,
            Instant completedAt,
            Double score) {
    }

    public record Insights(
            List<String> recentStrengths,
            List<String> recentAreasForImprovement,
            List<FillerCount> frequentFillers) {
    }

    public record FillerCount(String filler, long count) {
    }

    public record SubscriptionSummary(
            String plan,
            Integer sessionsLimit,
            boolean unlimitedSessions,
            Integer sessionsUsedInPeriod,
            String usageDefinition) {
    }
}

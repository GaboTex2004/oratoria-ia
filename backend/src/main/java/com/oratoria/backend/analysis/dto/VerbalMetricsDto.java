package com.oratoria.backend.analysis.dto;

import java.util.Map;

public record VerbalMetricsDto(
        int wordCount,
        double recordingDurationSeconds,
        Double estimatedSpokenDurationSeconds,
        Double spokenTimeRatio,
        double wordsPerMinute,
        Double spokenWordsPerMinute,
        int fillerWordsTotal,
        Map<String, Integer> fillerWordCounts,
        Integer estimatedPauseCount,
        Double totalPauseDurationSeconds,
        Double longestPauseSeconds,
        boolean timestampsAvailable) {
}

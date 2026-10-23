package com.oratoria.backend.progress.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.CriterionEvolution;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.CriterionPoint;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.CriterionScore;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.FillerCount;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.HistoryItem;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.Insights;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.Overview;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.SubscriptionSummary;
import com.oratoria.backend.session.entity.SessionStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class ProgressCalculator {
    static final int HISTORY_LIMIT = 100;
    private static final int RECENT_INSIGHT_LIMIT = 5;
    private static final int FREQUENT_FILLER_LIMIT = 10;
    private static final List<String> CRITERIA = List.of(
            "CLARITY", "COHERENCE", "ORGANIZATION", "ARGUMENTATION");
    private static final Logger log = LoggerFactory.getLogger(ProgressCalculator.class);

    private final ObjectMapper objectMapper;

    ProgressCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ProgressSummaryResponse calculate(
            List<ProgressSessionSnapshot> source,
            SubscriptionSummary subscription) {
        List<ProgressSessionSnapshot> rows = new ArrayList<>(source);
        rows.sort(Comparator
                .comparing(ProgressSessionSnapshot::createdAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ProgressSessionSnapshot::sessionId));

        List<ProgressSessionSnapshot> validEvaluations = rows.stream()
                .filter(this::hasValidEvaluation)
                .toList();
        List<ProgressSessionSnapshot> completedAnalyses = rows.stream()
                .filter(row -> row.analysisStatus() == AnalysisStatus.COMPLETED)
                .toList();

        List<Double> scores = validEvaluations.stream()
                .map(ProgressSessionSnapshot::evaluationOverallScore)
                .toList();
        List<Double> wpms = completedAnalyses.stream()
                .map(ProgressSessionSnapshot::wordsPerMinute)
                .filter(this::isNonNegativeFinite)
                .toList();
        List<Integer> fillers = completedAnalyses.stream()
                .map(ProgressSessionSnapshot::fillerWordsTotal)
                .filter(value -> value != null && value >= 0)
                .toList();

        long totalPracticeSeconds = rows.stream()
                .filter(row -> row.sessionStatus() == SessionStatus.COMPLETED)
                .map(ProgressSessionSnapshot::durationSeconds)
                .filter(value -> value != null && value > 0)
                .mapToLong(Integer::longValue)
                .sum();

        Overview overview = new Overview(
                rows.size(),
                rows.stream().filter(row -> row.sessionStatus() == SessionStatus.COMPLETED).count(),
                rows.stream().filter(row -> row.sessionStatus() == SessionStatus.COMPLETED
                        && row.evaluationStatus() == EvaluationStatus.COMPLETED).count(),
                totalPracticeSeconds,
                latest(scores),
                averageDoubles(scores),
                delta(scores),
                latest(wpms),
                averageDoubles(wpms),
                latestInteger(fillers),
                averageIntegers(fillers),
                integerDelta(fillers)
        );

        int historyStart = Math.max(0, rows.size() - HISTORY_LIMIT);
        List<HistoryItem> history = rows.subList(historyStart, rows.size()).stream()
                .map(this::toHistoryItem)
                .toList();

        return new ProgressSummaryResponse(
                overview,
                history,
                buildCriteriaEvolution(validEvaluations),
                buildInsights(validEvaluations, completedAnalyses),
                subscription,
                HISTORY_LIMIT,
                rows.size() > HISTORY_LIMIT
        );
    }

    private boolean hasValidEvaluation(ProgressSessionSnapshot row) {
        return row.sessionStatus() == SessionStatus.COMPLETED
                && row.evaluationStatus() == EvaluationStatus.COMPLETED
                && Boolean.TRUE.equals(row.sufficientEvidence())
                && isScore(row.evaluationOverallScore());
    }

    private HistoryItem toHistoryItem(ProgressSessionSnapshot row) {
        List<CriterionScore> criteria = row.evaluationStatus() == EvaluationStatus.COMPLETED
                ? parseCriteria(row)
                : List.of();
        return new HistoryItem(
                row.sessionId(),
                row.exerciseTitle(),
                row.trainingType(),
                row.createdAt(),
                row.completedAt(),
                row.durationSeconds(),
                row.sessionStatus(),
                row.analysisStatus(),
                row.evaluationStatus(),
                row.sufficientEvidence(),
                hasValidEvaluation(row) ? round(row.evaluationOverallScore()) : null,
                row.analysisStatus() == AnalysisStatus.COMPLETED ? row.wordCount() : null,
                completedMetric(row, ProgressSessionSnapshot::wordsPerMinute),
                completedMetric(row, ProgressSessionSnapshot::spokenWordsPerMinute),
                row.analysisStatus() == AnalysisStatus.COMPLETED ? validNonNegative(row.fillerWordsTotal()) : null,
                row.analysisStatus() == AnalysisStatus.COMPLETED ? validNonNegative(row.estimatedPauseCount()) : null,
                completedMetric(row, ProgressSessionSnapshot::spokenTimeRatio),
                criteria
        );
    }

    private Double completedMetric(
            ProgressSessionSnapshot row,
            java.util.function.Function<ProgressSessionSnapshot, Double> extractor) {
        if (row.analysisStatus() != AnalysisStatus.COMPLETED) return null;
        Double value = extractor.apply(row);
        return isNonNegativeFinite(value) ? round(value) : null;
    }

    private List<CriterionEvolution> buildCriteriaEvolution(
            List<ProgressSessionSnapshot> validEvaluations) {
        Map<String, List<CriterionPoint>> points = new LinkedHashMap<>();
        CRITERIA.forEach(code -> points.put(code, new ArrayList<>()));

        for (ProgressSessionSnapshot row : validEvaluations) {
            for (CriterionScore score : parseCriteria(row)) {
                if (points.containsKey(score.code()) && isScore(score.score())) {
                    points.get(score.code()).add(new CriterionPoint(
                            row.sessionId(), row.completedAt(), round(score.score())));
                }
            }
        }

        return CRITERIA.stream().map(code -> {
            List<CriterionPoint> allPoints = points.get(code);
            List<Double> values = allPoints.stream().map(CriterionPoint::score).toList();
            int start = Math.max(0, allPoints.size() - HISTORY_LIMIT);
            return new CriterionEvolution(
                    code,
                    latest(values),
                    averageDoubles(values),
                    delta(values),
                    List.copyOf(allPoints.subList(start, allPoints.size()))
            );
        }).toList();
    }

    private Insights buildInsights(
            List<ProgressSessionSnapshot> validEvaluations,
            List<ProgressSessionSnapshot> completedAnalyses) {
        Set<String> strengths = new LinkedHashSet<>();
        Set<String> improvements = new LinkedHashSet<>();
        for (int i = validEvaluations.size() - 1; i >= 0
                && (strengths.size() < RECENT_INSIGHT_LIMIT
                || improvements.size() < RECENT_INSIGHT_LIMIT); i--) {
            ProgressSessionSnapshot row = validEvaluations.get(i);
            addRecentStrings(strengths, parseStringArray(row, row.strengthsJson(), "strengths"));
            addRecentStrings(improvements,
                    parseStringArray(row, row.areasForImprovementJson(), "areasForImprovement"));
        }

        Map<String, Long> fillerTotals = new LinkedHashMap<>();
        for (ProgressSessionSnapshot row : completedAnalyses) {
            parseFillerCounts(row).forEach((name, count) ->
                    fillerTotals.merge(name, count.longValue(), Long::sum));
        }
        List<FillerCount> frequentFillers = fillerTotals.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(FREQUENT_FILLER_LIMIT)
                .map(entry -> new FillerCount(entry.getKey(), entry.getValue()))
                .toList();

        return new Insights(
                strengths.stream().limit(RECENT_INSIGHT_LIMIT).toList(),
                improvements.stream().limit(RECENT_INSIGHT_LIMIT).toList(),
                frequentFillers
        );
    }

    private void addRecentStrings(Set<String> target, List<String> values) {
        for (String value : values) {
            if (target.size() >= RECENT_INSIGHT_LIMIT) return;
            target.add(value);
        }
    }

    private List<CriterionScore> parseCriteria(ProgressSessionSnapshot row) {
        if (row.criteriaScoresJson() == null || row.criteriaScoresJson().isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(row.criteriaScoresJson());
            if (!root.isArray()) return List.of();
            Map<String, CriterionScore> result = new LinkedHashMap<>();
            for (JsonNode item : root) {
                String code = textOrNull(item.get("code"));
                if (code == null || !CRITERIA.contains(code)) continue;
                Double score = finiteNumberOrNull(item.get("score"));
                result.putIfAbsent(code, new CriterionScore(
                        code,
                        textOrNull(item.get("label")),
                        isScore(score) ? round(score) : null,
                        textOrNull(item.get("level"))
                ));
            }
            return CRITERIA.stream().filter(result::containsKey).map(result::get).toList();
        } catch (Exception ex) {
            log.warn("Ignoring invalid progress JSON [sessionId={} field=criteriaScores]", row.sessionId());
            return List.of();
        }
    }

    private List<String> parseStringArray(
            ProgressSessionSnapshot row,
            String raw,
            String field) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isArray()) return List.of();
            List<String> values = new ArrayList<>();
            for (JsonNode item : root) {
                if (item.isTextual()) {
                    String value = item.asText().trim();
                    if (!value.isEmpty()) values.add(value);
                }
            }
            return values;
        } catch (Exception ex) {
            log.warn("Ignoring invalid progress JSON [sessionId={} field={}]", row.sessionId(), field);
            return List.of();
        }
    }

    private Map<String, Integer> parseFillerCounts(ProgressSessionSnapshot row) {
        if (row.fillerCountsJson() == null || row.fillerCountsJson().isBlank()) return Map.of();
        try {
            JsonNode root = objectMapper.readTree(row.fillerCountsJson());
            if (!root.isObject()) return Map.of();
            Map<String, Integer> values = new LinkedHashMap<>();
            root.properties().forEach(entry -> {
                String name = entry.getKey().trim();
                JsonNode count = entry.getValue();
                if (!name.isEmpty() && count.isIntegralNumber()
                        && count.canConvertToInt() && count.intValue() >= 0) {
                    values.put(name, count.intValue());
                }
            });
            return values;
        } catch (Exception ex) {
            log.warn("Ignoring invalid progress JSON [sessionId={} field=fillerCounts]", row.sessionId());
            return Map.of();
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || !node.isTextual()) return null;
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static Double finiteNumberOrNull(JsonNode node) {
        if (node == null || !node.isNumber()) return null;
        double value = node.doubleValue();
        return Double.isFinite(value) ? value : null;
    }

    private boolean isScore(Double value) {
        return isNonNegativeFinite(value) && value <= 100.0;
    }

    private boolean isNonNegativeFinite(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0;
    }

    private static Integer validNonNegative(Integer value) {
        return value != null && value >= 0 ? value : null;
    }

    private static Double latest(List<Double> values) {
        return values.isEmpty() ? null : round(values.getLast());
    }

    private static Integer latestInteger(List<Integer> values) {
        return values.isEmpty() ? null : values.getLast();
    }

    private static Double averageDoubles(List<Double> values) {
        return values.isEmpty() ? null : round(values.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
    }

    private static Double averageIntegers(List<Integer> values) {
        return values.isEmpty() ? null : round(values.stream().mapToInt(Integer::intValue).average().orElseThrow());
    }

    private static Double delta(List<Double> values) {
        return values.size() < 2 ? null : round(values.getLast() - values.get(values.size() - 2));
    }

    private static Integer integerDelta(List<Integer> values) {
        return values.size() < 2 ? null : values.getLast() - values.get(values.size() - 2);
    }

    private static Double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}

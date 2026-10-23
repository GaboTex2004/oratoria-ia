package com.oratoria.backend.progress.service;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.SubscriptionSummary;
import com.oratoria.backend.session.entity.SessionStatus;
import com.oratoria.backend.training.TrainingType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProgressCalculatorTests {
    private static final Instant BASE = Instant.parse("2026-10-01T10:00:00Z");
    private static final SubscriptionSummary FREE =
            new SubscriptionSummary("FREE", 5, false, null, "NOT_DEFINED");

    private final ProgressCalculator calculator = new ProgressCalculator(new ObjectMapper());

    @Test
    void userWithoutSessionsGetsEmptyCollectionsAndNullMetrics() {
        ProgressSummaryResponse result = calculator.calculate(List.of(), FREE);

        assertThat(result.overview().totalSessions()).isZero();
        assertThat(result.overview().completedSessions()).isZero();
        assertThat(result.overview().evaluatedSessions()).isZero();
        assertThat(result.overview().totalPracticeSeconds()).isZero();
        assertThat(result.overview().latestOverallScore()).isNull();
        assertThat(result.overview().averageWordsPerMinute()).isNull();
        assertThat(result.overview().averageFillerWords()).isNull();
        assertThat(result.history()).isEmpty();
        assertThat(result.criteriaEvolution()).hasSize(4)
                .allSatisfy(item -> {
                    assertThat(item.latestValue()).isNull();
                    assertThat(item.series()).isEmpty();
                });
        assertThat(result.insights().recentStrengths()).isEmpty();
        assertThat(result.insights().frequentFillers()).isEmpty();
    }

    @Test
    void incompleteAndInsufficientSessionsNeverCreateInventedMetrics() {
        List<ProgressSessionSnapshot> rows = List.of(
                snapshot(0, SessionStatus.COMPLETED, null, null, null, null, null,
                        null, null, null, null, null, null),
                snapshot(1, SessionStatus.COMPLETED, AnalysisStatus.COMPLETED, null, null,
                        42, null, null, null, null, null, null, null),
                snapshot(2, SessionStatus.COMPLETED, AnalysisStatus.PROCESSING,
                        EvaluationStatus.PROCESSING, null, 99, 999.0, 99,
                        null, null, null, null, null),
                snapshot(3, SessionStatus.COMPLETED, AnalysisStatus.FAILED,
                        EvaluationStatus.COMPLETED, false, 75, null, null,
                        criteria(90), "[\"No debe contarse\"]", "[]", null, null),
                snapshot(4, SessionStatus.COMPLETED, AnalysisStatus.PENDING,
                        EvaluationStatus.PENDING, null, null, null, null,
                        null, null, null, null, null)
        );

        ProgressSummaryResponse result = calculator.calculate(rows, FREE);

        assertThat(result.overview().totalSessions()).isEqualTo(5);
        assertThat(result.overview().completedSessions()).isEqualTo(5);
        assertThat(result.overview().evaluatedSessions()).isEqualTo(1);
        assertThat(result.overview().latestOverallScore()).isNull();
        assertThat(result.overview().latestWordsPerMinute()).isNull();
        assertThat(result.overview().latestFillerWords()).isNull();
        assertThat(result.history()).allSatisfy(item -> assertThat(item.overallScore()).isNull());
        assertThat(result.insights().recentStrengths()).isEmpty();
    }

    @Test
    void calculatesExactAveragesDeltasCriteriaInsightsAndFillers() {
        List<ProgressSessionSnapshot> rows = List.of(
                snapshot(0, SessionStatus.COMPLETED, AnalysisStatus.COMPLETED,
                        EvaluationStatus.COMPLETED, true, 70, 120.0, 5,
                        criteria(70), "[\"Estructura clara\"]", "[\"Mejorar cierre\"]",
                        "{\"eh\":3,\"este\":2}", 30),
                snapshot(1, SessionStatus.COMPLETED, AnalysisStatus.COMPLETED,
                        EvaluationStatus.COMPLETED, true, 80, 140.0, 2,
                        criteria(80), "[\"Mayor fluidez\"]", "[\"Añadir evidencia\"]",
                        "{\"eh\":1}", 30),
                snapshot(2, SessionStatus.COMPLETED, AnalysisStatus.COMPLETED,
                        EvaluationStatus.COMPLETED, false, 99, null, null,
                        criteria(99), "[\"Ignorada\"]", "[\"Ignorada\"]", "{}", 30)
        );

        ProgressSummaryResponse result = calculator.calculate(rows, FREE);

        assertThat(result.overview().totalSessions()).isEqualTo(3);
        assertThat(result.overview().evaluatedSessions()).isEqualTo(3);
        assertThat(result.overview().totalPracticeSeconds()).isEqualTo(90);
        assertThat(result.overview().latestOverallScore()).isEqualTo(80.0);
        assertThat(result.overview().averageOverallScore()).isEqualTo(75.0);
        assertThat(result.overview().scoreDelta()).isEqualTo(10.0);
        assertThat(result.overview().latestWordsPerMinute()).isEqualTo(140.0);
        assertThat(result.overview().averageWordsPerMinute()).isEqualTo(130.0);
        assertThat(result.overview().latestFillerWords()).isEqualTo(2);
        assertThat(result.overview().averageFillerWords()).isEqualTo(3.5);
        assertThat(result.overview().fillerDelta()).isEqualTo(-3);

        assertThat(result.criteriaEvolution()).allSatisfy(item -> {
            assertThat(item.latestValue()).isEqualTo(80.0);
            assertThat(item.averageValue()).isEqualTo(75.0);
            assertThat(item.delta()).isEqualTo(10.0);
            assertThat(item.series()).hasSize(2);
        });
        assertThat(result.insights().recentStrengths())
                .containsExactly("Mayor fluidez", "Estructura clara");
        assertThat(result.insights().recentAreasForImprovement())
                .containsExactly("Añadir evidencia", "Mejorar cierre");
        assertThat(result.insights().frequentFillers())
                .extracting(item -> item.filler() + ":" + item.count())
                .containsExactly("eh:4", "este:2");
        assertThat(result.history().getLast().overallScore()).isNull();
    }

    @Test
    void invalidOrMissingJsonIsIgnoredWithoutLosingScalarProgress() {
        ProgressSessionSnapshot row = snapshot(
                0, SessionStatus.COMPLETED, AnalysisStatus.COMPLETED,
                EvaluationStatus.COMPLETED, true, 65, 110.0, 1,
                "not-json", "{wrong", null, "[1,2]", 20);

        ProgressSummaryResponse result = calculator.calculate(List.of(row), FREE);

        assertThat(result.overview().latestOverallScore()).isEqualTo(65.0);
        assertThat(result.overview().latestWordsPerMinute()).isEqualTo(110.0);
        assertThat(result.history().getFirst().criteriaScores()).isEmpty();
        assertThat(result.criteriaEvolution()).allSatisfy(item -> item.series().isEmpty());
        assertThat(result.insights().recentStrengths()).isEmpty();
        assertThat(result.insights().recentAreasForImprovement()).isEmpty();
        assertThat(result.insights().frequentFillers()).isEmpty();
    }

    @Test
    void limitsOnlyChartHistoryWhileKeepingGlobalTotals() {
        List<ProgressSessionSnapshot> rows = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            rows.add(snapshot(i, SessionStatus.COMPLETED, AnalysisStatus.COMPLETED,
                    EvaluationStatus.COMPLETED, true, 50 + (i % 10), 100.0, 0,
                    criteria(50), "[]", "[]", "{}", 10));
        }

        ProgressSummaryResponse result = calculator.calculate(rows, FREE);

        assertThat(result.overview().totalSessions()).isEqualTo(105);
        assertThat(result.overview().totalPracticeSeconds()).isEqualTo(1050);
        assertThat(result.history()).hasSize(100);
        assertThat(result.historyTruncated()).isTrue();
        assertThat(result.historyLimit()).isEqualTo(100);
        assertThat(result.history().getFirst().createdAt()).isEqualTo(BASE.plus(5, ChronoUnit.DAYS));
        assertThat(result.criteriaEvolution())
                .allSatisfy(item -> assertThat(item.series()).hasSize(100));
    }

    private static ProgressSessionSnapshot snapshot(
            int day,
            SessionStatus sessionStatus,
            AnalysisStatus analysisStatus,
            EvaluationStatus evaluationStatus,
            Boolean sufficientEvidence,
            Integer overallScore,
            Double wordsPerMinute,
            Integer fillerWords,
            String criteriaJson,
            String strengthsJson,
            String improvementsJson,
            String fillerJson,
            Integer durationSeconds) {
        Instant createdAt = BASE.plus(day, ChronoUnit.DAYS);
        return new ProgressSessionSnapshot(
                UUID.nameUUIDFromBytes(("session-" + day).getBytes()),
                "Ejercicio " + day,
                TrainingType.PRESENTATION,
                createdAt,
                sessionStatus == SessionStatus.COMPLETED ? createdAt.plusSeconds(60) : null,
                durationSeconds,
                sessionStatus,
                analysisStatus,
                analysisStatus == AnalysisStatus.COMPLETED ? 100 : null,
                wordsPerMinute,
                wordsPerMinute,
                fillerWords,
                fillerJson,
                analysisStatus == AnalysisStatus.COMPLETED ? 1 : null,
                analysisStatus == AnalysisStatus.COMPLETED ? 0.8 : null,
                evaluationStatus,
                sufficientEvidence,
                overallScore == null ? null : overallScore.doubleValue(),
                criteriaJson,
                strengthsJson,
                improvementsJson
        );
    }

    private static String criteria(int score) {
        return """
                [
                  {"code":"CLARITY","label":"Claridad","score":%d,"level":"COMPETENTE"},
                  {"code":"COHERENCE","label":"Coherencia","score":%d,"level":"COMPETENTE"},
                  {"code":"ORGANIZATION","label":"Organización","score":%d,"level":"COMPETENTE"},
                  {"code":"ARGUMENTATION","label":"Argumentación","score":%d,"level":"COMPETENTE"}
                ]
                """.formatted(score, score, score, score);
    }
}

package com.oratoria.backend.progress.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.oratoria.backend.exercise.dto.ExerciseResponse;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.CriterionScore;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.HistoryItem;
import com.oratoria.backend.progress.service.AdaptiveRecommendationEngine.Access;
import com.oratoria.backend.training.TrainingType;
import com.oratoria.backend.user.preferences.TrainingPreferences;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdaptiveRecommendationEngineTests {
    static final Map<String, String> NAMES = Map.of("CLARIDAD", "Claridad", "RITMO", "Ritmo", "ESTRUCTURA", "Estructura",
            "FLUIDEZ", "Fluidez", "ARGUMENTACION", "Argumentación", "IMPROVISACION", "Improvisación", "ESTILOS", "Estilos");
    static final Access FULL = new Access(true, true, true);
    static final Access FREE = new Access(false, false, false);

    static ExerciseResponse exercise(String title, String category, String level, TrainingType type, String style) {
        return new ExerciseResponse(UUID.randomUUID(), title.toUpperCase().replace(' ', '_'), title, "d", "p", type, 60,
                category, level, "o", List.of(), style);
    }

    static HistoryItem evaluation(int daysAgo, String title, double overall, Map<String, Double> criteria) {
        List<CriterionScore> scores = new ArrayList<>();
        criteria.forEach((code, score) -> scores.add(new CriterionScore(code, code, score, "x")));
        Instant at = Instant.now().minusSeconds(daysAgo * 86400L);
        return new HistoryItem(UUID.randomUUID(), title, TrainingType.PRESENTATION, at, at, 60, null, null, null,
                true, overall, 100, 120.0, 120.0, 1, 1, 0.9, scores);
    }

    static Map<String, Double> criteria(double clarity, double others) {
        return Map.of("CLARITY", clarity, "COHERENCE", others, "ORGANIZATION", others, "ARGUMENTATION", others);
    }

    static TrainingPreferences prefs(String goal, String level, List<String> skills, String style) {
        TrainingPreferences prefs = new TrainingPreferences(UUID.randomUUID());
        prefs.update(goal, level, skills, List.of(), style, 3);
        return prefs;
    }

    final List<ExerciseResponse> catalog = List.of(
            exercise("Estructura base", "ESTRUCTURA", "BASIC", TrainingType.PRESENTATION, null),
            exercise("Claridad uno", "CLARIDAD", "BASIC", TrainingType.PRESENTATION, null),
            exercise("Claridad dos", "CLARIDAD", "INTERMEDIATE", TrainingType.PRESENTATION, null),
            exercise("Ritmo intermedio", "RITMO", "INTERMEDIATE", TrainingType.PRESENTATION, null),
            exercise("Ritmo base", "RITMO", "BASIC", TrainingType.PRESENTATION, null),
            exercise("Tema inesperado", "IMPROVISACION", "INTERMEDIATE", TrainingType.IMPROVISATION, null),
            exercise("Historia", "ESTILOS", "INTERMEDIATE", TrainingType.PRESENTATION, "NARRATIVO"));

    @Test void recurringWeaknessDrivesTheRecommendation() {
        var history = List.of(evaluation(1, "a", 70, criteria(50, 80)), evaluation(2, "b", 72, criteria(55, 82)),
                evaluation(3, "c", 71, criteria(52, 81)));
        var result = AdaptiveRecommendationEngine.recommend(history, prefs(null, "BASIC", List.of(), null), catalog, NAMES, FULL);
        assertThat(result.category()).isEqualTo("CLARIDAD");
        assertThat(result.focusAreas().getFirst().code()).isEqualTo("CLARITY");
        assertThat(result.focusAreas().getFirst().lowScores()).isEqualTo(3);
        assertThat(result.basedOn()).anyMatch(text -> text.contains("recurrente"));
        assertThat(result.exercises().getFirst().category()).isEqualTo("CLARIDAD");
        assertThat(result.adaptive()).isTrue();
    }

    @Test void withoutHistoryThePrioritySkillAndDeclaredLevelAreUsed() {
        var result = AdaptiveRecommendationEngine.recommend(List.of(), prefs(null, "INTERMEDIATE", List.of("RITMO"), null), catalog, NAMES, FULL);
        assertThat(result.category()).isEqualTo("RITMO");
        assertThat(result.suggestedDifficulty()).isEqualTo("INTERMEDIATE");
        assertThat(result.exercises().getFirst().title()).isEqualTo("Ritmo intermedio");
    }

    @Test void theGoalPrioritizesItsTrainingType() {
        var interviewCatalog = List.of(
                exercise("Presentación", "ESTRUCTURA", "BASIC", TrainingType.PRESENTATION, null),
                exercise("Entrevista", "ESTRUCTURA", "BASIC", TrainingType.INTERVIEW, null));
        var result = AdaptiveRecommendationEngine.recommend(List.of(), prefs("JOB_INTERVIEWS", "BASIC", List.of(), null), interviewCatalog, NAMES, FULL);
        assertThat(result.exercises().getFirst().title()).isEqualTo("Entrevista");
        assertThat(result.basedOn()).anyMatch(text -> text.contains("entrevistas"));
    }

    @Test void recentlyPracticedExercisesGoLast() {
        var history = List.of(evaluation(1, "Claridad uno", 50, criteria(40, 60)));
        var result = AdaptiveRecommendationEngine.recommend(history, prefs(null, "BASIC", List.of(), null), catalog, NAMES, FULL);
        assertThat(result.exercises().getFirst().title()).isEqualTo("Claridad dos");
    }

    @Test void lockedFeaturesAreNeverRecommended() {
        var result = AdaptiveRecommendationEngine.recommend(List.of(), prefs("SPONTANEITY", "INTERMEDIATE", List.of("IMPROVISACION"), "NARRATIVO"),
                catalog, NAMES, new Access(true, false, false));
        assertThat(result.exercises()).noneMatch(item -> item.trainingType() == TrainingType.IMPROVISATION);
        assertThat(result.styleExercise()).isNull();
    }

    @Test void thePreferredStyleAddsAStyleExercise() {
        var result = AdaptiveRecommendationEngine.recommend(List.of(), prefs(null, "BASIC", List.of(), "NARRATIVO"), catalog, NAMES, FULL);
        assertThat(result.styleExercise()).isNotNull();
        assertThat(result.styleExercise().communicationStyle()).isEqualTo("NARRATIVO");
        assertThat(result.exercises()).noneMatch(item -> item.communicationStyle() != null);
    }

    @Test void theBasicModeGivesOneExerciseWithoutPersonalization() {
        var result = AdaptiveRecommendationEngine.recommend(List.of(), prefs("JOB_INTERVIEWS", "ADVANCED", List.of("RITMO"), "NARRATIVO"), catalog, NAMES, FREE);
        assertThat(result.adaptive()).isFalse();
        assertThat(result.category()).isEqualTo("ESTRUCTURA");
        assertThat(result.suggestedDifficulty()).isEqualTo("BASIC");
        assertThat(result.exercises()).hasSize(1);
        assertThat(result.styleExercise()).isNull();
        assertThat(result.upgradeHint()).isNotBlank();
    }

    @Test void aStrongStreakRaisesTheDifficulty() {
        var history = List.of(evaluation(1, "a", 85, criteria(80, 90)), evaluation(2, "b", 82, criteria(81, 90)),
                evaluation(3, "c", 90, criteria(85, 90)));
        var result = AdaptiveRecommendationEngine.recommend(history, prefs(null, "BASIC", List.of(), null), catalog, NAMES, FULL);
        assertThat(result.suggestedDifficulty()).isEqualTo("ADVANCED");
    }
}

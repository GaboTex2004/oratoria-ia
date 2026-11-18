package com.oratoria.backend.progress.service;

import com.oratoria.backend.exercise.dto.ExerciseResponse;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.CriterionScore;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.HistoryItem;
import com.oratoria.backend.training.TrainingType;
import com.oratoria.backend.user.preferences.TrainingGoal;
import com.oratoria.backend.user.preferences.TrainingPreferences;
import java.util.*;

/**
 * Adaptive training rules (CU-20). Pure function of the evaluation history, the user's profile and the catalog,
 * so the same inputs always give the same recommendation and every rule can be tested in isolation.
 */
public final class AdaptiveRecommendationEngine {
    static final String RULE_VERSION = "adaptive-rules-v2";
    static final int EVALUATION_WINDOW = 5;
    static final double LOW_SCORE = 60;
    static final Map<String, String> CRITERION_CATEGORY = Map.of("CLARITY", "CLARIDAD", "ARGUMENTATION", "ARGUMENTACION",
            "ORGANIZATION", "ESTRUCTURA", "COHERENCE", "FLUIDEZ");
    static final Map<String, String> CRITERION_LABELS = Map.of("CLARITY", "Claridad", "COHERENCE", "Coherencia",
            "ORGANIZATION", "Organización", "ARGUMENTATION", "Argumentación");
    static final String LEVEL_RULE = "Nivel BASIC sin historial o con último puntaje menor a 55; ADVANCED tras tres puntajes de 80 o más; "
            + "INTERMEDIATE en otro caso. Sin evaluaciones se usa el nivel declarado en tu perfil.";
    static final String UPGRADE_HINT = "Con Premium la recomendación usa tus últimas evaluaciones, tus objetivos, tus habilidades prioritarias y tu estilo, y te propone tres ejercicios.";

    private AdaptiveRecommendationEngine() {}

    public static Recommendation recommend(List<HistoryItem> history, TrainingPreferences preferences,
                                           List<ExerciseResponse> catalog, Map<String, String> categoryNames, Access access) {
        List<HistoryItem> evaluated = history.stream()
                .filter(item -> Boolean.TRUE.equals(item.sufficientEvidence()) && item.criteriaScores() != null && !item.criteriaScores().isEmpty())
                .sorted(Comparator.comparing(HistoryItem::createdAt).reversed())
                .limit(access.adaptive() ? EVALUATION_WINDOW : 1).toList();
        List<FocusArea> focusAreas = focusAreas(evaluated);
        List<String> basedOn = new ArrayList<>();
        List<ExerciseResponse> available = catalog.stream()
                .filter(item -> access.improvisation() || item.trainingType() != TrainingType.IMPROVISATION)
                .filter(item -> item.communicationStyle() == null).toList();

        if (!access.adaptive()) {
            String category = focusAreas.isEmpty() ? "ESTRUCTURA" : CRITERION_CATEGORY.get(focusAreas.getFirst().code());
            String level = level(history, "BASIC");
            basedOn.add(focusAreas.isEmpty() ? "Todavía no hay evaluaciones suficientes; comienza con una práctica de estructura."
                    : "El criterio con menor puntuación en tu última evaluación es " + focusAreas.getFirst().label()
                    + " (" + Math.round(focusAreas.getFirst().average()) + "/100).");
            List<ExerciseResponse> pick = available.stream().filter(item -> item.category().equals(category))
                    .sorted(Comparator.comparing(item -> !item.difficulty().equals(level))).limit(1).toList();
            return new Recommendation(RULE_VERSION, false, category, name(categoryNames, category), level, basedOn.getFirst(),
                    LEVEL_RULE, basedOn, focusAreas, pick, null, UPGRADE_HINT);
        }

        String category;
        if (!focusAreas.isEmpty()) {
            FocusArea weakest = focusAreas.getFirst();
            category = CRITERION_CATEGORY.getOrDefault(weakest.code(), "FLUIDEZ");
            basedOn.add("Tu criterio más débil en las últimas " + evaluated.size() + " evaluaciones es " + weakest.label()
                    + " (" + Math.round(weakest.average()) + "/100).");
            focusAreas.stream().filter(area -> area.lowScores() >= 2).forEach(area -> basedOn.add(area.label()
                    + " quedó por debajo de " + Math.round(LOW_SCORE) + " en " + area.lowScores() + " evaluaciones: es una dificultad recurrente."));
        } else if (!preferences.getPrioritySkills().isEmpty()) {
            category = preferences.getPrioritySkills().getFirst();
            basedOn.add("Todavía no hay evaluaciones suficientes; empezamos por tu habilidad prioritaria y tu nivel declarado.");
        } else {
            category = "ESTRUCTURA";
            basedOn.add("Todavía no hay evaluaciones suficientes; empezamos por la estructura del discurso y tu nivel declarado.");
        }
        String level = level(history, preferences.getExperienceLevel());

        Set<TrainingType> preferredTypes = new HashSet<>();
        TrainingGoal goal = preferences.getGoal() == null ? null : TrainingGoal.valueOf(preferences.getGoal());
        if (goal != null) {
            preferredTypes.addAll(goal.trainingTypes());
            basedOn.add("Tu objetivo es «" + goal.label() + "»: priorizamos ejercicios de ese tipo.");
        }
        preferences.getPreferredTrainingTypes().forEach(type -> preferredTypes.add(TrainingType.valueOf(type)));
        List<String> skills = preferences.getPrioritySkills();
        if (!skills.isEmpty()) {
            basedOn.add("Marcaste como prioritarias: " + String.join(", ", skills.stream().map(code -> name(categoryNames, code)).toList()) + ".");
        }
        Set<String> recentTitles = new HashSet<>();
        history.stream().sorted(Comparator.comparing(HistoryItem::createdAt).reversed()).limit(3)
                .map(HistoryItem::exerciseTitle).filter(Objects::nonNull).forEach(recentTitles::add);

        Map<ExerciseResponse, Integer> scores = new LinkedHashMap<>();
        for (ExerciseResponse item : available) {
            int score = (item.category().equals(category) ? 4 : 0) + (item.difficulty().equals(level) ? 2 : 0)
                    + (preferredTypes.contains(item.trainingType()) ? 1 : 0) + (skills.contains(item.category()) ? 1 : 0)
                    - (recentTitles.contains(item.title()) ? 3 : 0);
            scores.put(item, score);
        }
        List<ExerciseResponse> exercises = scores.entrySet().stream()
                .sorted(Map.Entry.<ExerciseResponse, Integer>comparingByValue().reversed())
                .limit(3).map(Map.Entry::getKey).toList();
        if (!recentTitles.isEmpty()) basedOn.add("Dejamos al final los ejercicios de tus últimas prácticas para variar el entrenamiento.");

        ExerciseResponse styleExercise = null;
        if (access.styles() && preferences.getPreferredStyle() != null) {
            String style = preferences.getPreferredStyle();
            styleExercise = catalog.stream().filter(item -> style.equals(item.communicationStyle()))
                    .min(Comparator.comparing((ExerciseResponse item) -> recentTitles.contains(item.title()))
                            .thenComparing(item -> !item.difficulty().equals(level))).orElse(null);
            if (styleExercise != null) basedOn.add("Elegiste un estilo de comunicación: te sugerimos «" + styleExercise.title() + "» para practicarlo.");
        }
        return new Recommendation(RULE_VERSION, true, category, name(categoryNames, category), level, basedOn.getFirst(),
                LEVEL_RULE, basedOn, focusAreas, exercises, styleExercise, null);
    }

    private static List<FocusArea> focusAreas(List<HistoryItem> evaluated) {
        Map<String, List<Double>> byCriterion = new LinkedHashMap<>();
        for (HistoryItem item : evaluated) {
            for (CriterionScore score : item.criteriaScores()) {
                if (score.score() != null) byCriterion.computeIfAbsent(score.code(), key -> new ArrayList<>()).add(score.score());
            }
        }
        return byCriterion.entrySet().stream().map(entry -> new FocusArea(entry.getKey(),
                        CRITERION_LABELS.getOrDefault(entry.getKey(), entry.getKey()),
                        Math.round(entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0,
                        (int) entry.getValue().stream().filter(score -> score < LOW_SCORE).count(), entry.getValue().size()))
                .sorted(Comparator.comparingDouble(FocusArea::average)).toList();
    }

    private static String level(List<HistoryItem> history, String declared) {
        List<Double> recent = history.stream()
                .filter(item -> Boolean.TRUE.equals(item.sufficientEvidence()) && item.overallScore() != null)
                .sorted(Comparator.comparing(HistoryItem::createdAt).reversed())
                .limit(3).map(HistoryItem::overallScore).toList();
        if (recent.isEmpty()) return declared == null ? "BASIC" : declared;
        if (recent.getFirst() < 55) return "BASIC";
        return recent.size() == 3 && recent.stream().allMatch(score -> score >= 80) ? "ADVANCED" : "INTERMEDIATE";
    }

    private static String name(Map<String, String> names, String code) { return names.getOrDefault(code, code); }

    public record Access(boolean adaptive, boolean improvisation, boolean styles) {}

    public record FocusArea(String code, String label, double average, int lowScores, int evaluations) {}

    public record Recommendation(String ruleVersion, boolean adaptive, String category, String categoryName,
                                 String suggestedDifficulty, String reason, String ruleDescription, List<String> basedOn,
                                 List<FocusArea> focusAreas, List<ExerciseResponse> exercises, ExerciseResponse styleExercise,
                                 String upgradeHint) {}
}

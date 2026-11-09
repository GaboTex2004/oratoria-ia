package com.oratoria.backend.progress.service;

import com.oratoria.backend.exercise.dto.ExerciseResponse;
import com.oratoria.backend.exercise.service.ExerciseService;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
    private final ProgressService progress;
    private final ExerciseService exercises;
    public RecommendationService(ProgressService progress, ExerciseService exercises) { this.progress = progress; this.exercises = exercises; }

    public Recommendation recommend(String email) {
        ProgressSummaryResponse summary = progress.getSummary(email);
        var weak = summary.criteriaEvolution().stream().filter(item -> item.latestValue() != null)
                .min(Comparator.comparingDouble(item -> item.latestValue()));
        String category = weak.map(item -> switch (item.code()) {
            case "CLARITY" -> "CLARIDAD";
            case "ARGUMENTATION" -> "ARGUMENTACION";
            case "ORGANIZATION" -> "ESTRUCTURA";
            default -> "FLUIDEZ";
        }).orElse("ESTRUCTURA");
        List<Double> recent = summary.history().stream()
                .filter(item -> Boolean.TRUE.equals(item.sufficientEvidence()) && item.overallScore() != null)
                .sorted(Comparator.comparing(ProgressSummaryResponse.HistoryItem::createdAt).reversed())
                .limit(3).map(ProgressSummaryResponse.HistoryItem::overallScore).toList();
        String level = recent.isEmpty() || recent.getFirst() < 55 ? "BASIC"
                : recent.size() == 3 && recent.stream().allMatch(score -> score >= 80) ? "ADVANCED" : "INTERMEDIATE";
        List<ExerciseResponse> catalog = exercises.listExercises(null);
        List<ExerciseResponse> selected = catalog.stream().filter(item -> item.category().equals(category))
                .sorted(Comparator.comparing(item -> !item.difficulty().equals(level))).limit(3).toList();
        String reason = weak.map(item -> "El criterio con menor puntuación reciente es " + item.code()
                + " (" + item.latestValue() + ").").orElse("Todavía no hay evaluaciones suficientes; comienza con una práctica de estructura.");
        return new Recommendation("adaptive-rules-v1", category, level, reason,
                "BASIC sin historial o último score <55; ADVANCED tras tres scores >=80; INTERMEDIATE en otro caso. Se prioriza el nivel recomendado si el catálogo dispone de él.", selected);
    }

    public record Recommendation(String ruleVersion, String category, String suggestedDifficulty,
            String reason, String ruleDescription, List<ExerciseResponse> exercises) {}
}

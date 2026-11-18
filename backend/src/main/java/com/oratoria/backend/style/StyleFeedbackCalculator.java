package com.oratoria.backend.style;

import com.oratoria.backend.style.CommunicationStyleCatalog.CommunicationStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Re-reads a session's evaluation through the lens of a communication style: weighted criteria plus pace and fillers. */
public final class StyleFeedbackCalculator {
    static final Map<String, String> CRITERIA_LABELS = Map.of("CLARITY", "Claridad", "COHERENCE", "Coherencia",
            "ORGANIZATION", "Organización", "ARGUMENTATION", "Argumentación");
    private static final double FILLERS_PER_MINUTE_LIMIT = 4.0;

    private StyleFeedbackCalculator() {}

    public static Feedback calculate(CommunicationStyle style, Map<String, Double> criteria, Double wordsPerMinute,
                                     Integer fillerWords, Integer durationSeconds) {
        double weighted = 0, totalWeight = 0;
        for (var weight : style.weights().entrySet()) {
            Double score = criteria.get(weight.getKey());
            if (score == null) continue;
            weighted += score * weight.getValue();
            totalWeight += weight.getValue();
        }
        Double score = totalWeight == 0 ? null : Math.round(weighted / totalWeight * 10) / 10.0;

        List<String> observations = new ArrayList<>();
        List<String> tips = new ArrayList<>();
        var rated = style.weights().entrySet().stream().filter(entry -> criteria.get(entry.getKey()) != null)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).toList();
        if (!rated.isEmpty()) {
            var key = rated.getFirst().getKey();
            observations.add("En el estilo " + style.name().toLowerCase() + " pesa más " + CRITERIA_LABELS.get(key)
                    + ": obtuviste " + Math.round(criteria.get(key)) + "/100.");
            var weakest = rated.stream().min(Comparator.comparingDouble(entry -> criteria.get(entry.getKey()))).orElseThrow();
            if (criteria.get(weakest.getKey()) < 70) {
                tips.add("Refuerza " + CRITERIA_LABELS.get(weakest.getKey()).toLowerCase() + " ("
                        + Math.round(criteria.get(weakest.getKey())) + "/100): " + style.guidance());
            }
        }
        if (wordsPerMinute != null) {
            long pace = Math.round(wordsPerMinute);
            String range = style.minWordsPerMinute() + "–" + style.maxWordsPerMinute() + " ppm";
            if (pace < style.minWordsPerMinute()) {
                tips.add("Tu ritmo (" + pace + " ppm) es más lento de lo que pide el estilo " + style.name().toLowerCase() + " (" + range + ").");
            } else if (pace > style.maxWordsPerMinute()) {
                tips.add("Tu ritmo (" + pace + " ppm) es más rápido de lo que pide el estilo " + style.name().toLowerCase() + " (" + range + ").");
            } else {
                observations.add("Tu ritmo (" + pace + " ppm) encaja con el estilo (" + range + ").");
            }
        }
        if (fillerWords != null && durationSeconds != null && durationSeconds > 0
                && fillerWords / (durationSeconds / 60.0) > FILLERS_PER_MINUTE_LIMIT) {
            tips.add("Usaste " + fillerWords + " muletillas; reemplázalas por pausas breves para sostener el estilo.");
        }
        if (tips.isEmpty()) tips.add("Mantén estos rasgos y prueba un ejercicio de nivel superior del mismo estilo.");
        return new Feedback(score, observations, tips);
    }

    public record Feedback(Double score, List<String> observations, List<String> tips) {}
}

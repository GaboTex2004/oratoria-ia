package com.oratoria.backend.evaluation.dto;

import java.util.List;

public record CriterionScoreDto(
        String code,
        String label,
        double weight,
        Double score,
        String level,
        String justification,
        List<String> evidenceQuotes,
        String judgmentType) {
}

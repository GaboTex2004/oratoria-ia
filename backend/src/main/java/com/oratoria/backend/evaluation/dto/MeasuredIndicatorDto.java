package com.oratoria.backend.evaluation.dto;

public record MeasuredIndicatorDto(
        String code,
        String label,
        Double value,
        String unit,
        String interpretation,
        String source) {
}

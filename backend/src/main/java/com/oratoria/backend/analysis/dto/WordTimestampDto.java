package com.oratoria.backend.analysis.dto;

public record WordTimestampDto(
        String word,
        double startSeconds,
        double endSeconds) {
}

package com.oratoria.backend.analysis.dto;

public record TranscriptSegmentDto(
        int index,
        double startSeconds,
        double endSeconds,
        String text) {
}

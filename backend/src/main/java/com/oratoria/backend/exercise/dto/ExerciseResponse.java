package com.oratoria.backend.exercise.dto;

import com.oratoria.backend.training.TrainingType;
import java.util.UUID;

public record ExerciseResponse(
        UUID id,
        String code,
        String title,
        String description,
        String prompt,
        TrainingType trainingType,
        int recommendedDurationSeconds) {
}

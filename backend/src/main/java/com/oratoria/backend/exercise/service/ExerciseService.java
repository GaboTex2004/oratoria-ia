package com.oratoria.backend.exercise.service;

import com.oratoria.backend.exercise.dto.ExerciseResponse;
import com.oratoria.backend.exercise.entity.Exercise;
import com.oratoria.backend.exercise.repository.ExerciseRepository;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import com.oratoria.backend.training.TrainingType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExerciseService {
    private final ExerciseRepository exercises;

    public ExerciseService(ExerciseRepository exercises) {
        this.exercises = exercises;
    }

    public List<ExerciseResponse> listExercises(TrainingType trainingType) {
        List<Exercise> items = trainingType == null
                ? exercises.findByActiveTrueOrderByCreatedAtAsc()
                : exercises.findByTrainingTypeAndActiveTrueOrderByCreatedAtAsc(trainingType);
        return items.stream().map(this::toResponse).toList();
    }

    public ExerciseResponse getExercise(UUID id) {
        return toResponse(findActiveEntity(id));
    }

    public Exercise findActiveEntity(UUID id) {
        return exercises.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("El ejercicio solicitado no existe o no está disponible."));
    }

    private ExerciseResponse toResponse(Exercise exercise) {
        return new ExerciseResponse(
                exercise.getId(),
                exercise.getCode(),
                exercise.getTitle(),
                exercise.getDescription(),
                exercise.getPrompt(),
                exercise.getTrainingType(),
                exercise.getRecommendedDurationSeconds()
        );
    }
}

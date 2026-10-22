package com.oratoria.backend.exercise.controller;

import com.oratoria.backend.exercise.dto.ExerciseResponse;
import com.oratoria.backend.exercise.service.ExerciseService;
import com.oratoria.backend.training.TrainingType;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {
    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping
    List<ExerciseResponse> list(@RequestParam(required = false) TrainingType trainingType) {
        return exerciseService.listExercises(trainingType);
    }

    @GetMapping("/{id}")
    ExerciseResponse getById(@PathVariable UUID id) {
        return exerciseService.getExercise(id);
    }
}

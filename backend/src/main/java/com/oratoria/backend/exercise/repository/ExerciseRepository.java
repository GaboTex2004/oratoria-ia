package com.oratoria.backend.exercise.repository;

import com.oratoria.backend.exercise.entity.Exercise;
import com.oratoria.backend.training.TrainingType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {
    List<Exercise> findByActiveTrueOrderByCreatedAtAsc();
    List<Exercise> findByTrainingTypeAndActiveTrueOrderByCreatedAtAsc(TrainingType trainingType);
    Optional<Exercise> findByIdAndActiveTrue(UUID id);
    Optional<Exercise> findByCode(String code);
}

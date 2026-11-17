package com.oratoria.backend.admin.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseCategoryRepository extends JpaRepository<ExerciseCategory, UUID> {
    Optional<ExerciseCategory> findByCode(String code);
    List<ExerciseCategory> findAllByOrderByNameAsc();
}

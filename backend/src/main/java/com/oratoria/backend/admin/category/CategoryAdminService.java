package com.oratoria.backend.admin.category;

import com.oratoria.backend.exercise.repository.ExerciseRepository;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryAdminService {
    private final ExerciseCategoryRepository categories;
    private final ExerciseRepository exercises;

    public CategoryAdminService(ExerciseCategoryRepository categories, ExerciseRepository exercises) {
        this.categories = categories; this.exercises = exercises;
    }

    @Transactional(readOnly = true)
    public List<CategoryRow> list() {
        return categories.findAllByOrderByNameAsc().stream().map(category -> new CategoryRow(category.getId(),
                category.getCode(), category.getName(), category.getDescription(), category.isActive(),
                exercises.countByCategory(category.getCode()))).toList();
    }

    @Transactional
    public void create(CategoryAdminController.CategoryUpdate update) {
        if (categories.findByCode(update.code()).isPresent()) throw new BadRequestException("El código de categoría ya existe.");
        categories.save(new ExerciseCategory(update.code(), update.name().trim(), blankToNull(update.description()), update.active()));
    }

    @Transactional
    public void update(UUID id, CategoryAdminController.CategoryUpdate update) {
        ExerciseCategory category = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada."));
        category.update(update.name().trim(), blankToNull(update.description()), update.active());
    }

    @Transactional(readOnly = true)
    public void requireActive(String code) {
        if (!categories.findByCode(code).map(ExerciseCategory::isActive).orElse(false)) {
            throw new BadRequestException("Categoría inexistente o inactiva.");
        }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record CategoryRow(UUID id, String code, String name, String description, boolean active, long exercises) {}
}

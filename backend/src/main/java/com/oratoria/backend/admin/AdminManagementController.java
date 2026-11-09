package com.oratoria.backend.admin;

import com.oratoria.backend.subscription.entity.PlanCode;
import com.oratoria.backend.training.TrainingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminManagementController {
    private final AdminManagementService service;
    public AdminManagementController(AdminManagementService service) { this.service = service; }
    @GetMapping("/overview") Map<String, Object> overview() { return service.overview(); }
    @GetMapping("/users") List<AdminManagementService.UserRow> users(@RequestParam(defaultValue = "0") int page) { return service.users(page); }
    @PutMapping("/users/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void user(@PathVariable UUID id, @Valid @RequestBody UserUpdate update) { service.updateUser(id, update); }
    @GetMapping("/exercises") List<AdminManagementService.ExerciseRow> exercises() { return service.exercises(); }
    @PostMapping("/exercises") @ResponseStatus(HttpStatus.CREATED)
    void create(@Valid @RequestBody ExerciseUpdate update) { service.saveExercise(null, update); }
    @PutMapping("/exercises/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void exercise(@PathVariable UUID id, @Valid @RequestBody ExerciseUpdate update) { service.saveExercise(id, update); }

    public record UserUpdate(boolean enabled, @NotNull PlanCode plan) {}
    @PostMapping("/users/{id}/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    void password(@PathVariable UUID id, @Valid @RequestBody PasswordReset request) { service.resetPassword(id, request.newPassword()); }
    public record PasswordReset(@NotBlank @Size(min = 12, max = 72) String newPassword) {
        @Override public String toString() { return "PasswordReset[password=***]"; }
    }
    public record ExerciseUpdate(@NotBlank @Pattern(regexp = "[A-Z0-9_]{3,60}") String code,
            @NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 400) String description,
            @NotBlank @Size(max = 1000) String prompt, @NotBlank @Size(max = 400) String objective,
            @NotBlank @Pattern(regexp = "CLARIDAD|RITMO|ENTONACION|FLUIDEZ|ESTRUCTURA|ARGUMENTACION|IMPROVISACION|NO_VERBAL|ESTILOS") String category,
            @NotBlank @Pattern(regexp = "BASIC|INTERMEDIATE|ADVANCED") String difficulty,
            @Min(15) @Max(300) int duration, @NotNull TrainingType trainingType, boolean active) {}
}

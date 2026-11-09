package com.oratoria.backend.admin;

import com.oratoria.backend.exercise.entity.Exercise;
import com.oratoria.backend.exercise.repository.ExerciseRepository;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import com.oratoria.backend.subscription.entity.*;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminManagementService {
    private final UserAccountRepository users;
    private final ExerciseRepository exercises;
    private final SubscriptionRepository subscriptions;
    private final PlanInitializationService plans;
    private final TrainingSessionRepository sessions;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;
    public AdminManagementService(UserAccountRepository users, ExerciseRepository exercises,
            SubscriptionRepository subscriptions, PlanInitializationService plans, TrainingSessionRepository sessions,
            org.springframework.security.crypto.password.PasswordEncoder encoder) {
        this.users = users; this.exercises = exercises; this.subscriptions = subscriptions;
        this.plans = plans; this.sessions = sessions;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        return Map.of("users", users.count(), "exercises", exercises.count(), "sessions", sessions.count());
    }

    @Transactional(readOnly = true)
    public List<UserRow> users(int page) {
        return users.findAll(PageRequest.of(Math.max(0, page), 50)).stream().map(user ->
                new UserRow(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole(), user.isEnabled(),
                        subscriptions.findFirstByUserIdAndStatusOrderByStartsAtDesc(user.getId(), SubscriptionStatus.ACTIVE)
                                .map(sub -> sub.getPlan().getCode()).orElse(null))).toList();
    }

    @Transactional
    public void updateUser(UUID id, AdminManagementController.UserUpdate update) {
        UserAccount user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        if (user.getRole() == UserRole.ADMIN && !update.enabled()) {
            throw new BadRequestException("Los administradores no pueden deshabilitarse desde este panel.");
        }
        if (user.isEnabled() != update.enabled()) user.setEnabled(update.enabled());
        Subscription subscription = subscriptions.findFirstByUserIdAndStatusOrderByStartsAtDesc(id, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No hay suscripción activa."));
        subscription.changePlan(update.plan() == PlanCode.FREE ? plans.freePlan() : plans.premiumPlan());
        subscriptions.save(subscription);
        users.save(user);
    }

    @Transactional
    public void resetPassword(UUID id, String password) {
        com.oratoria.backend.shared.PasswordPolicy.validate(password);
        UserAccount user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        if (user.getRole() == UserRole.ADMIN) throw new BadRequestException("Un administrador debe cambiar su contraseña desde su perfil.");
        user.changePassword(encoder.encode(password));
        users.save(user);
    }

    @Transactional(readOnly = true)
    public List<ExerciseRow> exercises() {
        return exercises.findAll().stream().map(exercise -> new ExerciseRow(exercise.getId(), exercise.getCode(),
                exercise.getTitle(), exercise.getDescription(), exercise.getPrompt(), exercise.getCategory(),
                exercise.getDifficulty(), exercise.getObjective(), exercise.getRecommendedDurationSeconds(),
                exercise.getTrainingType().name(), exercise.isActive())).toList();
    }

    @Transactional
    public void saveExercise(UUID id, AdminManagementController.ExerciseUpdate update) {
        Exercise exercise;
        if (id == null) {
            if (exercises.findByCode(update.code()).isPresent()) throw new BadRequestException("El código ya existe.");
            exercise = new Exercise(update.code(), update.title(), update.description(), update.prompt(),
                    update.trainingType(), update.duration());
        } else {
            exercise = exercises.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ejercicio no encontrado."));
            exercise.edit(update.title(), update.description(), update.prompt(), update.duration());
        }
        exercise.configure(update.category(), update.difficulty(), update.objective());
        exercise.setActive(update.active());
        exercises.save(exercise);
    }

    public record UserRow(UUID id, String name, String email, UserRole role, boolean enabled, PlanCode plan) {}
    public record ExerciseRow(UUID id, String code, String title, String description, String prompt,
            String category, String difficulty, String objective, int duration, String trainingType, boolean active) {}
}

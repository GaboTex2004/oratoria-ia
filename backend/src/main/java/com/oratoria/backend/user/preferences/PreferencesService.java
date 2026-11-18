package com.oratoria.backend.user.preferences;

import com.oratoria.backend.admin.category.ExerciseCategory;
import com.oratoria.backend.admin.category.ExerciseCategoryRepository;
import com.oratoria.backend.auth.exception.InvalidCredentialsException;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.style.CommunicationStyleCatalog;
import com.oratoria.backend.subscription.entity.Feature;
import com.oratoria.backend.subscription.service.FeatureAccessService;
import com.oratoria.backend.training.TrainingType;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.repository.UserAccountRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferencesService {
    static final List<String> LEVELS = List.of("BASIC", "INTERMEDIATE", "ADVANCED");
    private static final ZoneId WEEK_ZONE = ZoneId.of("America/La_Paz");

    private final UserAccountRepository users;
    private final TrainingPreferencesRepository preferences;
    private final ExerciseCategoryRepository categories;
    private final CommunicationStyleCatalog styles;
    private final FeatureAccessService features;
    private final TrainingSessionRepository sessions;

    public PreferencesService(UserAccountRepository users, TrainingPreferencesRepository preferences,
                              ExerciseCategoryRepository categories, CommunicationStyleCatalog styles,
                              FeatureAccessService features, TrainingSessionRepository sessions) {
        this.users = users; this.preferences = preferences; this.categories = categories;
        this.styles = styles; this.features = features; this.sessions = sessions;
    }

    /** Stored preferences, or unsaved defaults when the user never configured them. */
    @Transactional(readOnly = true)
    public TrainingPreferences forUser(UUID userId) {
        return preferences.findById(userId).orElseGet(() -> new TrainingPreferences(userId));
    }

    @Transactional(readOnly = true)
    public PreferencesView get(String email) {
        return view(forUser(user(email).getId()));
    }

    @Transactional
    public PreferencesView update(String email, PreferencesUpdate request) {
        UUID userId = user(email).getId();
        String goal = request.goal() == null ? null : parseGoal(request.goal()).name();
        if (!LEVELS.contains(request.experienceLevel())) throw new BadRequestException("Nivel de experiencia inválido.");
        Set<String> activeSkills = new LinkedHashSet<>(activeCategories().stream().map(ExerciseCategory::getCode).toList());
        List<String> skills = request.prioritySkills().stream().distinct().toList();
        for (String skill : skills) {
            if (!activeSkills.contains(skill)) throw new BadRequestException("Habilidad prioritaria inexistente: " + skill);
        }
        String style = request.preferredStyle() == null ? null : styles.require(request.preferredStyle()).code();
        if (style != null) features.require(userId, Feature.COMMUNICATION_STYLES);
        TrainingPreferences entity = preferences.findById(userId).orElseGet(() -> new TrainingPreferences(userId));
        entity.update(goal, request.experienceLevel(), skills,
                request.preferredTrainingTypes().stream().distinct().map(TrainingType::name).toList(), style, request.weeklyGoal());
        return view(preferences.saveAndFlush(entity));
    }

    @Transactional
    public void selectStyle(UUID userId, String style) {
        TrainingPreferences entity = preferences.findById(userId).orElseGet(() -> new TrainingPreferences(userId));
        if (entity.getUpdatedAt() == null) {
            entity.update(null, "BASIC", List.of(), List.of(), style, 3);
        } else {
            entity.selectStyle(style);
        }
        preferences.saveAndFlush(entity);
    }

    private PreferencesView view(TrainingPreferences entity) {
        Instant weekStart = ZonedDateTime.now(WEEK_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay(WEEK_ZONE).toInstant();
        Options options = new Options(
                Arrays.stream(TrainingGoal.values()).map(goal -> new Option(goal.name(), goal.label())).toList(),
                LEVELS,
                activeCategories().stream().map(category -> new Option(category.getCode(), category.getName())).toList(),
                Arrays.stream(TrainingType.values()).map(TrainingType::name).toList(),
                styles.list().stream().map(style -> new Option(style.code(), style.name())).toList());
        return new PreferencesView(entity.getGoal(), entity.getExperienceLevel(), entity.getPrioritySkills(),
                entity.getPreferredTrainingTypes(), entity.getPreferredStyle(), entity.getWeeklyGoal(),
                sessions.countByUserIdAndCreatedAtGreaterThanEqual(entity.getUserId(), weekStart),
                entity.getUpdatedAt(), options);
    }

    private List<ExerciseCategory> activeCategories() {
        return categories.findAllByOrderByNameAsc().stream().filter(ExerciseCategory::isActive).toList();
    }

    private static TrainingGoal parseGoal(String goal) {
        try {
            return TrainingGoal.valueOf(goal);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Objetivo de entrenamiento inválido.");
        }
    }

    private UserAccount user(String email) {
        return users.findByEmailIgnoreCase(email).filter(UserAccount::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);
    }

    public record PreferencesUpdate(@Size(max = 40) String goal,
                                    @NotBlank String experienceLevel,
                                    @NotNull @Size(max = 3, message = "Elige hasta 3 habilidades prioritarias.") List<@NotBlank String> prioritySkills,
                                    @NotNull @Size(max = 4) List<@NotNull TrainingType> preferredTrainingTypes,
                                    String preferredStyle,
                                    @Min(1) @Max(14) int weeklyGoal) {}

    public record PreferencesView(String goal, String experienceLevel, List<String> prioritySkills,
                                  List<String> preferredTrainingTypes, String preferredStyle, int weeklyGoal,
                                  long practicesThisWeek, Instant updatedAt, Options options) {}

    public record Options(List<Option> goals, List<String> levels, List<Option> skills,
                          List<String> trainingTypes, List<Option> styles) {}

    public record Option(String code, String label) {}
}

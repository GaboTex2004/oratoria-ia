package com.oratoria.backend.progress.service;

import com.oratoria.backend.admin.category.ExerciseCategory;
import com.oratoria.backend.admin.category.ExerciseCategoryRepository;
import com.oratoria.backend.auth.exception.InvalidCredentialsException;
import com.oratoria.backend.exercise.service.ExerciseService;
import com.oratoria.backend.progress.service.AdaptiveRecommendationEngine.Access;
import com.oratoria.backend.progress.service.AdaptiveRecommendationEngine.Recommendation;
import com.oratoria.backend.subscription.entity.Feature;
import com.oratoria.backend.subscription.service.FeatureAccessService;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.preferences.PreferencesService;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {
    private final ProgressService progress;
    private final ExerciseService exercises;
    private final PreferencesService preferences;
    private final FeatureAccessService features;
    private final ExerciseCategoryRepository categories;
    private final UserAccountRepository users;

    public RecommendationService(ProgressService progress, ExerciseService exercises, PreferencesService preferences,
                                 FeatureAccessService features, ExerciseCategoryRepository categories, UserAccountRepository users) {
        this.progress = progress; this.exercises = exercises; this.preferences = preferences;
        this.features = features; this.categories = categories; this.users = users;
    }

    @Transactional
    public Recommendation recommend(String email) {
        UUID userId = users.findByEmailIgnoreCase(email).filter(UserAccount::isEnabled)
                .orElseThrow(InvalidCredentialsException::new).getId();
        Map<String, String> names = categories.findAll().stream()
                .collect(Collectors.toMap(ExerciseCategory::getCode, ExerciseCategory::getName));
        Access access = new Access(features.hasAccess(userId, Feature.ADAPTIVE_TRAINING),
                features.hasAccess(userId, Feature.IMPROVISATION), features.hasAccess(userId, Feature.COMMUNICATION_STYLES));
        return AdaptiveRecommendationEngine.recommend(progress.getSummary(email).history(), preferences.forUser(userId),
                exercises.listExercises(null), names, access);
    }
}

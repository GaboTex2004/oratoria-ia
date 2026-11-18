package com.oratoria.backend.style;

import com.oratoria.backend.auth.exception.InvalidCredentialsException;
import com.oratoria.backend.exercise.dto.ExerciseResponse;
import com.oratoria.backend.exercise.service.ExerciseService;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.CriterionScore;
import com.oratoria.backend.progress.service.ProgressService;
import com.oratoria.backend.session.dto.SessionResponse;
import com.oratoria.backend.session.service.TrainingSessionService;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.style.CommunicationStyleCatalog.CommunicationStyle;
import com.oratoria.backend.subscription.entity.Feature;
import com.oratoria.backend.subscription.service.FeatureAccessService;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.preferences.PreferencesService;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StyleService {
    private final CommunicationStyleCatalog catalog;
    private final ExerciseService exercises;
    private final FeatureAccessService features;
    private final PreferencesService preferences;
    private final TrainingSessionService sessions;
    private final ProgressService progress;
    private final UserAccountRepository users;

    public StyleService(CommunicationStyleCatalog catalog, ExerciseService exercises, FeatureAccessService features,
                        PreferencesService preferences, TrainingSessionService sessions, ProgressService progress,
                        UserAccountRepository users) {
        this.catalog = catalog; this.exercises = exercises; this.features = features;
        this.preferences = preferences; this.sessions = sessions; this.progress = progress; this.users = users;
    }

    @Transactional
    public StylesResponse list(String email) {
        UUID userId = user(email).getId();
        List<ExerciseResponse> catalogExercises = exercises.listExercises(null);
        List<StyleView> views = catalog.list().stream().map(style -> new StyleView(style.code(), style.name(), style.summary(),
                style.traits(), style.guidance(), style.minWordsPerMinute(), style.maxWordsPerMinute(),
                catalogExercises.stream().filter(exercise -> style.code().equals(exercise.communicationStyle())).toList())).toList();
        return new StylesResponse(!features.hasAccess(userId, Feature.COMMUNICATION_STYLES),
                preferences.forUser(userId).getPreferredStyle(), CommunicationStyleCatalog.DISCLAIMER, views);
    }

    @Transactional
    public void select(String email, String style) {
        UUID userId = user(email).getId();
        String code = style == null ? null : catalog.require(style).code();
        if (code != null) features.require(userId, Feature.COMMUNICATION_STYLES);
        preferences.selectStyle(userId, code);
    }

    @Transactional
    public StyleFeedbackView feedback(String email, UUID sessionId) {
        SessionResponse session = sessions.getUserSession(email, sessionId);
        if (session.communicationStyle() == null) {
            throw new BadRequestException("Esta sesión no está asociada a un estilo de comunicación.");
        }
        CommunicationStyle style = catalog.require(session.communicationStyle());
        var item = progress.getSummary(email).history().stream()
                .filter(entry -> entry.sessionId().equals(sessionId)).findFirst()
                .filter(entry -> Boolean.TRUE.equals(entry.sufficientEvidence()) && entry.criteriaScores() != null
                        && !entry.criteriaScores().isEmpty());
        if (item.isEmpty()) {
            return new StyleFeedbackView(style.code(), style.name(), false,
                    "La retroalimentación del estilo estará disponible cuando la sesión tenga una evaluación con evidencia suficiente.",
                    null, List.of(), List.of(), style.traits());
        }
        Map<String, Double> criteria = new HashMap<>();
        for (CriterionScore score : item.get().criteriaScores()) {
            if (score.score() != null) criteria.put(score.code(), score.score());
        }
        var result = StyleFeedbackCalculator.calculate(style, criteria, item.get().wordsPerMinute(),
                item.get().fillerWordsTotal(), item.get().durationSeconds());
        return new StyleFeedbackView(style.code(), style.name(), true, null, result.score(),
                result.observations(), result.tips(), style.traits());
    }

    private UserAccount user(String email) {
        return users.findByEmailIgnoreCase(Objects.requireNonNull(email)).filter(UserAccount::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);
    }

    public record StylesResponse(boolean locked, String selected, String disclaimer, List<StyleView> styles) {}

    public record StyleView(String code, String name, String summary, List<String> traits, String guidance,
                            int minWordsPerMinute, int maxWordsPerMinute, List<ExerciseResponse> exercises) {}

    public record StyleFeedbackView(String style, String styleName, boolean available, String message, Double score,
                                    List<String> observations, List<String> tips, List<String> traits) {}
}

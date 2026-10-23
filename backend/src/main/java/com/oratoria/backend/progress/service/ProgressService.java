package com.oratoria.backend.progress.service;

import com.oratoria.backend.auth.exception.InvalidCredentialsException;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.SubscriptionSummary;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.session.repository.TrainingSessionRepository.ProgressSessionProjection;
import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.entity.SubscriptionStatus;
import com.oratoria.backend.subscription.entity.UsageLimit;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.FeatureAccessService;
import com.oratoria.backend.subscription.service.FeatureAccessService.EntitlementLimit;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService {
    private static final String USAGE_DEFINITION_NOT_DEFINED = "NOT_DEFINED";

    private final UserAccountRepository users;
    private final TrainingSessionRepository sessions;
    private final SubscriptionRepository subscriptions;
    private final FeatureAccessService featureAccess;
    private final ProgressCalculator calculator;

    public ProgressService(
            UserAccountRepository users,
            TrainingSessionRepository sessions,
            SubscriptionRepository subscriptions,
            FeatureAccessService featureAccess,
            ProgressCalculator calculator) {
        this.users = users;
        this.sessions = sessions;
        this.subscriptions = subscriptions;
        this.featureAccess = featureAccess;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public ProgressSummaryResponse getSummary(String userEmail) {
        UserAccount user = findEnabledUser(userEmail);
        List<ProgressSessionSnapshot> snapshots = sessions.findProgressRowsByUserId(user.getId())
                .stream()
                .map(this::toSnapshot)
                .toList();
        return calculator.calculate(snapshots, subscriptionSummary(user));
    }

    private UserAccount findEnabledUser(String email) {
        if (email == null || email.isBlank()) throw new InvalidCredentialsException();
        return users.findByEmailIgnoreCase(email.trim())
                .filter(UserAccount::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);
    }

    private SubscriptionSummary subscriptionSummary(UserAccount user) {
        Optional<Subscription> activeSubscription = subscriptions
                .findFirstByUserIdAndStatusOrderByStartsAtDesc(
                        user.getId(), SubscriptionStatus.ACTIVE);
        Optional<EntitlementLimit> configuredLimit = featureAccess
                .limitFor(user.getId(), UsageLimit.SESSIONS_PER_MONTH);

        return new SubscriptionSummary(
                activeSubscription.map(subscription -> subscription.getPlan().getCode().name()).orElse(null),
                configuredLimit.filter(limit -> !limit.unlimited()).map(EntitlementLimit::value).orElse(null),
                configuredLimit.map(EntitlementLimit::unlimited).orElse(false),
                null,
                USAGE_DEFINITION_NOT_DEFINED
        );
    }

    private ProgressSessionSnapshot toSnapshot(ProgressSessionProjection row) {
        return new ProgressSessionSnapshot(
                row.getSessionId(),
                row.getExerciseTitle(),
                row.getTrainingType(),
                row.getCreatedAt(),
                row.getCompletedAt(),
                row.getDurationSeconds(),
                row.getSessionStatus(),
                row.getAnalysisStatus(),
                row.getWordCount(),
                row.getWordsPerMinute(),
                row.getSpokenWordsPerMinute(),
                row.getFillerWordsTotal(),
                row.getFillerCountsJson(),
                row.getEstimatedPauseCount(),
                row.getSpokenTimeRatio(),
                row.getEvaluationStatus(),
                row.getSufficientEvidence(),
                row.getEvaluationOverallScore(),
                row.getCriteriaScoresJson(),
                row.getStrengthsJson(),
                row.getAreasForImprovementJson()
        );
    }
}

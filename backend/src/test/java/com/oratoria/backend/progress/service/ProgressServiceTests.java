package com.oratoria.backend.progress.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oratoria.backend.progress.dto.ProgressSummaryResponse;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.Insights;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.Overview;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.SubscriptionSummary;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.session.repository.TrainingSessionRepository.ProgressSessionProjection;
import com.oratoria.backend.subscription.entity.SubscriptionStatus;
import com.oratoria.backend.subscription.entity.UsageLimit;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.FeatureAccessService;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProgressServiceTests {

    @Test
    void hundredsOfSessionsUseOneBulkRepositoryCallInsteadOfPerSessionQueries() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        TrainingSessionRepository sessions = mock(TrainingSessionRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        FeatureAccessService featureAccess = mock(FeatureAccessService.class);
        ProgressCalculator calculator = mock(ProgressCalculator.class);
        UserAccount user = mock(UserAccount.class);
        UUID userId = UUID.randomUUID();
        ProgressSessionProjection projection = mock(ProgressSessionProjection.class);
        List<ProgressSessionProjection> rows = Collections.nCopies(250, projection);
        ProgressSummaryResponse expected = emptyResponse();

        when(user.getId()).thenReturn(userId);
        when(user.isEnabled()).thenReturn(true);
        when(users.findByEmailIgnoreCase("student@example.test")).thenReturn(Optional.of(user));
        when(sessions.findProgressRowsByUserId(userId)).thenReturn(rows);
        when(subscriptions.findFirstByUserIdAndStatusOrderByStartsAtDesc(
                userId, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(featureAccess.limitFor(userId, UsageLimit.SESSIONS_PER_MONTH))
                .thenReturn(Optional.empty());
        when(calculator.calculate(anyList(), eq(new SubscriptionSummary(
                null, null, false, null, "NOT_DEFINED")))).thenReturn(expected);

        ProgressService service = new ProgressService(
                users, sessions, subscriptions, featureAccess, calculator);

        assertThat(service.getSummary("student@example.test")).isSameAs(expected);
        verify(sessions, times(1)).findProgressRowsByUserId(userId);
        verify(calculator, times(1)).calculate(anyList(), eq(new SubscriptionSummary(
                null, null, false, null, "NOT_DEFINED")));
    }

    private static ProgressSummaryResponse emptyResponse() {
        return new ProgressSummaryResponse(
                new Overview(0, 0, 0, 0, null, null, null,
                        null, null, null, null, null),
                List.of(),
                List.of(),
                new Insights(List.of(), List.of(), List.of()),
                new SubscriptionSummary(null, null, false, null, "NOT_DEFINED"),
                100,
                false);
    }
}

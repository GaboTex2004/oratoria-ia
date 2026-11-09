package com.oratoria.backend.subscription.service;

import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.simulation.repository.SimulationSessionRepository;
import com.oratoria.backend.subscription.entity.UsageLimit;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotaService {
    private final FeatureAccessService access;
    private final TrainingSessionRepository sessions;
    private final SimulationSessionRepository simulations;
    private final UserAccountRepository users;
    public QuotaService(FeatureAccessService access, TrainingSessionRepository sessions,
            SimulationSessionRepository simulations, UserAccountRepository users) {
        this.access = access; this.sessions = sessions; this.simulations = simulations; this.users = users;
    }
    public static Instant monthStart() {
        return ZonedDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    }
    @Transactional
    public void checkCreation(UUID userId, boolean simulation) {
        users.lockForQuota(userId).orElseThrow();
        var limit = access.limitFor(userId, simulation ? UsageLimit.SIMULATIONS_PER_MONTH : UsageLimit.SESSIONS_PER_MONTH)
                .orElseThrow(() -> new BadRequestException("Tu plan no dispone de esta cuota."));
        long used = simulation ? simulations.countByUserIdAndCreatedAtGreaterThanEqual(userId, monthStart())
                : sessions.countByUserIdAndCreatedAtGreaterThanEqual(userId, monthStart());
        if (!limit.unlimited() && (limit.value() == null || used >= limit.value())) {
            throw new BadRequestException("Alcanzaste el límite mensual de tu plan. Las sesiones creadas consumen cuota hasta el siguiente mes UTC.");
        }
    }
}

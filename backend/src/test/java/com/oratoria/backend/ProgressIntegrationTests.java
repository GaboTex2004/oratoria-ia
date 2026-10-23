package com.oratoria.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient;
import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.evaluation.client.AiSpeechEvaluationClient;
import com.oratoria.backend.session.entity.TrainingSession;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.training.TrainingType;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import com.oratoria.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProgressIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PlanInitializationService plans;
    @Autowired TrainingSessionRepository sessions;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @MockitoBean AiSpeechAnalysisClient aiSpeechAnalysisClient;
    @MockitoBean AiSpeechEvaluationClient aiSpeechEvaluationClient;

    @Test
    void summaryRequiresAuthenticationAndStrictlyIsolatesUsers() throws Exception {
        mvc.perform(get("/api/progress/summary"))
                .andExpect(status().isUnauthorized());

        UserAccount owner = createUser("progress-owner@example.test");
        UserAccount anotherUser = createUser("progress-other@example.test");
        sessions.save(completedSession(owner, 45));
        sessions.save(completedSession(anotherUser, 90));

        mvc.perform(get("/api/progress/summary")
                        .header("Authorization", "Bearer " + jwtService.createToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.totalSessions").value(1))
                .andExpect(jsonPath("$.overview.completedSessions").value(1))
                .andExpect(jsonPath("$.overview.totalPracticeSeconds").value(45))
                .andExpect(jsonPath("$.overview.latestOverallScore").isEmpty())
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].durationSeconds").value(45))
                .andExpect(jsonPath("$.subscription.plan").value("FREE"))
                .andExpect(jsonPath("$.subscription.sessionsLimit").value(5))
                .andExpect(jsonPath("$.subscription.sessionsUsedInPeriod").isEmpty())
                .andExpect(jsonPath("$.subscription.usageDefinition").value("NOT_DEFINED"));
    }

    private UserAccount createUser(String email) {
        UserAccount user = users.save(new UserAccount(
                "Progress", "Student", email,
                passwordEncoder.encode("Password123!"), UserRole.USER));
        subscriptions.save(new Subscription(user, plans.freePlan()));
        return user;
    }

    private TrainingSession completedSession(UserAccount user, int durationSeconds) {
        TrainingSession session = new TrainingSession(user, TrainingType.PRESENTATION);
        session.markRecording();
        session.completeWithAudio(
                durationSeconds,
                "test-only/" + user.getId() + ".webm",
                "audio/webm",
                128,
                "test.webm");
        return session;
    }
}

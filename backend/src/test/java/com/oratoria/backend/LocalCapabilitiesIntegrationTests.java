package com.oratoria.backend;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.auth.service.DemoUserInitializer;
import com.oratoria.backend.exercise.service.ExtendedCatalogInitializer;
import com.oratoria.backend.exercise.repository.ExerciseRepository;
import com.oratoria.backend.session.entity.TrainingSession;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.session.service.AudioStorageService;
import com.oratoria.backend.session.service.SessionPrivacyService;
import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.training.TrainingType;
import com.oratoria.backend.user.entity.*;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocalCapabilitiesIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PlanInitializationService plans;
    @Autowired TrainingSessionRepository sessions;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired AudioStorageService storage;
    @Autowired SessionPrivacyService privacy;
    @Autowired ExerciseRepository exercises;
    @Autowired ExtendedCatalogInitializer catalog;
    @Autowired com.oratoria.backend.simulation.repository.SimulationSessionRepository simulationSessions;
    @Autowired com.oratoria.backend.simulation.repository.SimulationTurnRepository simulationTurns;
    @Autowired com.oratoria.backend.simulation.service.SimulationPrivacyService simulationPrivacy;

    UserAccount user(UserRole role) {
        UserAccount user = users.saveAndFlush(new UserAccount("Local", "Test", UUID.randomUUID() + "@example.test", encoder.encode("TestPassword123!"), role));
        subscriptions.save(new Subscription(user, plans.freePlan()));
        return user;
    }
    String authorization(UserAccount user) { return "Bearer " + jwt.createToken(user); }

    @Test void profileChangesPersistAndPasswordChangeRevokesPreviousToken() throws Exception {
        var user = user(UserRole.USER);
        String auth = authorization(user);
        mvc.perform(put("/api/users/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Isaias\",\"lastName\":\"Gutierrez\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Isaias"));
        mvc.perform(post("/api/users/me/password").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"ChangedPassword123!\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users/me/password").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"TestPassword123!\",\"newPassword\":\"ChangedPassword123!\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization", auth)).andExpect(status().isUnauthorized());
        assertThat(encoder.matches("ChangedPassword123!", users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void freePlanAllowsFiveCreatedPracticesAndRejectsSixth() throws Exception {
        String auth = authorization(user(UserRole.USER));
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/sessions").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"trainingType\":\"PRESENTATION\"}")).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/sessions").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"trainingType\":\"PRESENTATION\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/progress/summary").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subscription.sessionsUsedInPeriod").value(5));
    }

    @Test void adminEndpointsProtectUsersAndDisablingRevokesAccess() throws Exception {
        var student = user(UserRole.USER);
        String studentAuth = authorization(student);
        String adminAuth = authorization(user(UserRole.ADMIN));
        mvc.perform(get("/api/admin/users").header("Authorization", studentAuth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/overview").header("Authorization", adminAuth)).andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/" + student.getId()).header("Authorization", adminAuth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"plan\":\"PREMIUM\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization", studentAuth)).andExpect(status().isUnauthorized());
    }

    @Test void bodyAnalysisChecksOwnershipAndExplicitConsentBeforeCallingPython() throws Exception {
        var owner = user(UserRole.USER);
        var session = sessions.saveAndFlush(new TrainingSession(owner, TrainingType.PRESENTATION));
        String path = "/api/sessions/" + session.getId() + "/body-analysis";
        mvc.perform(get(path).header("Authorization", authorization(user(UserRole.USER)))).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", authorization(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"consent\":false,\"frames\":[\"not-an-image\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test void privateDataErasureRemovesMediaWithoutRefundingQuotaMetadata() throws Exception {
        var owner = user(UserRole.USER);
        var session = sessions.saveAndFlush(new TrainingSession(owner, TrainingType.PRESENTATION));
        var audio = storage.store(owner.getId(), session.getId(), new MockMultipartFile("audio", "synthetic.wav", "audio/wav", new byte[]{1, 2, 3}), 3);
        session.completeWithAudio(3, audio.storagePath(), audio.mimeType(), audio.sizeBytes(), audio.originalFilename());
        sessions.saveAndFlush(session);
        var path = storage.loadAsResource(audio.storagePath()).getFile().toPath();
        assertThat(Files.exists(path)).isTrue();
        mvc.perform(delete("/api/sessions/" + session.getId() + "/data").header("Authorization", authorization(user(UserRole.USER))))
                .andExpect(status().isForbidden());
        privacy.erase(owner.getEmail(), session.getId());
        privacy.deleteQueuedFiles();
        assertThat(Files.exists(path)).isFalse();
        assertThat(sessions.findById(session.getId()).orElseThrow().hasAudio()).isFalse();
        assertThat(sessions.countByUserIdAndCreatedAtGreaterThanEqual(owner.getId(), com.oratoria.backend.subscription.service.QuotaService.monthStart())).isEqualTo(1);
    }

    @Test void catalogAndDevelopmentAccountsAreIdempotent() {
        long count = exercises.count();
        catalog.run(new DefaultApplicationArguments());
        catalog.run(new DefaultApplicationArguments());
        assertThat(exercises.count()).isEqualTo(count);
        String email = UUID.randomUUID() + "@example.test";
        var environment = new MockEnvironment().withProperty("DEMO_FREE_EMAIL", email)
                .withProperty("DEMO_FREE_PASSWORD", "OnlyForAutomatedTests123!");
        var initializer = new DemoUserInitializer(users, subscriptions, plans, encoder, environment);
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());
        var demo = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(subscriptions.countByUserId(demo.getId())).isEqualTo(1);
        assertThat(encoder.matches("OnlyForAutomatedTests123!", demo.getPasswordHash())).isTrue();
    }

    @Test void administrativeRecoveryRevokesOldTokensAndRejectsRegularUsers() throws Exception {
        var student = user(UserRole.USER);
        String oldToken = authorization(student);
        String endpoint = "/api/admin/users/" + student.getId() + "/password";
        String body = "{\"newPassword\":\"RecoveredPassword123!\"}";
        mvc.perform(post(endpoint).header("Authorization", oldToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(endpoint).header("Authorization", authorization(user(UserRole.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization", oldToken)).andExpect(status().isUnauthorized());
        assertThat(encoder.matches("RecoveredPassword123!", users.findById(student.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void overlongUtf8PasswordIsRejectedWithoutBcryptServerError() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Test\",\"lastName\":\"Unicode\",\"email\":\"unicode@example.test\",\"password\":\"" + "á".repeat(40) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void conversationErasureIsPrivateAndClearsTextAndScores() throws Exception {
        var owner = user(UserRole.USER);
        var simulation = simulationSessions.saveAndFlush(new com.oratoria.backend.simulation.entity.SimulationSession(
                owner, "job-interview", TrainingType.INTERVIEW, com.oratoria.backend.simulation.entity.SimulationDifficulty.BASIC, 3));
        simulationTurns.saveAndFlush(com.oratoria.backend.simulation.entity.SimulationTurn.ai(simulation, 1, "Synthetic private fixture"));
        mvc.perform(delete("/api/simulations/" + simulation.getId() + "/data").header("Authorization", authorization(user(UserRole.USER))))
                .andExpect(status().isForbidden());
        simulationPrivacy.erase(owner.getEmail(), simulation.getId());
        assertThat(simulationTurns.findBySimulationIdOrderBySequenceNumberAsc(simulation.getId())).allMatch(turn -> turn.getText() == null && !turn.hasAudio());
        assertThat(simulationSessions.findById(simulation.getId()).orElseThrow().getOverallScore()).isNull();
    }

    @Test void corsAllowsOnlyConfiguredLocalOrigins() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://127.0.0.1:5173")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
        mvc.perform(options("/api/auth/login").header("Origin", "http://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    @Test void sensitiveDtosDoNotPrintCredentialsOrFrames() {
        String secret = "NeverLogThisPassword123!";
        assertThat(new com.oratoria.backend.auth.dto.LoginRequest("test@example.test", secret).toString()).doesNotContain(secret);
        assertThat(new com.oratoria.backend.auth.dto.RegisterRequest("Test", "User", "test@example.test", secret).toString()).doesNotContain(secret);
        assertThat(new com.oratoria.backend.user.service.ProfileService.PasswordUpdate(secret, secret).toString()).doesNotContain(secret);
        assertThat(new com.oratoria.backend.admin.AdminManagementController.PasswordReset(secret).toString()).doesNotContain(secret);
        assertThat(new com.oratoria.backend.body.BodyController.BodyRequest(true, java.util.List.of("PRIVATE_IMAGE_DATA")).toString()).doesNotContain("PRIVATE_IMAGE_DATA");
    }
}

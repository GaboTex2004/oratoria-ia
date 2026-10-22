package com.oratoria.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.exercise.entity.Exercise;
import com.oratoria.backend.exercise.repository.ExerciseRepository;
import com.oratoria.backend.session.entity.SessionStatus;
import com.oratoria.backend.session.entity.TrainingSession;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.training.TrainingType;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionAndExerciseIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PlanInitializationService plans;
    @Autowired ExerciseRepository exercises;
    @Autowired TrainingSessionRepository sessions;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @Test
    void exercisesCatalogIsSeededAndRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/exercises")).andExpect(status().isUnauthorized());

        String token = createTestUserAndToken("exercises-user@example.test");
        mvc.perform(get("/api/exercises").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].title").isNotEmpty())
                .andExpect(jsonPath("$[0].prompt").isNotEmpty());

        mvc.perform(get("/api/exercises")
                        .param("trainingType", "PITCH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].trainingType").value("PITCH"));
    }

    @Test
    void fullSessionLifecycleCreatesRecordsCompletesAndStreamsAudioForOwner() throws Exception {
        String token = createTestUserAndToken("owner@example.test");
        Exercise pitchExercise = exercises.findByCode("PITCH_ELEVATOR").orElseThrow();

        String createBody = """
                {"exerciseId":"%s","trainingType":"PITCH"}
                """.formatted(pitchExercise.getId());

        mvc.perform(post("/api/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECORDING"))
                .andExpect(jsonPath("$.trainingType").value("PITCH"))
                .andExpect(jsonPath("$.exerciseId").value(pitchExercise.getId().toString()))
                .andExpect(jsonPath("$.exerciseTitle").value(pitchExercise.getTitle()))
                .andExpect(jsonPath("$.hasAudio").value(false));

        UserAccount owner = users.findByEmailIgnoreCase("owner@example.test").orElseThrow();
        TrainingSession created = sessions.findByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst();

        byte[] audioBytes = "WEBMAUDIODATA-TEST-STREAM".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile audioFile = new MockMultipartFile(
                "audio", "practice.webm", "audio/webm;codecs=opus", audioBytes);

        mvc.perform(multipart("/api/sessions/{id}/complete", created.getId())
                        .file(audioFile)
                        .param("durationSeconds", "45")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.durationSeconds").value(45))
                .andExpect(jsonPath("$.hasAudio").value(true))
                .andExpect(jsonPath("$.audioMimeType").value("audio/webm"))
                .andExpect(jsonPath("$.audioSizeBytes").value(audioBytes.length))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mvc.perform(get("/api/sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(created.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));

        mvc.perform(get("/api/sessions/{id}/audio", created.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/webm"))
                .andExpect(content().bytes(audioBytes));
    }

    @Test
    void anotherUserCannotViewCompleteOrDownloadOtherUsersSession() throws Exception {
        String tokenA = createTestUserAndToken("user-a@example.test");
        String tokenB = createTestUserAndToken("user-b@example.test");

        UserAccount userA = users.findByEmailIgnoreCase("user-a@example.test").orElseThrow();
        Exercise exercise = exercises.findByCode("PRESENTATION_INTRO").orElseThrow();
        TrainingSession sessionA = new TrainingSession(userA, exercise, TrainingType.PRESENTATION);
        sessionA.markRecording();
        sessionA = sessions.save(sessionA);

        MockMultipartFile audioFile = new MockMultipartFile(
                "audio", "recording.webm", "audio/webm", "AUDIO-A".getBytes(StandardCharsets.UTF_8));

        // User B cannot view User A's session
        mvc.perform(get("/api/sessions/{id}", sessionA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // User B cannot complete User A's session
        mvc.perform(multipart("/api/sessions/{id}/complete", sessionA.getId())
                        .file(audioFile)
                        .param("durationSeconds", "30")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // User A completes their own session
        mvc.perform(multipart("/api/sessions/{id}/complete", sessionA.getId())
                        .file(audioFile)
                        .param("durationSeconds", "30")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // User B cannot download User A's audio
        mvc.perform(get("/api/sessions/{id}/audio", sessionA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // User B's session list is empty
        mvc.perform(get("/api/sessions")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(sessions.findById(sessionA.getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void audioUploadValidatesEmptyFileInvalidMimeTypeAndDurationLimits() throws Exception {
        String token = createTestUserAndToken("validation-user@example.test");
        UserAccount user = users.findByEmailIgnoreCase("validation-user@example.test").orElseThrow();
        TrainingSession session = sessions.save(new TrainingSession(user, TrainingType.DEBATE));

        MockMultipartFile emptyAudio = new MockMultipartFile(
                "audio", "empty.webm", "audio/webm", new byte[0]);
        mvc.perform(multipart("/api/sessions/{id}/complete", session.getId())
                        .file(emptyAudio)
                        .param("durationSeconds", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        MockMultipartFile invalidMime = new MockMultipartFile(
                "audio", "malicious.txt", "text/plain", "not-audio".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/sessions/{id}/complete", session.getId())
                        .file(invalidMime)
                        .param("durationSeconds", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        MockMultipartFile validAudio = new MockMultipartFile(
                "audio", "valid.ogg", "audio/ogg", "OGG-DATA".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/sessions/{id}/complete", session.getId())
                        .file(validAudio)
                        .param("durationSeconds", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mvc.perform(multipart("/api/sessions/{id}/complete", session.getId())
                        .file(validAudio)
                        .param("durationSeconds", "999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private String createTestUserAndToken(String email) {
        UserAccount user = users.save(new UserAccount(
                "Test", "Student", email, passwordEncoder.encode("Password123!"), UserRole.USER));
        subscriptions.save(new Subscription(user, plans.freePlan()));
        return jwtService.createToken(user);
    }
}

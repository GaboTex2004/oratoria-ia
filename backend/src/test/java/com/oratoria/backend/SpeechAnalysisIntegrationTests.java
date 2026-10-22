package com.oratoria.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient;
import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient.AiAnalysisClientException;
import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient.AiSpeechAnalysisPayload;
import com.oratoria.backend.analysis.client.HttpAiSpeechAnalysisClient;
import com.oratoria.backend.analysis.dto.TranscriptSegmentDto;
import com.oratoria.backend.analysis.dto.VerbalMetricsDto;
import com.oratoria.backend.analysis.dto.WordTimestampDto;
import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.analysis.entity.SpeechAnalysis;
import com.oratoria.backend.analysis.repository.SpeechAnalysisRepository;
import com.oratoria.backend.analysis.service.SpeechAnalysisRecoveryRunner;
import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.evaluation.client.AiSpeechEvaluationClient;
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
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpeechAnalysisIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PlanInitializationService plans;
    @Autowired ExerciseRepository exercises;
    @Autowired TrainingSessionRepository sessions;
    @Autowired SpeechAnalysisRepository analyses;
    @Autowired SpeechAnalysisRecoveryRunner recoveryRunner;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AiSpeechAnalysisClient aiClient;
    @MockitoBean AiSpeechEvaluationClient aiEvaluationClient;

    @BeforeEach
    void setUp() {
        reset(aiClient);
    }

    @Test
    void completingSessionPersistsTranscriptionMetricsAndPreventsDuplicateCalls() throws Exception {
        String token = createTestUserAndToken("analysis-owner@example.test");
        UserAccount owner = users.findByEmailIgnoreCase("analysis-owner@example.test").orElseThrow();
        Exercise exercise = exercises.findByCode("PITCH_ELEVATOR").orElseThrow();
        TrainingSession session = new TrainingSession(owner, exercise, TrainingType.PITCH);
        session.markRecording();
        session = sessions.save(session);

        when(aiClient.analyzeSpeech(any(UUID.class), any(byte[].class), any(), anyInt(), any()))
                .thenReturn(samplePayload(session.getId()));

        MockMultipartFile audioFile = new MockMultipartFile(
                "audio", "pitch.webm", "audio/webm;codecs=opus", "WEBM_AUDIO_BYTES".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/sessions/{id}/complete", session.getId())
                        .file(audioFile)
                        .param("durationSeconds", "30")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.analysisStatus").value("COMPLETED"));

        mvc.perform(get("/api/sessions/{id}/analysis", session.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.provider").value("groq"))
                .andExpect(jsonPath("$.model").value("whisper-large-v3-turbo"))
                .andExpect(jsonPath("$.transcriptText").value("Hola jurado, eh, presentamos nuestra solución innovadora."))
                .andExpect(jsonPath("$.metrics.wordCount").value(7))
                .andExpect(jsonPath("$.metrics.wordsPerMinute").value(84.0))
                .andExpect(jsonPath("$.metrics.fillerWordsTotal").value(1))
                .andExpect(jsonPath("$.metrics.fillerWordCounts.eh").value(1))
                .andExpect(jsonPath("$.metrics.estimatedPauseCount").value(1))
                .andExpect(jsonPath("$.segments.length()").value(1))
                .andExpect(jsonPath("$.words.length()").value(7));

        // Idempotent retry on already COMPLETED analysis does not invoke Groq again
        mvc.perform(post("/api/sessions/{id}/analysis/retry", session.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(aiClient, times(1)).analyzeSpeech(any(), any(), any(), anyInt(), any());
    }

    @Test
    void providerFailureDoesNotDestroySessionAndAllowsControlledRetryAndIsolation() throws Exception {
        String tokenA = createTestUserAndToken("speaker-a@example.test");
        String tokenB = createTestUserAndToken("speaker-b@example.test");
        UserAccount userA = users.findByEmailIgnoreCase("speaker-a@example.test").orElseThrow();
        TrainingSession session = sessions.save(new TrainingSession(userA, TrainingType.INTERVIEW));

        when(aiClient.analyzeSpeech(any(UUID.class), any(byte[].class), any(), anyInt(), any()))
                .thenThrow(new AiAnalysisClientException("PROVIDER_RATE_LIMIT", "Límite temporal en Groq.", true));

        MockMultipartFile audioFile = new MockMultipartFile(
                "audio", "interview.webm", "audio/webm", "AUDIO_INTERVIEW".getBytes(StandardCharsets.UTF_8));

        // Session completion still succeeds (status = COMPLETED) even though Groq failed!
        mvc.perform(multipart("/api/sessions/{id}/complete", session.getId())
                        .file(audioFile)
                        .param("durationSeconds", "20")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.hasAudio").value(true))
                .andExpect(jsonPath("$.analysisStatus").value("FAILED"));

        mvc.perform(get("/api/sessions/{id}/analysis", session.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("PROVIDER_RATE_LIMIT"))
                .andExpect(jsonPath("$.retryable").value(true));

        // User B cannot view or retry User A's analysis
        mvc.perform(get("/api/sessions/{id}/analysis", session.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/sessions/{id}/analysis/retry", session.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Now provider recovers on retry
        when(aiClient.analyzeSpeech(any(UUID.class), any(byte[].class), any(), anyInt(), any()))
                .thenReturn(samplePayload(session.getId()));

        mvc.perform(post("/api/sessions/{id}/analysis/retry", session.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.attemptCount").value(2));

        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void recoveryRunnerMarksInterruptedTasksAsFailedRetryableOnStartup() {
        UserAccount user = users.save(new UserAccount(
                "Recovery", "User", "recovery@example.test", passwordEncoder.encode("Pass123!"), UserRole.USER));
        subscriptions.save(new Subscription(user, plans.freePlan()));
        TrainingSession session = sessions.save(new TrainingSession(user, TrainingType.DEBATE));

        SpeechAnalysis stuck = new SpeechAnalysis(session);
        stuck.markProcessing();
        stuck = analyses.saveAndFlush(stuck);

        recoveryRunner.run(new DefaultApplicationArguments());

        SpeechAnalysis recovered = analyses.findById(stuck.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(recovered.getErrorCode()).isEqualTo("INTERRUPTED_BY_RESTART");
        assertThat(recovered.isRetryable()).isTrue();
    }

    @Test
    void httpAiSpeechAnalysisClientUsesHttp11AndMultipartWithoutUpgradeHeaders() throws Exception {
        AtomicReference<String> capturedProtocol = new AtomicReference<>();
        AtomicReference<String> capturedUpgradeHeader = new AtomicReference<>();
        AtomicReference<String> capturedContentType = new AtomicReference<>();
        AtomicReference<String> capturedInternalKey = new AtomicReference<>();
        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        AtomicReference<byte[]> capturedBody = new AtomicReference<>();

        UUID sessionId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/speech/analyze", exchange -> {
            capturedProtocol.set(exchange.getProtocol());
            capturedUpgradeHeader.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            capturedInternalKey.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            capturedRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            capturedBody.set(exchange.getRequestBody().readAllBytes());

            byte[] responseJson = objectMapper.writeValueAsBytes(samplePayload(sessionId));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("X-Request-Id", capturedRequestId.get());
            exchange.sendResponseHeaders(200, responseJson.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseJson);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            HttpAiSpeechAnalysisClient realClient = new HttpAiSpeechAnalysisClient(
                    objectMapper,
                    "http://127.0.0.1:" + port,
                    "test-shared-internal-key",
                    15
            );

            byte[] sampleWebm = new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 0x01, 0x02, 0x03};
            AiSpeechAnalysisPayload result = realClient.analyzeSpeech(
                    sessionId,
                    sampleWebm,
                    "audio/webm;codecs=opus",
                    9,
                    "session-" + sessionId + ".webm"
            );

            assertThat(result.sessionId()).isEqualTo(sessionId.toString());
            assertThat(capturedProtocol.get()).isEqualTo("HTTP/1.1");
            assertThat(capturedUpgradeHeader.get()).isNull();
            assertThat(capturedInternalKey.get()).isEqualTo("test-shared-internal-key");
            assertThat(capturedRequestId.get()).isNotBlank();
            assertThat(capturedContentType.get()).startsWith("multipart/form-data; boundary=");

            String bodyAsString = new String(capturedBody.get(), StandardCharsets.ISO_8859_1);
            assertThat(bodyAsString).contains("Content-Disposition: form-data; name=\"session_id\"");
            assertThat(bodyAsString).contains("Content-Disposition: form-data; name=\"duration_seconds\"");
            assertThat(bodyAsString).contains("Content-Disposition: form-data; name=\"file\"; filename=\"session-" + sessionId + ".webm\"");
            assertThat(bodyAsString).contains("Content-Type: audio/webm");
        } finally {
            server.stop(0);
        }
    }

    private AiSpeechAnalysisPayload samplePayload(UUID sessionId) {
        return new AiSpeechAnalysisPayload(
                "v1",
                sessionId.toString(),
                "groq",
                "whisper-large-v3-turbo",
                "es",
                "Hola jurado, eh, presentamos nuestra solución innovadora.",
                List.of(new TranscriptSegmentDto(0, 0.0, 5.0, "Hola jurado, eh, presentamos nuestra solución innovadora.")),
                List.of(
                        new WordTimestampDto("Hola", 0.0, 0.4),
                        new WordTimestampDto("jurado,", 0.4, 0.9),
                        new WordTimestampDto("eh,", 1.7, 2.0),
                        new WordTimestampDto("presentamos", 2.1, 2.8),
                        new WordTimestampDto("nuestra", 2.8, 3.2),
                        new WordTimestampDto("solución", 3.2, 3.8),
                        new WordTimestampDto("innovadora.", 3.8, 4.5)
                ),
                new VerbalMetricsDto(
                        7,
                        5.0,
                        3.7,
                        0.74,
                        84.0,
                        113.51,
                        1,
                        Map.of("eh", 1),
                        1,
                        0.8,
                        0.8,
                        true
                )
        );
    }

    private String createTestUserAndToken(String email) {
        UserAccount user = users.save(new UserAccount(
                "Test", "Speaker", email, passwordEncoder.encode("Password123!"), UserRole.USER));
        subscriptions.save(new Subscription(user, plans.freePlan()));
        return jwtService.createToken(user);
    }
}

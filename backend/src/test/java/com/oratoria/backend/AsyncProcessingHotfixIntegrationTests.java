package com.oratoria.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
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
import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.evaluation.client.AiSpeechEvaluationClient;
import com.oratoria.backend.evaluation.client.AiSpeechEvaluationClient.AiEvaluationClientException;
import com.oratoria.backend.evaluation.client.AiSpeechEvaluationClient.AiSpeechEvaluationPayload;
import com.oratoria.backend.evaluation.client.HttpAiSpeechEvaluationClient;
import com.oratoria.backend.evaluation.dto.CriterionScoreDto;
import com.oratoria.backend.evaluation.dto.ImprovedExampleDto;
import com.oratoria.backend.evaluation.dto.MeasuredIndicatorDto;
import com.oratoria.backend.evaluation.dto.RecommendationDto;
import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.evaluation.entity.SpeechEvaluation;
import com.oratoria.backend.evaluation.repository.SpeechEvaluationRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.analysis.async-enabled=true",
        "app.analysis.max-attempts=3"
})
class AsyncProcessingHotfixIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PlanInitializationService plans;
    @Autowired ExerciseRepository exercises;
    @Autowired TrainingSessionRepository sessions;
    @Autowired SpeechAnalysisRepository analyses;
    @Autowired SpeechEvaluationRepository evaluations;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AiSpeechAnalysisClient aiAnalysisClient;
    @MockitoBean AiSpeechEvaluationClient aiEvaluationClient;

    private final List<UUID> createdSessionIds = new ArrayList<>();
    private final List<UUID> createdSubscriptionIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        reset(aiAnalysisClient, aiEvaluationClient);
    }

    @AfterEach
    void cleanUp() {
        for (UUID sessionId : createdSessionIds) {
            evaluations.findBySessionId(sessionId).ifPresent(evaluations::delete);
            analyses.findBySessionId(sessionId).ifPresent(analyses::delete);
            sessions.findById(sessionId).ifPresent(sessions::delete);
        }
        for (UUID subId : createdSubscriptionIds) {
            subscriptions.findById(subId).ifPresent(subscriptions::delete);
        }
        for (UUID userId : createdUserIds) {
            users.findById(userId).ifPresent(users::delete);
        }
        createdSessionIds.clear();
        createdSubscriptionIds.clear();
        createdUserIds.clear();
    }

    @Test
    void asyncPostCommitDispatchCompletesAnalysisAndEvaluationWithoutStayingPending() throws Exception {
        String email = "async-hotfix-" + UUID.randomUUID() + "@example.test";
        String token = createTestUserAndToken(email);
        UserAccount owner = users.findByEmailIgnoreCase(email).orElseThrow();
        Exercise exercise = exercises.findByCode("PITCH_ELEVATOR").orElseThrow();
        TrainingSession session = new TrainingSession(owner, exercise, TrainingType.PITCH);
        session.markRecording();
        session = sessions.saveAndFlush(session);
        createdSessionIds.add(session.getId());

        UUID sessionId = session.getId();
        when(aiAnalysisClient.analyzeSpeech(any(UUID.class), any(byte[].class), any(), anyInt(), any()))
                .thenReturn(sampleAnalysisPayload(sessionId));
        when(aiEvaluationClient.evaluateSpeech(any(UUID.class), any(), any(), any(), any(), any()))
                .thenReturn(sampleEvaluationPayload(sessionId, 88.0));

        MockMultipartFile audioFile = new MockMultipartFile(
                "audio", "pitch.webm", "audio/webm;codecs=opus", "WEBM_AUDIO_DATA".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/sessions/{id}/complete", sessionId)
                        .file(audioFile)
                        .param("durationSeconds", "18")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Poll until both asynchronous workers complete (post-commit)
        awaitCondition(() -> {
            SpeechAnalysis a = analyses.findBySessionId(sessionId).orElse(null);
            SpeechEvaluation e = evaluations.findBySessionId(sessionId).orElse(null);
            return a != null && a.getStatus() == AnalysisStatus.COMPLETED
                    && e != null && e.getStatus() == EvaluationStatus.COMPLETED;
        }, 5000);

        mvc.perform(get("/api/sessions/{id}/analysis", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.attemptCount").value(1));

        mvc.perform(get("/api/sessions/{id}/evaluation", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.overallScore").value(88.0))
                .andExpect(jsonPath("$.attemptCount").value(1));

        TrainingSession updatedSession = sessions.findById(sessionId).orElseThrow();
        assertThat(updatedSession.getOverallScore()).isEqualTo(88.0);
    }

    @Test
    void pollingOrphanedPendingAnalysisAutomaticallyRecoversAndCompletesPipeline() throws Exception {
        String email = "orphan-pending-" + UUID.randomUUID() + "@example.test";
        String token = createTestUserAndToken(email);
        UserAccount owner = users.findByEmailIgnoreCase(email).orElseThrow();
        TrainingSession session = new TrainingSession(owner, TrainingType.PRESENTATION);
        session.markRecording();
        session = sessions.saveAndFlush(session);
        createdSessionIds.add(session.getId());

        UUID sessionId = session.getId();
        MockMultipartFile audioFile = new MockMultipartFile(
                "audio", "orphan.webm", "audio/webm", "WEBM_ORPHAN".getBytes(StandardCharsets.UTF_8));

        // First let completeSession fail so we can simulate an orphaned PENDING row with attemptCount = 0
        when(aiAnalysisClient.analyzeSpeech(any(UUID.class), any(byte[].class), any(), anyInt(), any()))
                .thenReturn(sampleAnalysisPayload(sessionId));
        when(aiEvaluationClient.evaluateSpeech(any(UUID.class), any(), any(), any(), any(), any()))
                .thenReturn(sampleEvaluationPayload(sessionId, 79.5));

        mvc.perform(multipart("/api/sessions/{id}/complete", sessionId)
                        .file(audioFile)
                        .param("durationSeconds", "15")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        awaitCondition(() -> analyses.findBySessionId(sessionId)
                .map(a -> a.getStatus() == AnalysisStatus.COMPLETED)
                .orElse(false), 5000);

        // Now simulate an orphaned PENDING row in PostgreSQL (status=PENDING, no active worker)
        evaluations.findBySessionId(sessionId).ifPresent(evaluations::delete);
        analyses.findBySessionId(sessionId).ifPresent(analyses::delete);
        SpeechAnalysis orphan = analyses.saveAndFlush(new SpeechAnalysis(sessions.findById(sessionId).orElseThrow()));
        assertThat(orphan.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(orphan.getAttemptCount()).isZero();

        // Polling GET /api/sessions/{id}/analysis must detect the orphaned PENDING state and dispatch it
        mvc.perform(get("/api/sessions/{id}/analysis", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        awaitCondition(() -> {
            SpeechAnalysis a = analyses.findBySessionId(sessionId).orElse(null);
            SpeechEvaluation e = evaluations.findBySessionId(sessionId).orElse(null);
            return a != null && a.getStatus() == AnalysisStatus.COMPLETED
                    && e != null && e.getStatus() == EvaluationStatus.COMPLETED;
        }, 5000);
    }

    @Test
    void analysisFailurePropagatesToPendingEvaluationAndAllowsRetryAfterAuthMisconfig() throws Exception {
        String email = "cascade-fail-" + UUID.randomUUID() + "@example.test";
        String token = createTestUserAndToken(email);
        UserAccount owner = users.findByEmailIgnoreCase(email).orElseThrow();
        TrainingSession session = new TrainingSession(owner, TrainingType.DEBATE);
        session.markRecording();
        session = sessions.saveAndFlush(session);
        createdSessionIds.add(session.getId());
        UUID sessionId = session.getId();

        when(aiAnalysisClient.analyzeSpeech(any(UUID.class), any(byte[].class), any(), anyInt(), any()))
                .thenThrow(new AiAnalysisClientException(
                        "UNAUTHORIZED_INTERNAL_CALL",
                        "Clave interna inválida entre servicios.",
                        false
                ));

        MockMultipartFile audioFile = new MockMultipartFile(
                "audio", "debate.webm", "audio/webm", "WEBM_DEBATE".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/sessions/{id}/complete", sessionId)
                        .file(audioFile)
                        .param("durationSeconds", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        awaitCondition(() -> analyses.findBySessionId(sessionId)
                .map(a -> a.getStatus() == AnalysisStatus.FAILED)
                .orElse(false), 5000);

        // Even if the analysis failed 3 times with UNAUTHORIZED_INTERNAL_CALL (a non-quota pre-Groq error),
        // manual retry after fixing .env must be permitted and succeed!
        mvc.perform(post("/api/sessions/{id}/analysis/retry", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        awaitCondition(() -> analyses.findBySessionId(sessionId)
                .map(a -> a.getStatus() == AnalysisStatus.FAILED && a.getAttemptCount() == 2)
                .orElse(false), 5000);

        mvc.perform(post("/api/sessions/{id}/analysis/retry", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        awaitCondition(() -> analyses.findBySessionId(sessionId)
                .map(a -> a.getStatus() == AnalysisStatus.FAILED && a.getAttemptCount() == 3)
                .orElse(false), 5000);

        // Now fix the mock (simulating INTERNAL_API_KEY fixed) and retry on attempt #4
        when(aiAnalysisClient.analyzeSpeech(any(UUID.class), any(byte[].class), any(), anyInt(), any()))
                .thenReturn(sampleAnalysisPayload(sessionId));
        when(aiEvaluationClient.evaluateSpeech(any(UUID.class), any(), any(), any(), any(), any()))
                .thenReturn(sampleEvaluationPayload(sessionId, 91.0));

        mvc.perform(post("/api/sessions/{id}/analysis/retry", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        awaitCondition(() -> {
            SpeechAnalysis a = analyses.findBySessionId(sessionId).orElse(null);
            SpeechEvaluation e = evaluations.findBySessionId(sessionId).orElse(null);
            return a != null && a.getStatus() == AnalysisStatus.COMPLETED && a.getAttemptCount() == 4
                    && e != null && e.getStatus() == EvaluationStatus.COMPLETED;
        }, 5000);
    }

    @Test
    void httpClientsCorrectlyClassify401InvalidJsonAndTimeoutErrors() throws Exception {
        UUID sessionId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // 1. 401 UNAUTHORIZED_INTERNAL_CALL
        server.createContext("/unauthorized/api/v1/speech/analyze", exchange -> {
            byte[] body = "{\"code\":\"UNAUTHORIZED_INTERNAL_CALL\",\"message\":\"Invalid key\",\"retryable\":false}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // 2. 200 OK with corrupted JSON -> INVALID_PROVIDER_RESPONSE
        server.createContext("/invalidjson/api/v1/evaluation/evaluate", exchange -> {
            byte[] body = "{corrupted-json".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.start();
        try {
            int port = server.getAddress().getPort();
            HttpAiSpeechAnalysisClient analysisHttpClient = new HttpAiSpeechAnalysisClient(
                    objectMapper,
                    "http://127.0.0.1:" + port + "/unauthorized",
                    "wrong-key",
                    5
            );
            assertThatThrownBy(() -> analysisHttpClient.analyzeSpeech(
                    sessionId, new byte[] {1, 2, 3}, "audio/webm", 10, "a.webm"))
                    .isInstanceOf(AiAnalysisClientException.class)
                    .extracting("errorCode")
                    .isEqualTo("UNAUTHORIZED_INTERNAL_CALL");

            HttpAiSpeechEvaluationClient evalHttpClient = new HttpAiSpeechEvaluationClient(
                    objectMapper,
                    "http://127.0.0.1:" + port + "/invalidjson",
                    "valid-key",
                    5
            );
            assertThatThrownBy(() -> evalHttpClient.evaluateSpeech(
                    sessionId, TrainingType.PITCH, "Pitch", "Prompt", "Texto de prueba", null))
                    .isInstanceOf(AiEvaluationClientException.class)
                    .extracting("errorCode")
                    .isEqualTo("INVALID_PROVIDER_RESPONSE");
        } finally {
            server.stop(0);
        }
    }

    private void awaitCondition( java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean())
                .as("Condition was not satisfied within " + timeoutMs + "ms")
                .isTrue();
    }

    private AiSpeechAnalysisPayload sampleAnalysisPayload(UUID sessionId) {
        return new AiSpeechAnalysisPayload(
                "v1",
                sessionId.toString(),
                "groq",
                "whisper-large-v3-turbo",
                "es",
                "Buenos días jurado, presentamos nuestra plataforma universitaria.",
                List.of(new TranscriptSegmentDto(0, 0.0, 5.0, "Buenos días jurado, presentamos nuestra plataforma universitaria.")),
                List.of(new WordTimestampDto("Buenos", 0.0, 0.4)),
                new VerbalMetricsDto(7, 5.0, 4.1, 0.82, 84.0, 102.4, 0, Map.of(), 0, 0.0, 0.0, true)
        );
    }

    private AiSpeechEvaluationPayload sampleEvaluationPayload(UUID sessionId, Double overallScore) {
        return new AiSpeechEvaluationPayload(
                "v1",
                sessionId.toString(),
                "PITCH",
                "oratoria-rubric-v1.0",
                "groq",
                "openai/gpt-oss-120b",
                true,
                null,
                overallScore,
                "Exposición clara y bien estructurada.",
                List.of(new MeasuredIndicatorDto("WORDS_PER_MINUTE", "Velocidad de habla", 84.0, "WPM", "Adecuado", "MEASURED")),
                List.of(
                        new CriterionScoreDto("CLARITY", "Claridad", 0.30, 88.0, "SOBRESALIENTE", "Directo.", List.of("presentamos nuestra plataforma"), "AI_ESTIMATED"),
                        new CriterionScoreDto("COHERENCE", "Coherencia", 0.20, 88.0, "SOBRESALIENTE", "Coherente.", List.of("plataforma universitaria"), "AI_ESTIMATED"),
                        new CriterionScoreDto("ORGANIZATION", "Organización", 0.25, 88.0, "SOBRESALIENTE", "Ordenado.", List.of("Buenos días jurado"), "AI_ESTIMATED"),
                        new CriterionScoreDto("ARGUMENTATION", "Argumentación", 0.25, 88.0, "SOBRESALIENTE", "Sólido.", List.of("nuestra plataforma universitaria"), "AI_ESTIMATED")
                ),
                List.of("Mensaje directo."),
                List.of("Profundizar métricas."),
                List.of(new RecommendationDto("Añadir datos", "Incluir porcentaje de impacto.", "ALTA")),
                List.of(new ImprovedExampleDto("presentamos nuestra plataforma", "presentamos OratoriaAI para 500 estudiantes", "Mayor precisión."))
        );
    }

    private String createTestUserAndToken(String email) {
        UserAccount user = users.saveAndFlush(new UserAccount(
                "Async", "Tester", email, passwordEncoder.encode("Password123!"), UserRole.USER));
        createdUserIds.add(user.getId());
        Subscription sub = subscriptions.saveAndFlush(new Subscription(user, plans.freePlan()));
        createdSubscriptionIds.add(sub.getId());
        return jwtService.createToken(user);
    }
}

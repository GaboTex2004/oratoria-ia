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
import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient.AiSpeechAnalysisPayload;
import com.oratoria.backend.analysis.dto.TranscriptSegmentDto;
import com.oratoria.backend.analysis.dto.VerbalMetricsDto;
import com.oratoria.backend.analysis.dto.WordTimestampDto;
import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.evaluation.client.AiSpeechEvaluationClient;
import com.oratoria.backend.evaluation.client.AiSpeechEvaluationClient.AiEvaluationClientException;
import com.oratoria.backend.evaluation.client.AiSpeechEvaluationClient.AiSpeechEvaluationPayload;
import com.oratoria.backend.evaluation.dto.CriterionScoreDto;
import com.oratoria.backend.evaluation.dto.ImprovedExampleDto;
import com.oratoria.backend.evaluation.dto.MeasuredIndicatorDto;
import com.oratoria.backend.evaluation.dto.RecommendationDto;
import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.evaluation.entity.SpeechEvaluation;
import com.oratoria.backend.evaluation.repository.SpeechEvaluationRepository;
import com.oratoria.backend.evaluation.service.SpeechEvaluationRecoveryRunner;
import com.oratoria.backend.exercise.entity.Exercise;
import com.oratoria.backend.exercise.repository.ExerciseRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpeechEvaluationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PlanInitializationService plans;
    @Autowired ExerciseRepository exercises;
    @Autowired TrainingSessionRepository sessions;
    @Autowired SpeechEvaluationRepository evaluations;
    @Autowired SpeechEvaluationRecoveryRunner recoveryRunner;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @MockitoBean AiSpeechAnalysisClient aiAnalysisClient;
    @MockitoBean AiSpeechEvaluationClient aiEvaluationClient;

    @BeforeEach
    void setUp() {
        reset(aiAnalysisClient, aiEvaluationClient);
    }

    @Test
    void completingSessionPersistsEvaluationUpdatesOverallScoreAndPreventsDuplicateQuotaConsumption() throws Exception {
        String token = createTestUserAndToken("eval-owner@example.test");
        UserAccount owner = users.findByEmailIgnoreCase("eval-owner@example.test").orElseThrow();
        Exercise exercise = exercises.findByCode("PITCH_ELEVATOR").orElseThrow();
        TrainingSession session = new TrainingSession(owner, exercise, TrainingType.PITCH);
        session.markRecording();
        session = sessions.save(session);

        when(aiAnalysisClient.analyzeSpeech(any(UUID.class), any(byte[].class), any(), anyInt(), any()))
                .thenReturn(sampleAnalysisPayload(session.getId()));
        when(aiEvaluationClient.evaluateSpeech(any(UUID.class), any(), any(), any(), any(), any()))
                .thenReturn(sampleEvaluationPayload(session.getId(), true, 84.5));

        MockMultipartFile audioFile = new MockMultipartFile(
                "audio", "pitch.webm", "audio/webm", "WEBM_AUDIO".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/sessions/{id}/complete", session.getId())
                        .file(audioFile)
                        .param("durationSeconds", "35")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.overallScore").value(84.5));

        mvc.perform(get("/api/sessions/{id}/evaluation", session.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rubricVersion").value("oratoria-rubric-v1.0"))
                .andExpect(jsonPath("$.provider").value("groq"))
                .andExpect(jsonPath("$.model").value("llama-3.3-70b-versatile"))
                .andExpect(jsonPath("$.sufficientEvidence").value(true))
                .andExpect(jsonPath("$.overallScore").value(84.5))
                .andExpect(jsonPath("$.measuredIndicators.length()").value(1))
                .andExpect(jsonPath("$.measuredIndicators[0].source").value("MEASURED"))
                .andExpect(jsonPath("$.criteriaScores.length()").value(4))
                .andExpect(jsonPath("$.criteriaScores[0].judgmentType").value("AI_ESTIMATED"))
                .andExpect(jsonPath("$.criteriaScores[0].evidenceQuotes[0]").value("presentamos nuestra solución innovadora"))
                .andExpect(jsonPath("$.strengths.length()").value(1))
                .andExpect(jsonPath("$.areasForImprovement.length()").value(1))
                .andExpect(jsonPath("$.recommendations.length()").value(1))
                .andExpect(jsonPath("$.improvedExamples.length()").value(1));

        // Calling retry on an already COMPLETED evaluation does not invoke Groq LLM again
        mvc.perform(post("/api/sessions/{id}/evaluation/retry", session.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(aiEvaluationClient, times(1)).evaluateSpeech(any(), any(), any(), any(), any(), any());
    }

    @Test
    void evaluationFailureAllowsRetryAndEnforcesOwnerAuthorization() throws Exception {
        String tokenA = createTestUserAndToken("eval-user-a@example.test");
        String tokenB = createTestUserAndToken("eval-user-b@example.test");
        UserAccount userA = users.findByEmailIgnoreCase("eval-user-a@example.test").orElseThrow();
        TrainingSession session = sessions.save(new TrainingSession(userA, TrainingType.DEBATE));

        when(aiAnalysisClient.analyzeSpeech(any(UUID.class), any(byte[].class), any(), anyInt(), any()))
                .thenReturn(sampleAnalysisPayload(session.getId()));
        when(aiEvaluationClient.evaluateSpeech(any(UUID.class), any(), any(), any(), any(), any()))
                .thenThrow(new AiEvaluationClientException("LLM_PROVIDER_RATE_LIMIT", "Cuota temporal alcanzada.", true));

        MockMultipartFile audioFile = new MockMultipartFile(
                "audio", "debate.webm", "audio/webm", "AUDIO_DEBATE".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/sessions/{id}/complete", session.getId())
                        .file(audioFile)
                        .param("durationSeconds", "25")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(get("/api/sessions/{id}/evaluation", session.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("LLM_PROVIDER_RATE_LIMIT"))
                .andExpect(jsonPath("$.retryable").value(true));

        // User B is forbidden from viewing or retrying User A's evaluation
        mvc.perform(get("/api/sessions/{id}/evaluation", session.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/sessions/{id}/evaluation/retry", session.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Provider recovers on retry
        when(aiEvaluationClient.evaluateSpeech(any(UUID.class), any(), any(), any(), any(), any()))
                .thenReturn(sampleEvaluationPayload(session.getId(), false, null));

        mvc.perform(post("/api/sessions/{id}/evaluation/retry", session.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.sufficientEvidence").value(false))
                .andExpect(jsonPath("$.overallScore").isEmpty());
    }

    @Test
    void evaluationRecoveryRunnerMarksInterruptedTasksAsFailedRetryable() {
        UserAccount user = users.save(new UserAccount(
                "Eval", "Recovery", "eval-recovery@example.test", passwordEncoder.encode("Pass123!"), UserRole.USER));
        subscriptions.save(new Subscription(user, plans.freePlan()));
        TrainingSession session = sessions.save(new TrainingSession(user, TrainingType.PRESENTATION));

        SpeechEvaluation interrupted = new SpeechEvaluation(session);
        interrupted.markProcessing();
        interrupted = evaluations.saveAndFlush(interrupted);

        recoveryRunner.run(new DefaultApplicationArguments());

        SpeechEvaluation recovered = evaluations.findById(interrupted.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(EvaluationStatus.FAILED);
        assertThat(recovered.getErrorCode()).isEqualTo("INTERRUPTED_BY_RESTART");
        assertThat(recovered.isRetryable()).isTrue();
    }

    private AiSpeechAnalysisPayload sampleAnalysisPayload(UUID sessionId) {
        return new AiSpeechAnalysisPayload(
                "v1",
                sessionId.toString(),
                "groq",
                "whisper-large-v3-turbo",
                "es",
                "Hola jurado, presentamos nuestra solución innovadora para estudiantes universitarios.",
                List.of(new TranscriptSegmentDto(0, 0.0, 5.0, "Hola jurado, presentamos nuestra solución innovadora.")),
                List.of(new WordTimestampDto("Hola", 0.0, 0.4)),
                new VerbalMetricsDto(9, 5.0, 4.2, 0.84, 108.0, 128.5, 0, Map.of(), 0, 0.0, 0.0, true)
        );
    }

    private AiSpeechEvaluationPayload sampleEvaluationPayload(
            UUID sessionId,
            boolean sufficient,
            Double overallScore) {
        return new AiSpeechEvaluationPayload(
                "v1",
                sessionId.toString(),
                "PITCH",
                "oratoria-rubric-v1.0",
                "groq",
                "llama-3.3-70b-versatile",
                sufficient,
                sufficient ? null : "Evidencia insuficiente para puntuar.",
                overallScore,
                "Propuesta clara y enfocada en el usuario universitario.",
                List.of(new MeasuredIndicatorDto("WORDS_PER_MINUTE", "Velocidad de habla", 108.0, "WPM", "Ritmo adecuado", "MEASURED")),
                List.of(
                        new CriterionScoreDto("CLARITY", "Claridad", 0.30, sufficient ? 86.0 : null, sufficient ? "SOBRESALIENTE" : "INSUFFICIENT_EVIDENCE", "Mensaje directo.", List.of("presentamos nuestra solución innovadora"), "AI_ESTIMATED"),
                        new CriterionScoreDto("COHERENCE", "Coherencia", 0.20, sufficient ? 84.0 : null, sufficient ? "COMPETENTE" : "INSUFFICIENT_EVIDENCE", "Alineado.", List.of("para estudiantes universitarios"), "AI_ESTIMATED"),
                        new CriterionScoreDto("ORGANIZATION", "Organización", 0.25, sufficient ? 82.0 : null, sufficient ? "COMPETENTE" : "INSUFFICIENT_EVIDENCE", "Buen inicio.", List.of("Hola jurado"), "AI_ESTIMATED"),
                        new CriterionScoreDto("ARGUMENTATION", "Argumentación", 0.25, sufficient ? 85.0 : null, sufficient ? "SOBRESALIENTE" : "INSUFFICIENT_EVIDENCE", "Justificado.", List.of("solución innovadora"), "AI_ESTIMATED")
                ),
                List.of("Enfoque claro en el segmento universitario."),
                List.of("Agregar un llamado a la acción cuantificable al cierre."),
                List.of(new RecommendationDto("Incluir métrica de impacto", "Cierra indicando cuántos estudiantes se benefician.", "ALTA")),
                List.of(new ImprovedExampleDto("presentamos nuestra solución", "presentamos OratoriaAI, que reduce en 40% las muletillas al exponer", "Aporta especificidad y valor medible."))
        );
    }

    private String createTestUserAndToken(String email) {
        UserAccount user = users.save(new UserAccount(
                "Eval", "Speaker", email, passwordEncoder.encode("Password123!"), UserRole.USER));
        subscriptions.save(new Subscription(user, plans.freePlan()));
        return jwtService.createToken(user);
    }
}

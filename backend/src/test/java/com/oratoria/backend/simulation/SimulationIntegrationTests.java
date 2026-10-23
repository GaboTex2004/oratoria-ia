package com.oratoria.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient;
import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient.AiSpeechAnalysisPayload;
import com.oratoria.backend.simulation.client.AiSimulationClient;
import com.oratoria.backend.simulation.dto.SimulationDtos.*;
import com.oratoria.backend.simulation.entity.*;
import com.oratoria.backend.simulation.repository.SimulationTurnRepository;
import com.oratoria.backend.simulation.service.SimulationService;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SimulationIntegrationTests {
    @Autowired SimulationService service;
    @Autowired UserAccountRepository users;
    @Autowired SimulationTurnRepository turns;

    @MockitoBean AiSpeechAnalysisClient speechClient;
    @MockitoBean AiSimulationClient simulationClient;

    @Test
    void audioToTranscriptToGeminiReplyIsPersistedOnceForSameIdempotencyKey() {
        UserAccount owner = users.save(new UserAccount(
                "Sim", "Owner", "sim-owner@example.test", "test-hash", UserRole.USER));
        var created = service.create(owner.getEmail(),
                new CreateRequest("job-interview", SimulationDifficulty.BASIC));

        when(speechClient.analyzeSpeech(any(), any(byte[].class), anyString(), anyInt(), anyString()))
                .thenReturn(new AiSpeechAnalysisPayload(
                        "v1", "turn", "groq", "whisper-large-v3-turbo", "es",
                        "Lideré un equipo y redujimos los errores.", List.of(), List.of(), null));
        when(simulationClient.generateReply(any())).thenReturn(new AiReplyResponse(
                "¿Cómo mediste esa reducción?", "PROBE", false, "resultados", "gemini", "gemini-3.8-flash"));

        MockMultipartFile audio = new MockMultipartFile(
                "audio", "answer.webm", "audio/webm",
                "TEST_WEBM_AUDIO".getBytes(StandardCharsets.UTF_8));
        String key = "idem-simulation-turn-1";

        var first = service.respond(owner.getEmail(), created.id(), key, audio, 8);
        var repeated = service.respond(owner.getEmail(), created.id(), key, audio, 8);

        assertThat(first.currentTurn()).isEqualTo(1);
        assertThat(repeated.turns()).hasSize(3);
        assertThat(turns.findBySimulationIdOrderBySequenceNumberAsc(created.id())).hasSize(3);
        verify(speechClient, times(1)).analyzeSpeech(any(), any(byte[].class), anyString(), anyInt(), anyString());
        verify(simulationClient, times(1)).generateReply(any());
    }

    @Test
    void geminiTransientFailurePreservesTranscriptAndIdempotentRetryResumesSameTurn() {
        UserAccount owner = users.save(new UserAccount(
                "Failed", "Turn", "failed-turn@example.test", "test-hash", UserRole.USER));
        var created = service.create(owner.getEmail(),
                new CreateRequest("job-interview", SimulationDifficulty.BASIC));
        String transcript = "La transcripción debe sobrevivir al rechazo de Gemini.";
        when(speechClient.analyzeSpeech(any(), any(byte[].class), anyString(), anyInt(), anyString()))
                .thenReturn(new AiSpeechAnalysisPayload(
                        "v1", "turn", "groq", "whisper-large-v3-turbo", "es",
                        transcript, List.of(), List.of(), null));
        when(simulationClient.generateReply(any()))
                .thenThrow(new AiSimulationClient.AiSimulationClientException(
                        "LLM_PROVIDER_UNAVAILABLE", "Gemini no está disponible.", true))
                .thenReturn(new AiReplyResponse(
                        "Continuemos.", "CONTINUE", false, "evidencia",
                        "gemini", "gemini-3.8-flash"));
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "answer.webm", "audio/webm",
                "TEST_WEBM_AUDIO".getBytes(StandardCharsets.UTF_8));
        String key = "idem-gemini-invalid-argument";

        assertThatThrownBy(() -> service.respond(owner.getEmail(), created.id(), key, audio, 8))
                .hasMessageContaining("Gemini no está disponible");
        var failed = turns.findBySimulationIdAndIdempotencyKey(created.id(), key).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(SimulationTurnStatus.FAILED);
        assertThat(failed.getText()).isEqualTo(transcript);

        var repeated = service.respond(owner.getEmail(), created.id(), key, audio, 8);
        assertThat(repeated.turns()).hasSize(3);
        assertThat(repeated.currentTurn()).isEqualTo(1);
        assertThat(turns.findBySimulationIdOrderBySequenceNumberAsc(created.id())).hasSize(3);
        verify(speechClient, times(1)).analyzeSpeech(any(), any(byte[].class), anyString(), anyInt(), anyString());
        verify(simulationClient, times(2)).generateReply(any());
    }

    @Test
    void simulationsRemainPrivateToTheirOwner() {
        UserAccount owner = users.save(new UserAccount(
                "Sim", "Owner", "private-owner@example.test", "test-hash", UserRole.USER));
        UserAccount stranger = users.save(new UserAccount(
                "Sim", "Stranger", "private-stranger@example.test", "test-hash", UserRole.USER));
        var created = service.create(owner.getEmail(),
                new CreateRequest("structured-debate", SimulationDifficulty.BASIC));

        assertThatThrownBy(() -> service.get(stranger.getEmail(), created.id()))
                .isInstanceOf(AccessDeniedException.class);
    }
}

package com.oratoria.backend.analysis.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient;
import com.oratoria.backend.session.service.AudioStorageService;
import com.oratoria.backend.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class VoiceCoachControllerTests {
    final AiSpeechAnalysisClient client = mock(AiSpeechAnalysisClient.class);
    final AudioStorageService limits = mock(AudioStorageService.class);
    final VoiceCoachController controller = new VoiceCoachController(client, limits);
    final MockMultipartFile audio = new MockMultipartFile("audio", "test.webm", "audio/webm;codecs=opus", new byte[]{1, 2});

    VoiceCoachControllerTests() {
        when(limits.getMaxDurationSeconds()).thenReturn(300);
        when(limits.getMaxSizeBytes()).thenReturn(100L);
    }

    @Test void requiresConsentAndValidDurationBeforeCallingTheModel() {
        assertThatThrownBy(() -> controller.analyze(audio, 10, false)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.analyze(audio, 301, true)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(client);
    }

    @Test void rejectsEmptyOversizedAndUnsupportedUploads() {
        assertThatThrownBy(() -> controller.analyze(new MockMultipartFile("audio", new byte[0]), 10, true)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.analyze(new MockMultipartFile("audio", "test", "text/plain", new byte[]{1}), 10, true)).isInstanceOf(BadRequestException.class);
        when(limits.getMaxSizeBytes()).thenReturn(1L);
        assertThatThrownBy(() -> controller.analyze(audio, 10, true)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(client);
    }

    @Test void forwardsTransientAudioWithoutUsingAudioStorage() throws Exception {
        var response = controller.analyze(audio, 10, true);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(client).analyzeSpeech(any(), eq(new byte[]{1, 2}), eq("audio/webm"), eq(10), eq("voice-practice"));
        verify(limits, never()).store(any(), any(), any(), any());
    }

    @Test void modelFailureReleasesSlotForRetry() throws Exception {
        when(client.analyzeSpeech(any(), any(), any(), anyInt(), any())).thenThrow(
                new AiSpeechAnalysisClient.AiAnalysisClientException("BUSY", "Servicio ocupado", true));
        assertThat(controller.analyze(audio, 10, true).getStatusCode().value()).isEqualTo(503);
        assertThat(controller.analyze(audio, 10, true).getStatusCode().value()).isEqualTo(503);
    }
}

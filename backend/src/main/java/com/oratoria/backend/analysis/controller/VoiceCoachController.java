package com.oratoria.backend.analysis.controller;

import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient;
import com.oratoria.backend.session.service.AudioStorageService;
import com.oratoria.backend.shared.exception.BadRequestException;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class VoiceCoachController {
    private static final Set<String> ALLOWED = Set.of("audio/webm", "audio/ogg", "audio/wav", "audio/mp4", "audio/mpeg");
    private final AiSpeechAnalysisClient client;
    private final AudioStorageService limits;
    private final Semaphore slot = new Semaphore(1);

    public VoiceCoachController(AiSpeechAnalysisClient client, AudioStorageService limits) {
        this.client = client;
        this.limits = limits;
    }

    @PostMapping(path = "/api/voice-coach/analyze", consumes = "multipart/form-data")
    public ResponseEntity<?> analyze(@RequestParam MultipartFile audio, @RequestParam int durationSeconds,
                                    @RequestParam boolean consent) throws IOException {
        if (!consent) throw new BadRequestException("Debes autorizar el procesamiento local del audio.");
        if (durationSeconds < 1 || durationSeconds > limits.getMaxDurationSeconds()) {
            throw new BadRequestException("La duración del audio supera los límites permitidos.");
        }
        if (audio.isEmpty() || audio.getSize() > limits.getMaxSizeBytes()) {
            throw new BadRequestException("Audio vacío o demasiado grande.");
        }
        String mime = audio.getContentType() == null ? "" : audio.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(mime)) throw new BadRequestException("Formato de audio no permitido.");
        if (!slot.tryAcquire()) return ResponseEntity.status(429).body(Map.of("message", "El entrenador de voz está ocupado. Reintenta en unos momentos."));
        try {
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(
                    client.analyzeSpeech(UUID.randomUUID(), audio.getBytes(), mime, durationSeconds, "voice-practice"));
        } catch (AiSpeechAnalysisClient.AiAnalysisClientException exception) {
            return ResponseEntity.status(exception.isRetryable() ? 503 : 400)
                    .body(Map.of("message", exception.getMessage(), "retryable", exception.isRetryable()));
        } finally {
            slot.release();
        }
    }
}

package com.oratoria.backend.analysis.service;

import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient;
import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient.AiAnalysisClientException;
import com.oratoria.backend.analysis.client.AiSpeechAnalysisClient.AiSpeechAnalysisPayload;
import com.oratoria.backend.analysis.dto.SpeechAnalysisResponse;
import com.oratoria.backend.analysis.dto.TranscriptSegmentDto;
import com.oratoria.backend.analysis.dto.VerbalMetricsDto;
import com.oratoria.backend.analysis.dto.WordTimestampDto;
import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.analysis.entity.SpeechAnalysis;
import com.oratoria.backend.analysis.repository.SpeechAnalysisRepository;
import com.oratoria.backend.auth.exception.InvalidCredentialsException;
import com.oratoria.backend.evaluation.service.SpeechEvaluationService;
import com.oratoria.backend.session.entity.SessionStatus;
import com.oratoria.backend.session.entity.TrainingSession;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.session.service.AudioStorageService;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class SpeechAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(SpeechAnalysisService.class);
    private static final Set<String> NON_QUOTA_ERROR_CODES = Set.of(
            "UNAUTHORIZED_INTERNAL_CALL",
            "INTERRUPTED_BY_RESTART",
            "INTERRUPTED_PROCESSING",
            "AI_SERVICE_UNAVAILABLE"
    );

    private final SpeechAnalysisRepository analyses;
    private final TrainingSessionRepository sessions;
    private final UserAccountRepository users;
    private final AudioStorageService audioStorage;
    private final AiSpeechAnalysisClient aiClient;
    private final SpeechEvaluationService evaluationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;
    private final boolean asyncEnabled;
    private final int maxAttempts;
    private final Set<UUID> activeJobs = ConcurrentHashMap.newKeySet();
    private final ExecutorService workerExecutor;

    public SpeechAnalysisService(
            SpeechAnalysisRepository analyses,
            TrainingSessionRepository sessions,
            UserAccountRepository users,
            AudioStorageService audioStorage,
            AiSpeechAnalysisClient aiClient,
            SpeechEvaluationService evaluationService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.analysis.async-enabled:true}") boolean asyncEnabled,
            @Value("${app.analysis.max-attempts:5}") int maxAttempts) {
        this.analyses = analyses;
        this.sessions = sessions;
        this.users = users;
        this.audioStorage = audioStorage;
        this.aiClient = aiClient;
        this.evaluationService = evaluationService;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.asyncEnabled = asyncEnabled;
        this.maxAttempts = Math.max(1, maxAttempts);
        AtomicInteger threadIndex = new AtomicInteger(1);
        this.workerExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "oratoria-analysis-worker-" + threadIndex.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdownExecutor() {
        workerExecutor.shutdown();
    }

    public SpeechAnalysis enqueueForCompletedSession(TrainingSession session) {
        SpeechAnalysis analysis = txTemplate.execute(status ->
                analyses.findBySessionId(session.getId())
                        .orElseGet(() -> {
                            SpeechAnalysis created = analyses.save(new SpeechAnalysis(session));
                            log.info(
                                    "Speech analysis queued [sessionId={} analysisId={} status=PENDING]",
                                    session.getId(),
                                    created.getId()
                            );
                            return created;
                        })
        );
        triggerDispatch(session.getId());
        return analyses.findBySessionId(session.getId()).orElse(analysis);
    }

    public Optional<AnalysisStatus> findStatusBySessionId(UUID sessionId) {
        return analyses.findBySessionId(sessionId).map(SpeechAnalysis::getStatus);
    }

    public SpeechAnalysisResponse getAnalysisForUserSession(String userEmail, UUID sessionId) {
        TrainingSession session = findOwnedSession(userEmail, sessionId);
        Optional<SpeechAnalysis> existing = analyses.findBySessionId(sessionId);
        if (existing.isEmpty()) {
            if (session.getStatus() == SessionStatus.COMPLETED && session.hasAudio()) {
                enqueueForCompletedSession(session);
                return toResponse(analyses.findBySessionId(sessionId).orElseThrow());
            }
            throw new ResourceNotFoundException("La sesión aún no tiene un análisis de voz registrado.");
        }

        SpeechAnalysis current = existing.get();
        if (current.getStatus() == AnalysisStatus.PENDING && !activeJobs.contains(sessionId)) {
            log.info(
                    "Recovering orphaned PENDING speech analysis [sessionId={} analysisId={}]",
                    sessionId,
                    current.getId()
            );
            triggerDispatch(sessionId);
            current = analyses.findBySessionId(sessionId).orElse(current);
        } else if (current.getStatus() == AnalysisStatus.PROCESSING && !activeJobs.contains(sessionId)) {
            log.warn(
                    "Recovering orphaned PROCESSING speech analysis without active worker [sessionId={} analysisId={}]",
                    sessionId,
                    current.getId()
            );
            current = txTemplate.execute(status -> {
                SpeechAnalysis entity = analyses.findBySessionId(sessionId).orElseThrow();
                if (entity.getStatus() == AnalysisStatus.PROCESSING) {
                    entity.markFailed(
                            "INTERRUPTED_PROCESSING",
                            "El análisis de voz en curso se interrumpió antes de finalizar. Puedes reintentarlo manualmente.",
                            true
                    );
                    return analyses.save(entity);
                }
                return entity;
            });
            evaluationService.markFailedDueToAnalysisFailure(sessionId, "INTERRUPTED_PROCESSING");
        }
        return toResponse(current);
    }

    public SpeechAnalysisResponse retryAnalysisForUserSession(String userEmail, UUID sessionId) {
        TrainingSession session = findOwnedSession(userEmail, sessionId);
        if (session.getStatus() != SessionStatus.COMPLETED || !session.hasAudio()) {
            throw new BadRequestException("Solo se puede analizar una sesión finalizada con grabación de audio.");
        }

        SpeechAnalysis current = txTemplate.execute(status -> {
            SpeechAnalysis entity = analyses.findBySessionId(sessionId)
                    .orElseGet(() -> analyses.save(new SpeechAnalysis(session)));
            if (entity.getStatus() == AnalysisStatus.COMPLETED
                    || (entity.getStatus() == AnalysisStatus.PROCESSING && activeJobs.contains(sessionId))) {
                return entity;
            }
            boolean isNonQuotaError = entity.getErrorCode() != null
                    && NON_QUOTA_ERROR_CODES.contains(entity.getErrorCode());
            if (entity.getAttemptCount() >= maxAttempts && !isNonQuotaError) {
                throw new BadRequestException(
                        "Se alcanzó el límite máximo de intentos (" + maxAttempts + ") para analizar esta sesión.");
            }
            entity.markPendingForRetry();
            return analyses.save(entity);
        });

        if (current != null && current.getStatus() == AnalysisStatus.PENDING) {
            triggerDispatch(sessionId);
        }
        return toResponse(analyses.findBySessionId(sessionId).orElseThrow());
    }

    private void triggerDispatch(UUID sessionId) {
        if (!asyncEnabled) {
            executeAnalysisJob(sessionId);
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("Registering afterCommit dispatch for speech analysis [sessionId={}]", sessionId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> executeAnalysisJob(sessionId), workerExecutor);
                }
            });
        } else {
            CompletableFuture.runAsync(() -> executeAnalysisJob(sessionId), workerExecutor);
        }
    }

    public void executeAnalysisJob(UUID sessionId) {
        if (!activeJobs.add(sessionId)) {
            log.debug("Skipping duplicate speech analysis dispatch [sessionId={}]", sessionId);
            return;
        }
        long startNanos = System.nanoTime();
        try {
            JobInput input = txTemplate.execute(status -> {
                SpeechAnalysis analysis = analyses.findBySessionId(sessionId).orElse(null);
                if (analysis == null) {
                    log.warn("Speech analysis job found no entity in database [sessionId={}]", sessionId);
                    return null;
                }
                if (analysis.getStatus() == AnalysisStatus.COMPLETED) return null;
                boolean isNonQuotaError = analysis.getErrorCode() != null
                        && NON_QUOTA_ERROR_CODES.contains(analysis.getErrorCode());
                if (analysis.getAttemptCount() >= maxAttempts && !isNonQuotaError) {
                    analysis.markFailed(
                            "MAX_ATTEMPTS_REACHED",
                            "Se agotó el número máximo de intentos de análisis.",
                            false
                    );
                    analyses.save(analysis);
                    return null;
                }
                TrainingSession session = analysis.getSession();
                if (!session.hasAudio()) {
                    analysis.markFailed("MISSING_AUDIO", "La sesión no posee un archivo de audio válido.", false);
                    analyses.save(analysis);
                    return null;
                }
                analysis.markProcessing();
                analyses.save(analysis);
                log.info(
                        "Speech analysis started [sessionId={} attempt={} transition=PENDING->PROCESSING]",
                        sessionId,
                        analysis.getAttemptCount()
                );
                return new JobInput(
                        session.getId(),
                        session.getAudioStoragePath(),
                        session.getAudioMimeType() != null ? session.getAudioMimeType() : "audio/webm",
                        session.getDurationSeconds() != null ? session.getDurationSeconds() : 1,
                        session.getAudioOriginalFilename()
                );
            });

            if (input == null) {
                return;
            }

            byte[] audioBytes = readAudioBytes(input.storagePath());
            AiSpeechAnalysisPayload payload = aiClient.analyzeSpeech(
                    input.sessionId(),
                    audioBytes,
                    input.mimeType(),
                    input.durationSeconds(),
                    input.filename()
            );

            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            txTemplate.executeWithoutResult(status -> {
                SpeechAnalysis analysis = analyses.findBySessionId(sessionId).orElseThrow();
                VerbalMetricsDto m = payload.metrics();
                analysis.markCompleted(
                        payload.schemaVersion(),
                        payload.provider(),
                        payload.model(),
                        payload.language(),
                        payload.transcriptText(),
                        writeJsonSafely(payload.segments() != null ? payload.segments() : List.of()),
                        writeJsonSafely(payload.words() != null ? payload.words() : List.of()),
                        writeJsonSafely(m != null && m.fillerWordCounts() != null ? m.fillerWordCounts() : Map.of()),
                        m != null ? m.wordCount() : 0,
                        m != null ? m.recordingDurationSeconds() : (double) input.durationSeconds(),
                        m != null ? m.estimatedSpokenDurationSeconds() : null,
                        m != null ? m.spokenTimeRatio() : null,
                        m != null ? m.wordsPerMinute() : 0.0,
                        m != null ? m.spokenWordsPerMinute() : null,
                        m != null ? m.fillerWordsTotal() : 0,
                        m != null ? m.estimatedPauseCount() : null,
                        m != null ? m.totalPauseDurationSeconds() : null,
                        m != null ? m.longestPauseSeconds() : null,
                        m != null && m.timestampsAvailable()
                );
                analyses.save(analysis);
                log.info(
                        "Speech analysis persisted [sessionId={} attempt={} elapsedMs={} transition=PROCESSING->COMPLETED words={}]",
                        sessionId,
                        analysis.getAttemptCount(),
                        elapsedMs,
                        m != null ? m.wordCount() : 0
                );
            });

            evaluationService.enqueueForAnalyzedSession(sessionId);
        } catch (AiAnalysisClientException clientEx) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.warn(
                    "Speech analysis failed [sessionId={} elapsedMs={} code={} retryable={}]",
                    sessionId,
                    elapsedMs,
                    clientEx.getErrorCode(),
                    clientEx.isRetryable()
            );
            recordFailure(sessionId, clientEx.getErrorCode(), clientEx.getMessage(), clientEx.isRetryable());
        } catch (Exception unexpected) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.error(
                    "Unexpected error during speech analysis [sessionId={} elapsedMs={}]",
                    sessionId,
                    elapsedMs,
                    unexpected
            );
            recordFailure(
                    sessionId,
                    "ANALYSIS_INTERNAL_ERROR",
                    "Ocurrió un error al procesar la grabación de audio.",
                    true
            );
        } finally {
            activeJobs.remove(sessionId);
        }
    }

    private void recordFailure(UUID sessionId, String code, String message, boolean providerRetryable) {
        txTemplate.executeWithoutResult(status -> {
            analyses.findBySessionId(sessionId).ifPresent(analysis -> {
                boolean canRetry = providerRetryable && analysis.getAttemptCount() < maxAttempts;
                analysis.markFailed(code, message, canRetry);
                analyses.save(analysis);
            });
        });
        evaluationService.markFailedDueToAnalysisFailure(sessionId, code);
    }

    private byte[] readAudioBytes(String storagePath) {
        Resource resource = audioStorage.loadAsResource(storagePath);
        try (var in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException exception) {
            throw new AiAnalysisClientException(
                    "AUDIO_READ_ERROR",
                    "No se pudo leer el archivo de audio almacenado para la sesión.",
                    false
            );
        }
    }

    private TrainingSession findOwnedSession(String userEmail, UUID sessionId) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new InvalidCredentialsException();
        }
        UserAccount user = users.findByEmailIgnoreCase(userEmail.trim())
                .filter(UserAccount::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);
        TrainingSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("La sesión solicitada no existe."));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("No tienes permiso para acceder a los resultados de esta sesión.");
        }
        return session;
    }

    private SpeechAnalysisResponse toResponse(SpeechAnalysis analysis) {
        List<TranscriptSegmentDto> segments = readJsonList(
                analysis.getSegmentsJson(),
                new TypeReference<List<TranscriptSegmentDto>>() {}
        );
        List<WordTimestampDto> words = readJsonList(
                analysis.getWordsJson(),
                new TypeReference<List<WordTimestampDto>>() {}
        );
        Map<String, Integer> fillerCounts = readJsonMap(analysis.getFillerCountsJson());

        VerbalMetricsDto metrics = null;
        if (analysis.getStatus() == AnalysisStatus.COMPLETED && analysis.getWordCount() != null) {
            metrics = new VerbalMetricsDto(
                    analysis.getWordCount(),
                    analysis.getRecordingDurationSeconds() != null ? analysis.getRecordingDurationSeconds() : 0.0,
                    analysis.getEstimatedSpokenDurationSeconds(),
                    analysis.getSpokenTimeRatio(),
                    analysis.getWordsPerMinute() != null ? analysis.getWordsPerMinute() : 0.0,
                    analysis.getSpokenWordsPerMinute(),
                    analysis.getFillerWordsTotal() != null ? analysis.getFillerWordsTotal() : 0,
                    fillerCounts,
                    analysis.getEstimatedPauseCount(),
                    analysis.getTotalPauseDurationSeconds(),
                    analysis.getLongestPauseSeconds(),
                    Boolean.TRUE.equals(analysis.getTimestampsAvailable())
            );
        }

        return new SpeechAnalysisResponse(
                analysis.getId(),
                analysis.getSession().getId(),
                analysis.getStatus(),
                analysis.getSchemaVersion(),
                analysis.getProvider(),
                analysis.getModel(),
                analysis.getLanguage(),
                analysis.getTranscriptText(),
                segments,
                words,
                metrics,
                analysis.getAttemptCount(),
                analysis.getErrorCode(),
                analysis.getErrorMessage(),
                analysis.isRetryable(),
                analysis.getCreatedAt(),
                analysis.getStartedAt(),
                analysis.getCompletedAt(),
                analysis.getUpdatedAt()
        );
    }

    private String writeJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private <T> List<T> readJsonList(String rawJson, TypeReference<List<T>> typeRef) {
        if (rawJson == null || rawJson.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(rawJson, typeRef);
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    private Map<String, Integer> readJsonMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(rawJson, new TypeReference<Map<String, Integer>>() {});
        } catch (Exception exception) {
            return Collections.emptyMap();
        }
    }

    private record JobInput(
            UUID sessionId,
            String storagePath,
            String mimeType,
            int durationSeconds,
            String filename) {
    }
}

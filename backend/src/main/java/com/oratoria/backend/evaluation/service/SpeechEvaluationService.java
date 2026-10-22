package com.oratoria.backend.evaluation.service;

import com.oratoria.backend.analysis.dto.VerbalMetricsDto;
import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.analysis.entity.SpeechAnalysis;
import com.oratoria.backend.analysis.repository.SpeechAnalysisRepository;
import com.oratoria.backend.auth.exception.InvalidCredentialsException;
import com.oratoria.backend.evaluation.client.AiSpeechEvaluationClient;
import com.oratoria.backend.evaluation.client.AiSpeechEvaluationClient.AiEvaluationClientException;
import com.oratoria.backend.evaluation.client.AiSpeechEvaluationClient.AiSpeechEvaluationPayload;
import com.oratoria.backend.evaluation.dto.CriterionScoreDto;
import com.oratoria.backend.evaluation.dto.ImprovedExampleDto;
import com.oratoria.backend.evaluation.dto.MeasuredIndicatorDto;
import com.oratoria.backend.evaluation.dto.RecommendationDto;
import com.oratoria.backend.evaluation.dto.SpeechEvaluationResponse;
import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.evaluation.entity.SpeechEvaluation;
import com.oratoria.backend.evaluation.repository.SpeechEvaluationRepository;
import com.oratoria.backend.exercise.entity.Exercise;
import com.oratoria.backend.session.entity.TrainingSession;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import com.oratoria.backend.training.TrainingType;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.repository.UserAccountRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class SpeechEvaluationService {
    private static final Logger log = LoggerFactory.getLogger(SpeechEvaluationService.class);
    private static final Set<String> NON_QUOTA_ERROR_CODES = Set.of(
            "UNAUTHORIZED_INTERNAL_CALL",
            "INTERRUPTED_BY_RESTART",
            "INTERRUPTED_PROCESSING",
            "ANALYSIS_DEPENDENCY_FAILED",
            "EVALUATION_SERVICE_UNAVAILABLE"
    );

    private final SpeechEvaluationRepository evaluations;
    private final SpeechAnalysisRepository analyses;
    private final TrainingSessionRepository sessions;
    private final UserAccountRepository users;
    private final AiSpeechEvaluationClient aiEvaluationClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;
    private final boolean asyncEnabled;
    private final int maxAttempts;
    private final Set<UUID> activeJobs = ConcurrentHashMap.newKeySet();
    private final ExecutorService workerExecutor;

    public SpeechEvaluationService(
            SpeechEvaluationRepository evaluations,
            SpeechAnalysisRepository analyses,
            TrainingSessionRepository sessions,
            UserAccountRepository users,
            AiSpeechEvaluationClient aiEvaluationClient,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.analysis.async-enabled:true}") boolean asyncEnabled,
            @Value("${app.analysis.max-attempts:5}") int maxAttempts) {
        this.evaluations = evaluations;
        this.analyses = analyses;
        this.sessions = sessions;
        this.users = users;
        this.aiEvaluationClient = aiEvaluationClient;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.asyncEnabled = asyncEnabled;
        this.maxAttempts = Math.max(1, maxAttempts);
        AtomicInteger threadIndex = new AtomicInteger(1);
        this.workerExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "oratoria-evaluation-worker-" + threadIndex.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdownExecutor() {
        workerExecutor.shutdown();
    }

    public Optional<EvaluationStatus> findStatusBySessionId(UUID sessionId) {
        return evaluations.findBySessionId(sessionId).map(SpeechEvaluation::getStatus);
    }

    public SpeechEvaluation enqueueForAnalyzedSession(UUID sessionId) {
        SpeechEvaluation evaluation = txTemplate.execute(status -> {
            TrainingSession session = sessions.findById(sessionId).orElse(null);
            if (session == null) return null;
            SpeechEvaluation existing = evaluations.findBySessionId(sessionId).orElse(null);
            if (existing == null) {
                SpeechEvaluation created = evaluations.save(new SpeechEvaluation(session));
                log.info(
                        "Speech evaluation queued [sessionId={} evaluationId={} status=PENDING]",
                        sessionId,
                        created.getId()
                );
                return created;
            }
            if (existing.getStatus() == EvaluationStatus.FAILED
                    && "ANALYSIS_DEPENDENCY_FAILED".equals(existing.getErrorCode())) {
                existing.markPendingForRetry();
                return evaluations.save(existing);
            }
            return existing;
        });
        if (evaluation == null) {
            return null;
        }
        if (evaluation.getStatus() == EvaluationStatus.PENDING) {
            triggerDispatch(sessionId);
        }
        return evaluations.findBySessionId(sessionId).orElse(evaluation);
    }

    public void markFailedDueToAnalysisFailure(UUID sessionId, String upstreamCode) {
        txTemplate.executeWithoutResult(status -> {
            evaluations.findBySessionId(sessionId).ifPresent(evaluation -> {
                if (evaluation.getStatus() == EvaluationStatus.PENDING
                        || evaluation.getStatus() == EvaluationStatus.PROCESSING) {
                    evaluation.markFailed(
                            "ANALYSIS_DEPENDENCY_FAILED",
                            "La evaluación discursiva no pudo iniciarse porque la transcripción de voz previa falló ("
                                    + upstreamCode + ").",
                            true
                    );
                    evaluations.save(evaluation);
                    log.info(
                            "Speech evaluation marked failed due to upstream analysis failure [sessionId={} upstreamCode={}]",
                            sessionId,
                            upstreamCode
                    );
                }
            });
        });
    }

    public SpeechEvaluationResponse getEvaluationForUserSession(String userEmail, UUID sessionId) {
        TrainingSession session = findOwnedSession(userEmail, sessionId);
        Optional<SpeechAnalysis> speechAnalysis = analyses.findBySessionId(sessionId);
        Optional<SpeechEvaluation> existing = evaluations.findBySessionId(sessionId);

        if (existing.isPresent()) {
            SpeechEvaluation current = existing.get();
            if (current.getStatus() == EvaluationStatus.PENDING) {
                if (speechAnalysis.isPresent() && speechAnalysis.get().getStatus() == AnalysisStatus.COMPLETED) {
                    if (!activeJobs.contains(sessionId)) {
                        log.info(
                                "Recovering orphaned PENDING speech evaluation [sessionId={} evaluationId={}]",
                                sessionId,
                                current.getId()
                        );
                        triggerDispatch(sessionId);
                        current = evaluations.findBySessionId(sessionId).orElse(current);
                    }
                } else if (speechAnalysis.isPresent() && speechAnalysis.get().getStatus() == AnalysisStatus.FAILED) {
                    markFailedDueToAnalysisFailure(sessionId, speechAnalysis.get().getErrorCode());
                    current = evaluations.findBySessionId(sessionId).orElse(current);
                }
            } else if (current.getStatus() == EvaluationStatus.PROCESSING && !activeJobs.contains(sessionId)) {
                log.warn(
                        "Recovering orphaned PROCESSING speech evaluation without active worker [sessionId={} evaluationId={}]",
                        sessionId,
                        current.getId()
                );
                current = txTemplate.execute(status -> {
                    SpeechEvaluation entity = evaluations.findBySessionId(sessionId).orElseThrow();
                    if (entity.getStatus() == EvaluationStatus.PROCESSING) {
                        entity.markFailed(
                                "INTERRUPTED_PROCESSING",
                                "La evaluación del discurso se interrumpió antes de finalizar. Puedes reintentarla manualmente.",
                                true
                        );
                        return evaluations.save(entity);
                    }
                    return entity;
                });
            }
            return toResponse(current);
        }

        if (speechAnalysis.isPresent() && speechAnalysis.get().getStatus() == AnalysisStatus.COMPLETED) {
            enqueueForAnalyzedSession(sessionId);
            return toResponse(evaluations.findBySessionId(sessionId).orElseThrow());
        }

        if (speechAnalysis.isPresent()
                && (speechAnalysis.get().getStatus() == AnalysisStatus.PENDING
                || speechAnalysis.get().getStatus() == AnalysisStatus.PROCESSING)) {
            SpeechEvaluation pending = txTemplate.execute(status ->
                    evaluations.findBySessionId(sessionId)
                            .orElseGet(() -> evaluations.save(new SpeechEvaluation(session)))
            );
            return toResponse(pending);
        }

        throw new ResourceNotFoundException(
                "La evaluación inteligente estará disponible una vez que finalice la transcripción de voz.");
    }

    public SpeechEvaluationResponse retryEvaluationForUserSession(String userEmail, UUID sessionId) {
        TrainingSession session = findOwnedSession(userEmail, sessionId);
        SpeechAnalysis speechAnalysis = analyses.findBySessionId(sessionId)
                .orElseThrow(() -> new BadRequestException(
                        "Primero debe completarse la transcripción de voz antes de evaluar el discurso."));
        if (speechAnalysis.getStatus() != AnalysisStatus.COMPLETED) {
            throw new BadRequestException(
                    "La transcripción de voz aún no está completada para esta sesión.");
        }

        SpeechEvaluation current = txTemplate.execute(status -> {
            SpeechEvaluation entity = evaluations.findBySessionId(sessionId)
                    .orElseGet(() -> evaluations.save(new SpeechEvaluation(session)));
            if (entity.getStatus() == EvaluationStatus.COMPLETED
                    || (entity.getStatus() == EvaluationStatus.PROCESSING && activeJobs.contains(sessionId))) {
                return entity;
            }
            boolean isNonQuotaError = entity.getErrorCode() != null
                    && NON_QUOTA_ERROR_CODES.contains(entity.getErrorCode());
            if (entity.getAttemptCount() >= maxAttempts && !isNonQuotaError) {
                throw new BadRequestException(
                        "Se alcanzó el límite máximo de intentos (" + maxAttempts + ") para evaluar esta sesión.");
            }
            entity.markPendingForRetry();
            return evaluations.save(entity);
        });

        if (current != null && current.getStatus() == EvaluationStatus.PENDING) {
            triggerDispatch(sessionId);
        }
        return toResponse(evaluations.findBySessionId(sessionId).orElseThrow());
    }

    private void triggerDispatch(UUID sessionId) {
        if (!asyncEnabled) {
            executeEvaluationJob(sessionId);
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("Registering afterCommit dispatch for speech evaluation [sessionId={}]", sessionId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> executeEvaluationJob(sessionId), workerExecutor);
                }
            });
        } else {
            CompletableFuture.runAsync(() -> executeEvaluationJob(sessionId), workerExecutor);
        }
    }

    public void executeEvaluationJob(UUID sessionId) {
        if (!activeJobs.add(sessionId)) {
            log.debug("Skipping duplicate speech evaluation dispatch [sessionId={}]", sessionId);
            return;
        }
        long startNanos = System.nanoTime();
        try {
            EvaluationJobInput input = txTemplate.execute(status -> {
                SpeechEvaluation evaluation = evaluations.findBySessionId(sessionId).orElse(null);
                if (evaluation == null) {
                    log.warn("Speech evaluation job found no entity in database [sessionId={}]", sessionId);
                    return null;
                }
                if (evaluation.getStatus() == EvaluationStatus.COMPLETED) {
                    return null;
                }
                boolean isNonQuotaError = evaluation.getErrorCode() != null
                        && NON_QUOTA_ERROR_CODES.contains(evaluation.getErrorCode());
                if (evaluation.getAttemptCount() >= maxAttempts && !isNonQuotaError) {
                    evaluation.markFailed(
                            "MAX_ATTEMPTS_REACHED",
                            "Se agotó el número máximo de intentos de evaluación.",
                            false
                    );
                    evaluations.save(evaluation);
                    return null;
                }

                SpeechAnalysis analysis = analyses.findBySessionId(sessionId).orElse(null);
                if (analysis == null || analysis.getStatus() != AnalysisStatus.COMPLETED) {
                    log.debug(
                            "Speech evaluation deferred until speech analysis completes [sessionId={} analysisStatus={}]",
                            sessionId,
                            analysis != null ? analysis.getStatus() : "NONE"
                    );
                    return null;
                }

                TrainingSession session = evaluation.getSession();
                Exercise exercise = session.getExercise();
                String exerciseTitle = exercise != null ? exercise.getTitle() : null;
                String exercisePrompt = exercise != null ? exercise.getPrompt() : null;

                Map<String, Integer> fillerCounts = readJsonMap(analysis.getFillerCountsJson());
                VerbalMetricsDto metrics = new VerbalMetricsDto(
                        analysis.getWordCount() != null ? analysis.getWordCount() : 0,
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

                evaluation.markProcessing();
                evaluations.save(evaluation);
                log.info(
                        "Speech evaluation started [sessionId={} attempt={} transition=PENDING->PROCESSING]",
                        sessionId,
                        evaluation.getAttemptCount()
                );
                return new EvaluationJobInput(
                        sessionId,
                        session.getTrainingType(),
                        exerciseTitle,
                        exercisePrompt,
                        analysis.getTranscriptText() != null ? analysis.getTranscriptText() : "",
                        metrics
                );
            });

            if (input == null) {
                return;
            }

            AiSpeechEvaluationPayload payload = aiEvaluationClient.evaluateSpeech(
                    input.sessionId(),
                    input.trainingType(),
                    input.exerciseTitle(),
                    input.exercisePrompt(),
                    input.transcriptText(),
                    input.metrics()
            );

            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            txTemplate.executeWithoutResult(status -> {
                SpeechEvaluation evaluation = evaluations.findBySessionId(sessionId).orElseThrow();
                evaluation.markCompleted(
                        payload.schemaVersion(),
                        payload.rubricVersion(),
                        payload.provider(),
                        payload.model(),
                        payload.sufficientEvidence(),
                        payload.insufficientEvidenceReason(),
                        payload.overallScore(),
                        payload.executiveSummary(),
                        writeJsonSafely(payload.measuredIndicators() != null ? payload.measuredIndicators() : List.of()),
                        writeJsonSafely(payload.criteriaScores() != null ? payload.criteriaScores() : List.of()),
                        writeJsonSafely(payload.strengths() != null ? payload.strengths() : List.of()),
                        writeJsonSafely(payload.areasForImprovement() != null ? payload.areasForImprovement() : List.of()),
                        writeJsonSafely(payload.recommendations() != null ? payload.recommendations() : List.of()),
                        writeJsonSafely(payload.improvedExamples() != null ? payload.improvedExamples() : List.of())
                );
                evaluations.save(evaluation);

                if (payload.sufficientEvidence() && payload.overallScore() != null) {
                    TrainingSession session = evaluation.getSession();
                    session.updateOverallScore(payload.overallScore());
                    sessions.save(session);
                }
                log.info(
                        "Speech evaluation persisted [sessionId={} attempt={} elapsedMs={} transition=PROCESSING->COMPLETED overallScore={}]",
                        sessionId,
                        evaluation.getAttemptCount(),
                        elapsedMs,
                        payload.overallScore()
                );
            });
        } catch (AiEvaluationClientException clientEx) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.warn(
                    "Speech evaluation failed [sessionId={} elapsedMs={} code={} retryable={}]",
                    sessionId,
                    elapsedMs,
                    clientEx.getErrorCode(),
                    clientEx.isRetryable()
            );
            recordFailure(sessionId, clientEx.getErrorCode(), clientEx.getMessage(), clientEx.isRetryable());
        } catch (Exception unexpected) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.error(
                    "Unexpected error during speech evaluation [sessionId={} elapsedMs={}]",
                    sessionId,
                    elapsedMs,
                    unexpected
            );
            recordFailure(
                    sessionId,
                    "EVALUATION_INTERNAL_ERROR",
                    "Ocurrió un error al generar la evaluación inteligente del discurso.",
                    true
            );
        } finally {
            activeJobs.remove(sessionId);
        }
    }

    private void recordFailure(UUID sessionId, String code, String message, boolean providerRetryable) {
        txTemplate.executeWithoutResult(status -> {
            evaluations.findBySessionId(sessionId).ifPresent(evaluation -> {
                boolean canRetry = providerRetryable && evaluation.getAttemptCount() < maxAttempts;
                evaluation.markFailed(code, message, canRetry);
                evaluations.save(evaluation);
            });
        });
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
            throw new AccessDeniedException("No tienes permiso para acceder a la evaluación de esta sesión.");
        }
        return session;
    }

    private SpeechEvaluationResponse toResponse(SpeechEvaluation evaluation) {
        List<MeasuredIndicatorDto> measuredIndicators = readJsonList(
                evaluation.getMeasuredIndicatorsJson(),
                new TypeReference<List<MeasuredIndicatorDto>>() {}
        );
        List<CriterionScoreDto> criteriaScores = readJsonList(
                evaluation.getCriteriaScoresJson(),
                new TypeReference<List<CriterionScoreDto>>() {}
        );
        List<String> strengths = readJsonList(
                evaluation.getStrengthsJson(),
                new TypeReference<List<String>>() {}
        );
        List<String> areasForImprovement = readJsonList(
                evaluation.getAreasForImprovementJson(),
                new TypeReference<List<String>>() {}
        );
        List<RecommendationDto> recommendations = readJsonList(
                evaluation.getRecommendationsJson(),
                new TypeReference<List<RecommendationDto>>() {}
        );
        List<ImprovedExampleDto> improvedExamples = readJsonList(
                evaluation.getImprovedExamplesJson(),
                new TypeReference<List<ImprovedExampleDto>>() {}
        );

        return new SpeechEvaluationResponse(
                evaluation.getId(),
                evaluation.getSession().getId(),
                evaluation.getStatus(),
                evaluation.getSchemaVersion(),
                evaluation.getRubricVersion(),
                evaluation.getProvider(),
                evaluation.getModel(),
                evaluation.getSufficientEvidence(),
                evaluation.getInsufficientEvidenceReason(),
                evaluation.getOverallScore(),
                evaluation.getExecutiveSummary(),
                measuredIndicators,
                criteriaScores,
                strengths,
                areasForImprovement,
                recommendations,
                improvedExamples,
                evaluation.getAttemptCount(),
                evaluation.getErrorCode(),
                evaluation.getErrorMessage(),
                evaluation.isRetryable(),
                evaluation.getCreatedAt(),
                evaluation.getStartedAt(),
                evaluation.getCompletedAt(),
                evaluation.getUpdatedAt()
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

    private record EvaluationJobInput(
            UUID sessionId,
            TrainingType trainingType,
            String exerciseTitle,
            String exercisePrompt,
            String transcriptText,
            VerbalMetricsDto metrics) {
    }
}

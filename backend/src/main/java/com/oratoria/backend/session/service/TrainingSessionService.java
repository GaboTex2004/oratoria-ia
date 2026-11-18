package com.oratoria.backend.session.service;

import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.analysis.service.SpeechAnalysisService;
import com.oratoria.backend.auth.exception.InvalidCredentialsException;
import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.evaluation.service.SpeechEvaluationService;
import com.oratoria.backend.exercise.entity.Exercise;
import com.oratoria.backend.exercise.service.ExerciseService;
import com.oratoria.backend.style.CommunicationStyleCatalog;
import com.oratoria.backend.subscription.entity.Feature;
import com.oratoria.backend.subscription.service.FeatureAccessService;
import com.oratoria.backend.session.dto.CreateSessionRequest;
import com.oratoria.backend.session.dto.SessionResponse;
import com.oratoria.backend.session.entity.SessionStatus;
import com.oratoria.backend.session.entity.TrainingSession;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.session.service.AudioStorageService.StoredAudio;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import com.oratoria.backend.training.TrainingType;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TrainingSessionService {
    private final TrainingSessionRepository sessions;
    private final UserAccountRepository users;
    private final ExerciseService exercises;
    private final AudioStorageService audioStorage;
    private final SpeechAnalysisService speechAnalysisService;
    private final SpeechEvaluationService speechEvaluationService;
    private final com.oratoria.backend.subscription.service.QuotaService quotas;
    private final FeatureAccessService features;
    private final CommunicationStyleCatalog styles;

    public TrainingSessionService(TrainingSessionRepository sessions,
                                  UserAccountRepository users,
                                  ExerciseService exercises,
                                  AudioStorageService audioStorage,
                                  SpeechAnalysisService speechAnalysisService,
                                  SpeechEvaluationService speechEvaluationService,
                                  com.oratoria.backend.subscription.service.QuotaService quotas,
                                  FeatureAccessService features,
                                  CommunicationStyleCatalog styles) {
        this.features = features;
        this.styles = styles;
        this.sessions = sessions;
        this.users = users;
        this.exercises = exercises;
        this.audioStorage = audioStorage;
        this.speechAnalysisService = speechAnalysisService;
        this.speechEvaluationService = speechEvaluationService;
        this.quotas = quotas;
    }

    @Transactional
    public SessionResponse createSession(String userEmail, CreateSessionRequest request) {
        UserAccount user = findUser(userEmail);
        if (request == null || (request.exerciseId() == null && request.trainingType() == null)) {
            throw new BadRequestException("Debes seleccionar un ejercicio o tipo de entrenamiento.");
        }

        Exercise exercise = null;
        TrainingType trainingType = request.trainingType();
        if (request.exerciseId() != null) {
            exercise = exercises.findActiveEntity(request.exerciseId());
            if (trainingType != null && trainingType != exercise.getTrainingType()) {
                throw new BadRequestException("El tipo de entrenamiento no coincide con el ejercicio seleccionado.");
            }
            trainingType = exercise.getTrainingType();
        }

        // Plan features are checked before the quota so a locked attempt does not consume it.
        String style = exercise != null ? exercise.getCommunicationStyle() : null;
        if (request.communicationStyle() != null) {
            String requested = styles.require(request.communicationStyle()).code();
            if (style != null && !style.equals(requested)) {
                throw new BadRequestException("El ejercicio pertenece a otro estilo de comunicación.");
            }
            style = requested;
        }
        if (trainingType == TrainingType.IMPROVISATION) features.require(user.getId(), Feature.IMPROVISATION);
        if (style != null) features.require(user.getId(), Feature.COMMUNICATION_STYLES);
        quotas.checkCreation(user.getId(), false);
        TrainingSession session = new TrainingSession(user, exercise, trainingType);
        session.assignStyle(style);
        session.markRecording();
        return toResponse(sessions.save(session));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listUserSessions(String userEmail) {
        UserAccount user = findUser(userEmail);
        return sessions.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse getUserSession(String userEmail, UUID sessionId) {
        UserAccount user = findUser(userEmail);
        TrainingSession session = findOwnedSession(user, sessionId);
        return toResponse(session);
    }

    @Transactional
    public SessionResponse completeSession(String userEmail, UUID sessionId,
                                           MultipartFile audioFile, Integer durationSeconds) {
        UserAccount user = findUser(userEmail);
        TrainingSession session = findOwnedSession(user, sessionId);
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new BadRequestException("La sesión ya fue finalizada anteriormente.");
        }

        StoredAudio storedAudio = audioStorage.store(user.getId(), session.getId(), audioFile, durationSeconds);
        session.completeWithAudio(
                durationSeconds,
                storedAudio.storagePath(),
                storedAudio.mimeType(),
                storedAudio.sizeBytes(),
                storedAudio.originalFilename()
        );
        TrainingSession saved = sessions.saveAndFlush(session);
        speechAnalysisService.enqueueForCompletedSession(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SessionAudioResource loadSessionAudio(String userEmail, UUID sessionId) {
        UserAccount user = findUser(userEmail);
        TrainingSession session = findOwnedSession(user, sessionId);
        if (!session.hasAudio()) {
            throw new ResourceNotFoundException("Esta sesión aún no tiene una grabación de audio disponible.");
        }
        Resource resource = audioStorage.loadAsResource(session.getAudioStoragePath());
        String mimeType = session.getAudioMimeType() != null ? session.getAudioMimeType() : "audio/webm";
        return new SessionAudioResource(resource, mimeType, session.getAudioOriginalFilename());
    }

    private UserAccount findUser(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidCredentialsException();
        }
        return users.findByEmailIgnoreCase(email.trim())
                .filter(UserAccount::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);
    }

    private TrainingSession findOwnedSession(UserAccount user, UUID sessionId) {
        TrainingSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("La sesión solicitada no existe."));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("No tienes permiso para acceder a esta sesión.");
        }
        return session;
    }

    private SessionResponse toResponse(TrainingSession session) {
        Exercise exercise = session.getExercise();
        AnalysisStatus analysisStatus = speechAnalysisService
                .findStatusBySessionId(session.getId())
                .orElse(null);
        EvaluationStatus evaluationStatus = speechEvaluationService
                .findStatusBySessionId(session.getId())
                .orElse(null);
        return new SessionResponse(
                session.getId(),
                session.getTrainingType(),
                session.getStatus(),
                analysisStatus,
                evaluationStatus,
                exercise != null ? exercise.getId() : null,
                exercise != null ? exercise.getTitle() : null,
                session.getCreatedAt(),
                session.getCompletedAt(),
                session.getDurationSeconds(),
                session.getOverallScore(),
                session.hasAudio(),
                session.getAudioMimeType(),
                session.getAudioSizeBytes(),
                session.getCommunicationStyle()
        );
    }

    public record SessionAudioResource(Resource resource, String mimeType, String filename) {
    }
}

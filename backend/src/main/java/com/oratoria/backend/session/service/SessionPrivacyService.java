package com.oratoria.backend.session.service;

import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.analysis.repository.SpeechAnalysisRepository;
import com.oratoria.backend.body.BodyAnalysisRepository;
import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.evaluation.repository.SpeechEvaluationRepository;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionPrivacyService {
    private final TrainingSessionRepository sessions;
    private final SpeechAnalysisRepository analyses;
    private final SpeechEvaluationRepository evaluations;
    private final BodyAnalysisRepository body;
    private final AudioStorageService storage;
    private final JdbcTemplate jdbc;
    public SessionPrivacyService(TrainingSessionRepository sessions, SpeechAnalysisRepository analyses,
            SpeechEvaluationRepository evaluations, BodyAnalysisRepository body, AudioStorageService storage, JdbcTemplate jdbc) {
        this.sessions = sessions; this.analyses = analyses; this.evaluations = evaluations;
        this.body = body; this.storage = storage; this.jdbc = jdbc;
    }

    @Transactional
    public void erase(String email, UUID id) {
        var session = sessions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada."));
        if (!session.getUser().getEmail().equalsIgnoreCase(email)) throw new AccessDeniedException("La sesión pertenece a otro usuario.");
        var analysis = analyses.findBySessionId(id);
        var evaluation = evaluations.findBySessionId(id);
        if (analysis.filter(item -> item.getStatus() == AnalysisStatus.PENDING || item.getStatus() == AnalysisStatus.PROCESSING).isPresent()
                || evaluation.filter(item -> item.getStatus() == EvaluationStatus.PENDING || item.getStatus() == EvaluationStatus.PROCESSING).isPresent()) {
            throw new BadRequestException("Espera a que termine el procesamiento antes de eliminar los datos.");
        }
        if (session.hasAudio()) jdbc.update("insert into media_deletion_jobs(id, storage_path) values (?, ?)", UUID.randomUUID(), session.getAudioStoragePath());
        body.findBySessionId(id).ifPresent(body::delete);
        evaluation.ifPresent(evaluations::delete);
        analysis.ifPresent(analyses::delete);
        session.clearPrivateData();
        sessions.save(session);
    }

    @Scheduled(fixedDelay = 30000)
    public void deleteQueuedFiles() {
        jdbc.queryForList("select id, storage_path from media_deletion_jobs order by created_at limit 50").forEach(job -> {
            try {
                storage.delete((String) job.get("storage_path"));
                jdbc.update("delete from media_deletion_jobs where id = ?", job.get("id"));
            } catch (java.io.IOException | RuntimeException exception) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Media deletion pending [jobId={} reason={}]", job.get("id"), exception.getClass().getSimpleName());
            }
        });
    }
}

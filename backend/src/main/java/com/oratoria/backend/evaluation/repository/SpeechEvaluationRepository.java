package com.oratoria.backend.evaluation.repository;

import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.evaluation.entity.SpeechEvaluation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeechEvaluationRepository extends JpaRepository<SpeechEvaluation, UUID> {
    Optional<SpeechEvaluation> findBySessionId(UUID sessionId);

    List<SpeechEvaluation> findByStatusIn(Collection<EvaluationStatus> statuses);
}

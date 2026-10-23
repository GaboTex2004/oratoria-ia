package com.oratoria.backend.session.repository;

import com.oratoria.backend.session.entity.TrainingSession;
import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.session.entity.SessionStatus;
import com.oratoria.backend.training.TrainingType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {
    List<TrainingSession> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Reads the progress dataset in one query. The projection deliberately omits
     * transcripts, word timestamps, audio paths and recommendation payloads.
     */
    @Query("""
            select s.id as sessionId,
                   ex.title as exerciseTitle,
                   s.trainingType as trainingType,
                   s.createdAt as createdAt,
                   s.completedAt as completedAt,
                   s.durationSeconds as durationSeconds,
                   s.status as sessionStatus,
                   a.status as analysisStatus,
                   a.wordCount as wordCount,
                   a.wordsPerMinute as wordsPerMinute,
                   a.spokenWordsPerMinute as spokenWordsPerMinute,
                   a.fillerWordsTotal as fillerWordsTotal,
                   a.fillerCountsJson as fillerCountsJson,
                   a.estimatedPauseCount as estimatedPauseCount,
                   a.spokenTimeRatio as spokenTimeRatio,
                   e.status as evaluationStatus,
                   e.sufficientEvidence as sufficientEvidence,
                   e.overallScore as evaluationOverallScore,
                   e.criteriaScoresJson as criteriaScoresJson,
                   e.strengthsJson as strengthsJson,
                   e.areasForImprovementJson as areasForImprovementJson
              from TrainingSession s
              left join s.exercise ex
              left join SpeechAnalysis a on a.session = s
              left join SpeechEvaluation e on e.session = s
             where s.user.id = :userId
             order by s.createdAt asc, s.id asc
            """)
    List<ProgressSessionProjection> findProgressRowsByUserId(@Param("userId") UUID userId);

    interface ProgressSessionProjection {
        UUID getSessionId();
        String getExerciseTitle();
        TrainingType getTrainingType();
        Instant getCreatedAt();
        Instant getCompletedAt();
        Integer getDurationSeconds();
        SessionStatus getSessionStatus();
        AnalysisStatus getAnalysisStatus();
        Integer getWordCount();
        Double getWordsPerMinute();
        Double getSpokenWordsPerMinute();
        Integer getFillerWordsTotal();
        String getFillerCountsJson();
        Integer getEstimatedPauseCount();
        Double getSpokenTimeRatio();
        EvaluationStatus getEvaluationStatus();
        Boolean getSufficientEvidence();
        Double getEvaluationOverallScore();
        String getCriteriaScoresJson();
        String getStrengthsJson();
        String getAreasForImprovementJson();
    }
}

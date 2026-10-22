package com.oratoria.backend.analysis.repository;

import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.analysis.entity.SpeechAnalysis;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeechAnalysisRepository extends JpaRepository<SpeechAnalysis, UUID> {
    Optional<SpeechAnalysis> findBySessionId(UUID sessionId);
    List<SpeechAnalysis> findByStatusIn(Collection<AnalysisStatus> statuses);
}

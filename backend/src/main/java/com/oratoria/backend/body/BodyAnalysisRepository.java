package com.oratoria.backend.body;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BodyAnalysisRepository extends JpaRepository<BodyAnalysis, UUID> {
    Optional<BodyAnalysis> findBySessionId(UUID sessionId);
}

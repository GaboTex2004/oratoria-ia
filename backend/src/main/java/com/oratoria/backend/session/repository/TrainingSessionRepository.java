package com.oratoria.backend.session.repository;

import com.oratoria.backend.session.entity.TrainingSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {
    List<TrainingSession> findByUserIdOrderByCreatedAtDesc(UUID userId);
}

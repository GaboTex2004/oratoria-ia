package com.oratoria.backend.simulation.repository;
import com.oratoria.backend.simulation.entity.SimulationSession;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.oratoria.backend.simulation.entity.SimulationStatus;
public interface SimulationSessionRepository extends JpaRepository<SimulationSession,UUID>{
 List<SimulationSession> findByUserIdOrderByCreatedAtDesc(UUID userId);
 List<SimulationSession> findByStatus(SimulationStatus status);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SimulationSession s where s.id=:id") Optional<SimulationSession> findLockedById(@Param("id") UUID id);
}

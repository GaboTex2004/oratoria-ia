package com.oratoria.backend.simulation.repository;
import com.oratoria.backend.simulation.entity.SimulationTurn;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SimulationTurnRepository extends JpaRepository<SimulationTurn,UUID>{
 List<SimulationTurn> findBySimulationIdOrderBySequenceNumberAsc(UUID simulationId);
 Optional<SimulationTurn> findBySimulationIdAndIdempotencyKey(UUID simulationId,String key);
 long countBySimulationId(UUID simulationId);
}

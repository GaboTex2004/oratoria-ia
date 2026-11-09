package com.oratoria.backend.simulation.service;

import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import com.oratoria.backend.simulation.entity.SimulationStatus;
import com.oratoria.backend.simulation.repository.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimulationPrivacyService {
    private final SimulationSessionRepository sessions;
    private final SimulationTurnRepository turns;
    private final JdbcTemplate jdbc;
    public SimulationPrivacyService(SimulationSessionRepository sessions, SimulationTurnRepository turns, JdbcTemplate jdbc) {
        this.sessions = sessions; this.turns = turns; this.jdbc = jdbc;
    }
    @Transactional
    public void erase(String email, UUID id) {
        var session = sessions.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Simulación no encontrada."));
        if (!session.getUser().getEmail().equalsIgnoreCase(email)) throw new AccessDeniedException("La simulación pertenece a otro usuario.");
        if (session.getStatus() == SimulationStatus.PROCESSING) throw new BadRequestException("Espera a que termine el procesamiento.");
        turns.findBySimulationIdOrderBySequenceNumberAsc(id).forEach(turn -> {
            if (turn.hasAudio()) jdbc.update("insert into media_deletion_jobs(id, storage_path) values (?, ?)", UUID.randomUUID(), turn.getAudioStoragePath());
            turn.clearPrivateData(); turns.save(turn);
        });
        session.clearPrivateData(); sessions.save(session);
    }
}

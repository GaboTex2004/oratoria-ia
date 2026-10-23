package com.oratoria.backend.simulation.service;
import com.oratoria.backend.simulation.entity.*;
import com.oratoria.backend.simulation.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SimulationRecoveryRunner implements ApplicationRunner {
 private final SimulationSessionRepository sessions; private final SimulationTurnRepository turns;
 public SimulationRecoveryRunner(SimulationSessionRepository sessions,SimulationTurnRepository turns){this.sessions=sessions;this.turns=turns;}
 @Override @Transactional public void run(ApplicationArguments args){for(SimulationSession session:sessions.findByStatus(SimulationStatus.PROCESSING)){turns.findBySimulationIdOrderBySequenceNumberAsc(session.getId()).stream().filter(t->t.getStatus()==SimulationTurnStatus.PROCESSING).forEach(t->{t.fail("INTERRUPTED_PROCESSING","El procesamiento se interrumpió al reiniciar el servidor. Puedes grabar un nuevo turno.");turns.save(t);});session.restoreAfterFailure("INTERRUPTED_PROCESSING","La operación se interrumpió al reiniciar el servidor. Puedes reintentarla.");sessions.save(session);}}
}

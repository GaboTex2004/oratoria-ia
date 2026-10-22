package com.oratoria.backend.evaluation.service;

import com.oratoria.backend.evaluation.entity.EvaluationStatus;
import com.oratoria.backend.evaluation.entity.SpeechEvaluation;
import com.oratoria.backend.evaluation.repository.SpeechEvaluationRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(35)
public class SpeechEvaluationRecoveryRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SpeechEvaluationRecoveryRunner.class);

    private final SpeechEvaluationRepository evaluations;

    public SpeechEvaluationRecoveryRunner(SpeechEvaluationRepository evaluations) {
        this.evaluations = evaluations;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SpeechEvaluation> interrupted = evaluations.findByStatusIn(
                List.of(EvaluationStatus.PENDING, EvaluationStatus.PROCESSING)
        );
        if (interrupted.isEmpty()) {
            return;
        }
        for (SpeechEvaluation item : interrupted) {
            item.markFailed(
                    "INTERRUPTED_BY_RESTART",
                    "La evaluación del discurso se interrumpió por reinicio del servidor. Puedes reintentarla.",
                    true
            );
        }
        evaluations.saveAll(interrupted);
        log.info("Recuperadas {} tareas de evaluación discursiva interrumpidas tras reinicio.", interrupted.size());
    }
}

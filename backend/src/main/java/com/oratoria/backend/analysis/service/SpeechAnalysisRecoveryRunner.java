package com.oratoria.backend.analysis.service;

import com.oratoria.backend.analysis.entity.AnalysisStatus;
import com.oratoria.backend.analysis.entity.SpeechAnalysis;
import com.oratoria.backend.analysis.repository.SpeechAnalysisRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(30)
public class SpeechAnalysisRecoveryRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SpeechAnalysisRecoveryRunner.class);
    private final SpeechAnalysisRepository analyses;

    public SpeechAnalysisRecoveryRunner(SpeechAnalysisRepository analyses) {
        this.analyses = analyses;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SpeechAnalysis> interrupted = analyses.findByStatusIn(
                List.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING)
        );
        if (interrupted.isEmpty()) {
            return;
        }
        for (SpeechAnalysis analysis : interrupted) {
            analysis.markFailed(
                    "INTERRUPTED_BY_RESTART",
                    "El análisis en curso fue interrumpido por un reinicio del servidor. Puedes reintentarlo manualmente.",
                    true
            );
        }
        analyses.saveAll(interrupted);
        log.info("Recuperadas {} tareas de análisis interrumpidas tras reinicio.", interrupted.size());
    }
}

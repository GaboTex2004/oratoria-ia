package com.oratoria.backend.user.preferences;

import com.oratoria.backend.training.TrainingType;
import java.util.List;

/** Main reason the user trains; it steers which training types recommendations prioritize. */
public enum TrainingGoal {
    PUBLIC_SPEAKING("Hablar en público con seguridad", List.of(TrainingType.PRESENTATION)),
    JOB_INTERVIEWS("Preparar entrevistas laborales", List.of(TrainingType.INTERVIEW)),
    ACADEMIC_PRESENTATIONS("Exposiciones y defensas académicas", List.of(TrainingType.PRESENTATION, TrainingType.PROJECT_DEFENSE)),
    DEBATE("Debatir y argumentar", List.of(TrainingType.DEBATE)),
    SALES("Ventas, negociación y pitch", List.of(TrainingType.SALES, TrainingType.PITCH)),
    SPONTANEITY("Improvisar con fluidez", List.of(TrainingType.IMPROVISATION));

    private final String label;
    private final List<TrainingType> trainingTypes;

    TrainingGoal(String label, List<TrainingType> trainingTypes) {
        this.label = label;
        this.trainingTypes = trainingTypes;
    }

    public String label() { return label; }
    public List<TrainingType> trainingTypes() { return trainingTypes; }
}

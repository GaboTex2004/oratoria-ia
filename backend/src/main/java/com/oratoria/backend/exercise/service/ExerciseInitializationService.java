package com.oratoria.backend.exercise.service;

import com.oratoria.backend.exercise.entity.Exercise;
import com.oratoria.backend.exercise.repository.ExerciseRepository;
import com.oratoria.backend.training.TrainingType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(15)
public class ExerciseInitializationService implements ApplicationRunner {
    private final ExerciseRepository exercises;

    public ExerciseInitializationService(ExerciseRepository exercises) {
        this.exercises = exercises;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureExercise(
                "PRESENTATION_INTRO",
                "Presentación académica y profesional",
                "Practica tu introducción personal o de un tema universitario con inicio, desarrollo y cierre claros.",
                "Preséntate en 90 segundos ante un comité académico: menciona tu carrera, el problema que te interesa resolver, tu propuesta principal y una conclusión memorable.",
                TrainingType.PRESENTATION,
                90
        );
        ensureExercise(
                "INTERVIEW_STAR",
                "Entrevista laboral con método STAR",
                "Responde una pregunta conductual estructurando Situación, Tarea, Acción y Resultado.",
                "Describe un proyecto universitario o profesional desafiante en el que tuviste que resolver un bloqueo técnico o de equipo. Explica el contexto, tu rol exacto y el resultado medible.",
                TrainingType.INTERVIEW,
                120
        );
        ensureExercise(
                "DEBATE_ARGUMENT",
                "Argumentación y refutación estructurada",
                "Defiende una postura con tesis, dos evidencias sólidas y anticipación de objeciones.",
                "Expón tu postura sobre la integración obligatoria de herramientas de inteligencia artificial en la educación superior: presenta tu tesis, dos argumentos respaldados y responde a una posible objeción.",
                TrainingType.DEBATE,
                120
        );
        ensureExercise(
                "PITCH_ELEVATOR",
                "Elevator Pitch de impacto",
                "Comunica el valor de tu proyecto o startup en menos de dos minutos de forma persuasiva.",
                "Imagina que tienes 90 segundos frente a un jurado de innovación universitaria: define el dolor del usuario, tu solución diferencial, el impacto esperado y el llamado a la acción.",
                TrainingType.PITCH,
                90
        );
        ensureExercise(
                "PROJECT_DEFENSE_FINAL",
                "Defensa de proyecto de grado o materia",
                "Sintetiza objetivos, arquitectura, resultados y conclusiones frente a un tribunal evaluador.",
                "Realiza la apertura de la defensa de tu proyecto de ingeniería: plantea el objetivo general, las decisiones clave de arquitectura adoptadas y los resultados verificados.",
                TrainingType.PROJECT_DEFENSE,
                180
        );
        ensureExercise(
                "SALES_VALUE_PROPOSITION",
                "Propuesta de valor consultiva",
                "Presenta una solución técnica enfocándote en beneficios y retorno para el cliente.",
                "Presenta una plataforma tecnológica a un director académico explicando cómo reduce tiempos operativos, qué problema elimina y cómo iniciar una prueba piloto.",
                TrainingType.SALES,
                90
        );
        ensureExercise(
                "IMPROVISATION_QUICK",
                "Improvisación con estructura rápida",
                "Organiza tus ideas al instante sobre un tema inesperado manteniendo fluidez y evitando muletillas.",
                "Habla durante 60 segundos de forma continua sobre la habilidad más valiosa que has aprendido en la universidad y cómo la aplicarías mañana en un equipo real.",
                TrainingType.IMPROVISATION,
                60
        );
    }

    private void ensureExercise(String code, String title, String description, String prompt,
                                TrainingType trainingType, int recommendedDurationSeconds) {
        exercises.findByCode(code).orElseGet(() -> exercises.save(
                new Exercise(code, title, description, prompt, trainingType, recommendedDurationSeconds)));
    }
}

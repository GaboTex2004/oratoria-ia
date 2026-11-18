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
@Order(16)
public class ExtendedCatalogInitializer implements ApplicationRunner {
    private static final java.util.Map<String, String> STYLE_LABELS = java.util.Map.of(
            "PERSUASIVO", "persuasivo", "NARRATIVO", "narrativo", "DINAMICO", "dinámico", "ACADEMICO", "académico");
    private final ExerciseRepository exercises;
    public ExtendedCatalogInitializer(ExerciseRepository exercises) { this.exercises = exercises; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("CLARIDAD_PRECISION", "Explica sin tecnicismos", "CLARIDAD", "BASIC", 60,
                "Explica qué es una base de datos a una persona sin formación técnica. Usa una analogía y cierra con una idea principal.");
        seed("RITMO_PAUSAS", "Pausas con intención", "RITMO", "BASIC", 60,
                "Explica tres hábitos de estudio. Separa cada hábito con una pausa y un ejemplo; conserva un ritmo cómodo.");
        seed("ENTONACION_CONTRASTES", "Contrastes en la voz", "ENTONACION", "INTERMEDIATE", 90,
                "Cuenta una decisión con ventajas y desventajas. Destaca con la voz los contrastes y evita forzar tu registro.");
        seed("FLUIDEZ_CONTINUIDAD", "Una idea sin interrupciones", "FLUIDEZ", "BASIC", 60,
                "Describe una habilidad que dominas. Ante una duda, haz una pausa en lugar de repetir muletillas.");
        seed("ESTRUCTURA_TRES_PASOS", "Apertura, desarrollo y cierre", "ESTRUCTURA", "BASIC", 90,
                "Propón una mejora para tu universidad. Presenta el objetivo, explica dos acciones y termina resumiendo el beneficio.");
        seed("ARGUMENTACION_EVIDENCIA", "Defiende con evidencia", "ARGUMENTACION", "ADVANCED", 120,
                "Defiende una política educativa. Distingue hechos de opiniones y presenta una objeción con su respuesta.");
        seed("NO_VERBAL_ENCUADRE", "Presentación con postura observable", "NO_VERBAL", "BASIC", 60,
                "Presenta tu proyecto frente a la cámara opcional. Mantén cabeza y hombros visibles; revisa la alineación sin forzar la postura.");
        String[] topics = {"Una biblioteca sin libros", "Aprender de un error", "La ciudad dentro de diez años", "Una solución para ahorrar agua"};
        for (int i = 0; i < topics.length; i++) {
            seed("IMPROVISACION_" + (i + 1), "Tema inesperado: " + topics[i], "IMPROVISACION", "INTERMEDIATE", 60,
                    "Habla de «" + topics[i] + "» durante un minuto. Presenta una idea, un ejemplo y una conclusión.");
        }
        style("ESTILO_NARRATIVO", "NARRATIVO", "Una historia que enseña", "INTERMEDIATE", 90,
                "Cuenta una experiencia con contexto, conflicto y aprendizaje. Conecta el final con una enseñanza para la audiencia.");
        style("ESTILO_NARRATIVO_2", "NARRATIVO", "El día que todo cambió", "BASIC", 60,
                "Narra en un minuto un momento que cambió tu forma de pensar. Describe el lugar, la decisión y lo que aprendiste.");
        style("ESTILO_PERSUASIVO", "PERSUASIVO", "Una propuesta convincente", "ADVANCED", 90,
                "Convence a tu audiencia de apoyar una iniciativa universitaria. Explica el problema, la evidencia y una acción concreta.");
        style("ESTILO_PERSUASIVO_2", "PERSUASIVO", "Defiende un hábito", "INTERMEDIATE", 60,
                "Convence a un compañero de adoptar un hábito de estudio. Da dos razones con evidencia, responde una objeción y pide que lo pruebe una semana.");
        style("ESTILO_DINAMICO_1", "DINAMICO", "Presentación relámpago", "BASIC", 60,
                "Presenta tu carrera en un minuto con frases cortas. Incluye una pregunta a la audiencia y tres ideas marcadas con pausas.");
        style("ESTILO_DINAMICO_2", "DINAMICO", "Motiva al equipo", "INTERMEDIATE", 90,
                "Motiva a tu equipo antes de una entrega difícil. Alterna energía y calma, usa frases breves y termina con una consigna.");
        style("ESTILO_ACADEMICO_1", "ACADEMICO", "Explica un concepto de tu carrera", "INTERMEDIATE", 90,
                "Define un concepto central de tu carrera, anuncia los tres puntos que tratarás, desarróllalos con un fundamento y cierra con una síntesis.");
        style("ESTILO_ACADEMICO_2", "ACADEMICO", "Resumen de una investigación", "ADVANCED", 120,
                "Resume una investigación o artículo: pregunta, método, resultado principal y una limitación. Distingue datos de opiniones.");
    }

    private void style(String code, String style, String title, String level, int duration, String prompt) {
        seed(code, title, "ESTILOS", level, duration, prompt);
        // Databases seeded before styles existed get the style and its description on the next start.
        exercises.findByCode(code).ifPresent(exercise -> {
            if (exercise.getCommunicationStyle() == null) exercise.assignStyle(style);
            if (exercise.getDescription().startsWith("Práctica enfocada en")) {
                exercise.edit(exercise.getTitle(), "Práctica del estilo " + STYLE_LABELS.get(style) + ".",
                        exercise.getPrompt(), exercise.getRecommendedDurationSeconds());
            }
        });
    }

    private void seed(String code, String title, String category, String level, int duration, String prompt) {
        if (exercises.findByCode(code).isPresent()) return;
        TrainingType type = category.equals("IMPROVISACION") ? TrainingType.IMPROVISATION
                : category.equals("ARGUMENTACION") ? TrainingType.DEBATE : TrainingType.PRESENTATION;
        Exercise exercise = new Exercise(code, title, "Práctica enfocada en " + category.toLowerCase() + ".", prompt, type, duration);
        exercise.configure(category, level, prompt);
        exercises.save(exercise);
    }
}

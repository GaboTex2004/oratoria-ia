package com.oratoria.backend.style;

import com.oratoria.backend.shared.exception.BadRequestException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Fixed catalog of communication styles (CU-21). Styles describe traits, never a real person's voice or identity. */
@Component
public class CommunicationStyleCatalog {
    public static final String DISCLAIMER = "Cada estilo entrena un conjunto de rasgos comunicativos; no imita la voz ni la identidad de ninguna persona concreta.";

    private final Map<String, CommunicationStyle> styles = new LinkedHashMap<>();

    public CommunicationStyleCatalog() {
        add(new CommunicationStyle("PERSUASIVO", "Persuasivo",
                "Convence con argumentos, evidencia y un llamado a la acción.",
                List.of("Plantea un problema que importe a la audiencia", "Respalda cada idea con evidencia",
                        "Anticipa y responde una objeción", "Cierra con una acción concreta"),
                "Abre con el problema, presenta dos razones con evidencia, responde una objeción y termina pidiendo una acción.",
                Map.of("ARGUMENTATION", 0.45, "COHERENCE", 0.25, "CLARITY", 0.15, "ORGANIZATION", 0.15), 120, 160));
        add(new CommunicationStyle("NARRATIVO", "Narrativo",
                "Enseña a través de una historia con contexto, conflicto y aprendizaje.",
                List.of("Sitúa a la audiencia en un momento concreto", "Presenta un conflicto o decisión",
                        "Usa detalles que se puedan imaginar", "Conecta el final con una enseñanza"),
                "Cuenta la historia en orden: contexto, conflicto, decisión y aprendizaje. Termina explicando qué puede llevarse la audiencia.",
                Map.of("ORGANIZATION", 0.35, "COHERENCE", 0.30, "CLARITY", 0.25, "ARGUMENTATION", 0.10), 110, 150));
        add(new CommunicationStyle("DINAMICO", "Dinámico",
                "Mantiene la atención con energía, frases cortas y ritmo ágil.",
                List.of("Frases cortas y directas", "Ritmo ágil con pausas marcadas",
                        "Preguntas a la audiencia", "Cambios de energía entre ideas"),
                "Usa frases cortas, haz al menos una pregunta a la audiencia y marca con una pausa cada cambio de idea.",
                Map.of("CLARITY", 0.35, "COHERENCE", 0.25, "ORGANIZATION", 0.20, "ARGUMENTATION", 0.20), 140, 180));
        add(new CommunicationStyle("ACADEMICO", "Académico",
                "Expone con precisión, estructura explícita y fundamentos verificables.",
                List.of("Define los términos clave", "Anuncia la estructura antes de desarrollarla",
                        "Distingue datos, fuentes y opiniones", "Concluye con una síntesis"),
                "Presenta el tema y su estructura, define los conceptos, desarrolla con fundamentos y cierra con una síntesis.",
                Map.of("ORGANIZATION", 0.30, "ARGUMENTATION", 0.30, "CLARITY", 0.25, "COHERENCE", 0.15), 100, 140));
    }

    private void add(CommunicationStyle style) { styles.put(style.code(), style); }

    public List<CommunicationStyle> list() { return List.copyOf(styles.values()); }

    public Optional<CommunicationStyle> find(String code) { return Optional.ofNullable(code == null ? null : styles.get(code)); }

    public CommunicationStyle require(String code) {
        return find(code).orElseThrow(() -> new BadRequestException("Estilo de comunicación inexistente."));
    }

    public record CommunicationStyle(String code, String name, String summary, List<String> traits, String guidance,
                                     Map<String, Double> weights, int minWordsPerMinute, int maxWordsPerMinute) {}
}

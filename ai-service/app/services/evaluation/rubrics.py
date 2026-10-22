from dataclasses import dataclass

from app.schemas.evaluation import MeasuredIndicatorSchema
from app.schemas.speech import VerbalCommunicationMetricsSchema

RUBRIC_VERSION = "oratoria-rubric-v1.0"


@dataclass(frozen=True)
class CriterionDefinition:
    code: str
    label: str
    weight: float
    focus: str


@dataclass(frozen=True)
class TrainingRubric:
    training_type: str
    title: str
    scenario_context: str
    criteria: tuple[CriterionDefinition, ...]


RUBRICS_BY_TRAINING_TYPE: dict[str, TrainingRubric] = {
    "PRESENTATION": TrainingRubric(
        training_type="PRESENTATION",
        title="Presentación Académica o Profesional",
        scenario_context=(
            "Exposición expositiva frente a una audiencia universitaria o profesional. "
            "Se valora una apertura contextualizadora, explicación didáctica de conceptos y cierre con síntesis."
        ),
        criteria=(
            CriterionDefinition(
                code="CLARITY",
                label="Claridad y precisión conceptual",
                weight=0.25,
                focus="Uso de vocabulario comprensible, definición de términos clave y ausencia de ambigüedades.",
            ),
            CriterionDefinition(
                code="COHERENCE",
                label="Coherencia y conexión temática",
                weight=0.25,
                focus="Hilo conductor sostenido entre las ideas sin saltos abruptos ni contradicciones.",
            ),
            CriterionDefinition(
                code="ORGANIZATION",
                label="Organización (Apertura, Desarrollo y Cierre)",
                weight=0.25,
                focus="Presencia de introducción del tema, desarrollo ordenado de puntos y conclusión recapitulativa.",
            ),
            CriterionDefinition(
                code="ARGUMENTATION",
                label="Sustento, ejemplos y valor explicativo",
                weight=0.25,
                focus="Respaldo de las afirmaciones mediante datos, casos prácticos, causas o consecuencias.",
            ),
        ),
    ),
    "INTERVIEW": TrainingRubric(
        training_type="INTERVIEW",
        title="Entrevista Laboral o Académica",
        scenario_context=(
            "Respuesta a preguntas de selección profesional o beca. "
            "Se espera enfoque directo (metodología STAR: Situación, Tarea, Acción, Resultado), relevancia y concisión."
        ),
        criteria=(
            CriterionDefinition(
                code="CLARITY",
                label="Claridad y especificidad del rol propio",
                weight=0.25,
                focus="Descripción directa de la experiencia o competencia sin rodeos ni generalidades vagas.",
            ),
            CriterionDefinition(
                code="COHERENCE",
                label="Coherencia con la pregunta planteada",
                weight=0.25,
                focus="Pertinencia de la narrativa respecto al objetivo de la entrevista y consistencia temporal.",
            ),
            CriterionDefinition(
                code="ORGANIZATION",
                label="Estructura narrativa (Situación → Acción → Resultado)",
                weight=0.25,
                focus="Secuencia ordenada desde el contexto del reto hasta las acciones tomadas y el logro obtenido.",
            ),
            CriterionDefinition(
                code="ARGUMENTATION",
                label="Evidencia de logro y aprendizaje demostrado",
                weight=0.25,
                focus="Aporte de indicadores concretos, impacto real de las decisiones y reflexión profesional.",
            ),
        ),
    ),
    "DEBATE": TrainingRubric(
        training_type="DEBATE",
        title="Debate y Argumentación Crítica",
        scenario_context=(
            "Defensa razonada de una postura ante una controversia pública o académica. "
            "Se requiere tesis explícita, premisas verificables y anticipación o refutación de contraargumentos."
        ),
        criteria=(
            CriterionDefinition(
                code="CLARITY",
                label="Claridad de la tesis y postura",
                weight=0.20,
                focus="Formulación nítida de la postura defendida desde el inicio de la intervención.",
            ),
            CriterionDefinition(
                code="COHERENCE",
                label="Coherencia lógica entre premisas y conclusión",
                weight=0.25,
                focus="Validez del encadenamiento causal y ausencia de contradicciones internas o falacias evidentes.",
            ),
            CriterionDefinition(
                code="ORGANIZATION",
                label="Jerarquización de argumentos",
                weight=0.25,
                focus="Separación ordenada entre tesis, argumentos principales, matices y cierre conclusivo.",
            ),
            CriterionDefinition(
                code="ARGUMENTATION",
                label="Solidez argumentativa y manejo de objeciones",
                weight=0.30,
                focus="Calidad de las razones, evidencias y consideración de perspectivas alternativas.",
            ),
        ),
    ),
    "PITCH": TrainingRubric(
        training_type="PITCH",
        title="Elevator Pitch / Presentación de Impacto",
        scenario_context=(
            "Presentación breve de alto impacto sobre una propuesta, proyecto o emprendimiento. "
            "Debe identificar el problema, presentar la solución diferencial, el valor y un llamado a la acción."
        ),
        criteria=(
            CriterionDefinition(
                code="CLARITY",
                label="Claridad de la propuesta de valor",
                weight=0.30,
                focus="Capacidad de explicar qué resuelve la propuesta y para quién en lenguaje directo y memorable.",
            ),
            CriterionDefinition(
                code="COHERENCE",
                label="Alineación Problema–Solución–Beneficio",
                weight=0.20,
                focus="Correspondencia directa entre la necesidad planteada y la solución ofrecida.",
            ),
            CriterionDefinition(
                code="ORGANIZATION",
                label="Secuencia de Pitch (Gancho → Problema → Solución → Cierre)",
                weight=0.25,
                focus="Aprovechamiento eficiente del tiempo con transiciones ágiles y cierre con llamado a la acción.",
            ),
            CriterionDefinition(
                code="ARGUMENTATION",
                label="Diferenciación y viabilidad",
                weight=0.25,
                focus="Justificación de por qué la solución es efectiva, innovadora o superior a las alternativas.",
            ),
        ),
    ),
    "PROJECT_DEFENSE": TrainingRubric(
        training_type="PROJECT_DEFENSE",
        title="Defensa de Proyecto o Tesis Universitaria",
        scenario_context=(
            "Sustentación formal ante tribunal académico. "
            "Exige rigor metodológico, planteamiento del problema, objetivos, resultados y conclusiones justificadas."
        ),
        criteria=(
            CriterionDefinition(
                code="CLARITY",
                label="Precisión terminológica y metodológica",
                weight=0.25,
                focus="Exactitud en la exposición del problema de investigación, variables, diseño o arquitectura.",
            ),
            CriterionDefinition(
                code="COHERENCE",
                label="Consistencia entre problema, objetivos y hallazgos",
                weight=0.25,
                focus="Correspondencia rigurosa entre lo que el proyecto buscaba resolver y las conclusiones expuestas.",
            ),
            CriterionDefinition(
                code="ORGANIZATION",
                label="Estructura académica de sustentación",
                weight=0.20,
                focus="Progresión ordenada: planteamiento, metodología/desarrollo, resultados y conclusiones.",
            ),
            CriterionDefinition(
                code="ARGUMENTATION",
                label="Justificación técnica y defensa de decisiones",
                weight=0.30,
                focus="Sustento de las elecciones metodológicas o técnicas con evidencias y criterios objetivos.",
            ),
        ),
    ),
    "SALES": TrainingRubric(
        training_type="SALES",
        title="Comunicación Comercial y Ventas Consultivas",
        scenario_context=(
            "Presentación consultiva orientada a resolver dolores de un cliente potencial. "
            "Prioriza empatía con la necesidad del cliente, beneficios tangibles, manejo de dudas y siguiente paso."
        ),
        criteria=(
            CriterionDefinition(
                code="CLARITY",
                label="Claridad de beneficios para el cliente",
                weight=0.25,
                focus="Traducción de características técnicas en beneficios concretos y comprensibles para el interlocutor.",
            ),
            CriterionDefinition(
                code="COHERENCE",
                label="Pertinencia consultiva",
                weight=0.25,
                focus="Enfoque constante en la necesidad del cliente sin dispersarse en detalles irrelevantes.",
            ),
            CriterionDefinition(
                code="ORGANIZATION",
                label="Estructura consultiva (Diagnóstico → Propuesta → Acción)",
                weight=0.25,
                focus="Orden lógico desde el dolor del cliente hasta la propuesta de solución y acuerdo de seguimiento.",
            ),
            CriterionDefinition(
                code="ARGUMENTATION",
                label="Persuasión basada en valor y retorno",
                weight=0.25,
                focus="Respaldo de la oferta mediante casos de éxito, garantías, ahorro o impacto cuantificable.",
            ),
        ),
    ),
    "IMPROVISATION": TrainingRubric(
        training_type="IMPROVISATION",
        title="Improvisación Estructurada (Impromptu Speaking)",
        scenario_context=(
            "Discurso espontáneo con tiempo mínimo de preparación. "
            "Se evalúa la capacidad de adoptar rápidamente una tesis, estructurar 2-3 ideas claras y cerrar con sentido."
        ),
        criteria=(
            CriterionDefinition(
                code="CLARITY",
                label="Nitidez de la idea central",
                weight=0.25,
                focus="Formulación comprensible de un mensaje rector pese a la espontaneidad del ejercicio.",
            ),
            CriterionDefinition(
                code="COHERENCE",
                label="Continuidad temática sin divagación",
                weight=0.30,
                focus="Capacidad de mantener el eje del tema evitando contradicciones o repeticiones circulares.",
            ),
            CriterionDefinition(
                code="ORGANIZATION",
                label="Microestructura ágil (Inicio → Desarrollo → Cierre)",
                weight=0.25,
                focus="Adopción de un esquema mental rápido que evite un final inconcluso o abrupto.",
            ),
            CriterionDefinition(
                code="ARGUMENTATION",
                label="Uso de ejemplos o analogías de apoyo",
                weight=0.20,
                focus="Ilustración de la idea improvisada con al menos un ejemplo cotidiano, histórico o práctico.",
            ),
        ),
    ),
}


def get_rubric_for_training_type(training_type: str | None) -> TrainingRubric:
    normalized = (training_type or "PRESENTATION").strip().upper()
    return RUBRICS_BY_TRAINING_TYPE.get(normalized, RUBRICS_BY_TRAINING_TYPE["PRESENTATION"])


def score_to_level(score: float | None) -> str:
    if score is None:
        return "INSUFFICIENT_EVIDENCE"
    if score >= 85.0:
        return "SOBRESALIENTE"
    if score >= 70.0:
        return "COMPETENTE"
    if score >= 50.0:
        return "EN_DESARROLLO"
    return "INICIAL"


def build_measured_indicators(metrics: VerbalCommunicationMetricsSchema) -> list[MeasuredIndicatorSchema]:
    wpm = metrics.wordsPerMinute
    if wpm < 90:
        wpm_interp = "Ritmo pausado (inferior al rango conversacional habitual de 110–150 WPM)."
    elif wpm <= 160:
        wpm_interp = "Ritmo fluido dentro del rango recomendado para exposición en español (110–160 WPM)."
    else:
        wpm_interp = "Ritmo acelerado (superior a 160 WPM); puede dificultar la asimilación de ideas complejas."

    filler_rate = (
        round((metrics.fillerWordsTotal / metrics.recordingDurationSeconds) * 60.0, 2)
        if metrics.recordingDurationSeconds > 0
        else 0.0
    )
    if metrics.fillerWordsTotal == 0:
        filler_interp = "Sin muletillas detectadas en la transcripción."
    elif filler_rate <= 3.0:
        filler_interp = f"Incidencia baja de muletillas ({filler_rate} por minuto)."
    else:
        filler_interp = f"Incidencia perceptible de muletillas ({filler_rate} por minuto); conviene sustituirlas por pausas silenciosas."

    indicators = [
        MeasuredIndicatorSchema(
            code="WORD_COUNT",
            label="Extensión verbal medida",
            value=metrics.wordCount,
            unit="palabras",
            interpretation=f"Total de palabras reconocidas en {round(metrics.recordingDurationSeconds, 1)} s de grabación.",
        ),
        MeasuredIndicatorSchema(
            code="WORDS_PER_MINUTE",
            label="Velocidad de habla (WPM)",
            value=round(metrics.wordsPerMinute, 1),
            unit="palabras/min",
            interpretation=wpm_interp,
        ),
        MeasuredIndicatorSchema(
            code="FILLER_WORDS_TOTAL",
            label="Muletillas contabilizadas",
            value=metrics.fillerWordsTotal,
            unit="ocurrencias",
            interpretation=filler_interp,
        ),
    ]

    if metrics.timestampsAvailable and metrics.estimatedPauseCount is not None:
        indicators.append(
            MeasuredIndicatorSchema(
                code="ESTIMATED_PAUSE_COUNT",
                label="Pausas prolongadas (≥ 0.6 s)",
                value=metrics.estimatedPauseCount,
                unit="pausas",
                interpretation=(
                    f"Tiempo acumulado en pausas: {round(metrics.totalPauseDurationSeconds or 0.0, 2)} s "
                    f"(máxima: {round(metrics.longestPauseSeconds or 0.0, 2)} s)."
                ),
            )
        )

    if metrics.spokenTimeRatio is not None:
        indicators.append(
            MeasuredIndicatorSchema(
                code="SPOKEN_TIME_RATIO",
                label="Proporción de tiempo hablado activo",
                value=round(metrics.spokenTimeRatio * 100.0, 1),
                unit="%",
                interpretation="Porcentaje de la grabación con fonación activa frente a silencios.",
            )
        )

    return indicators

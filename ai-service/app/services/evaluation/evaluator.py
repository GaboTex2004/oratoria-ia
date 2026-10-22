import re

from app.core.config import settings
from app.schemas.evaluation import (
    ActionableRecommendationSchema,
    CriterionEvaluationSchema,
    ImprovedResponseExampleSchema,
    SpeechEvaluationRequestSchema,
    SpeechEvaluationResponseSchema,
)
from app.services.evaluation.provider import (
    DiscursiveEvaluationProvider,
    EvaluationProviderError,
    GroqChatEvaluationProvider,
)
from app.services.evaluation.rubrics import (
    RUBRIC_VERSION,
    TrainingRubric,
    build_measured_indicators,
    get_rubric_for_training_type,
    score_to_level,
)

PROHIBITED_NON_TEXTUAL_PATTERNS = re.compile(
    r"\b(contacto visual|lenguaje corporal|postura corporal|gesticulaci[oó]n|expresi[oó]n facial|timbre de voz|volumen de voz)\b",
    re.IGNORECASE,
)


def _sanitize_judgment_text(text: str, fallback: str) -> str:
    cleaned = (text or "").strip()
    if not cleaned:
        return fallback
    cleaned = PROHIBITED_NON_TEXTUAL_PATTERNS.sub("expresión verbal", cleaned)
    return cleaned


class SpeechEvaluationService:
    def __init__(
        self,
        provider: DiscursiveEvaluationProvider | None = None,
        min_words: int | None = None,
        min_duration_seconds: float | None = None,
    ) -> None:
        self._provider = provider or GroqChatEvaluationProvider(
            api_key=settings.groq_api_key,
            model=settings.groq_llm_model,
            api_url=settings.groq_chat_api_url,
            timeout_seconds=settings.groq_llm_timeout_seconds,
        )
        self._min_words = min_words if min_words is not None else settings.evaluation_min_words
        self._min_duration_seconds = (
            min_duration_seconds
            if min_duration_seconds is not None
            else settings.evaluation_min_duration_seconds
        )

    def evaluate(self, req: SpeechEvaluationRequestSchema) -> SpeechEvaluationResponseSchema:
        session_id = (req.sessionId or "").strip()
        if not session_id:
            raise EvaluationProviderError(
                code="INVALID_SESSION_ID",
                message="El identificador de sesión es obligatorio para evaluar el discurso.",
                status_code=400,
                retryable=False,
                origin="ai-service-validation",
            )

        rubric = get_rubric_for_training_type(req.trainingType)
        measured_indicators = build_measured_indicators(req.metrics)

        transcript_clean = (req.transcriptText or "").strip()
        actual_words = len(re.findall(r"[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9]+", transcript_clean))
        effective_word_count = max(req.metrics.wordCount, actual_words)
        duration = req.metrics.recordingDurationSeconds

        if (
            not transcript_clean
            or effective_word_count < self._min_words
            or duration < self._min_duration_seconds
        ):
            return self._build_insufficient_evidence_response(
                session_id=session_id,
                rubric=rubric,
                measured_indicators=measured_indicators,
                word_count=effective_word_count,
                duration_seconds=duration,
            )

        raw_eval = self._provider.evaluate_discourse(
            rubric=rubric,
            transcript_text=transcript_clean,
            metrics=req.metrics,
            exercise_title=req.exerciseTitle,
            exercise_prompt=req.exercisePrompt,
        )

        return self._normalize_and_validate_llm_output(
            session_id=session_id,
            rubric=rubric,
            measured_indicators=measured_indicators,
            transcript_text=transcript_clean,
            raw_eval=raw_eval,
        )

    def _build_insufficient_evidence_response(
        self,
        session_id: str,
        rubric: TrainingRubric,
        measured_indicators: list,
        word_count: int,
        duration_seconds: float,
    ) -> SpeechEvaluationResponseSchema:
        reason = (
            f"Evidencia verbal insuficiente para emitir puntuaciones confiables: se registraron "
            f"{word_count} palabras en {round(duration_seconds, 1)} s (mínimo requerido: "
            f"{self._min_words} palabras y {round(self._min_duration_seconds, 1)} s)."
        )
        criteria_scores = [
            CriterionEvaluationSchema(
                code=c.code,  # type: ignore[arg-type]
                label=c.label,
                weight=c.weight,
                score=None,
                level="INSUFFICIENT_EVIDENCE",
                justification=(
                    "No se asigna puntuación porque la extensión del texto transcrito no permite "
                    f"verificar con rigor el criterio «{c.label}»."
                ),
                evidenceQuotes=[],
                judgmentType="AI_ESTIMATED",
            )
            for c in rubric.criteria
        ]
        return SpeechEvaluationResponseSchema(
            schemaVersion="v1",
            sessionId=session_id,
            trainingType=rubric.training_type,
            rubricVersion=RUBRIC_VERSION,
            provider=self._provider.provider_name,
            model=self._provider.model_name,
            sufficientEvidence=False,
            insufficientEvidenceReason=reason,
            overallScore=None,
            executiveSummary=(
                "La grabación es demasiado breve o contiene muy pocas palabras reconocidas para "
                "evaluar su claridad, coherencia, organización y argumentación sin caer en juicios arbitrarios."
            ),
            measuredIndicators=measured_indicators,
            criteriaScores=criteria_scores,
            strengths=[],
            areasForImprovement=[
                "Extensión verbal insuficiente para desarrollar una idea completa con apertura, cuerpo y cierre."
            ],
            recommendations=[
                ActionableRecommendationSchema(
                    title="Desarrollar una intervención completa",
                    description=(
                        f"Para un ejercicio de {rubric.title}, procura hablar durante al menos 20 a 45 segundos "
                        "exponiendo una idea central, al menos un argumento o ejemplo y una conclusión breve."
                    ),
                    priority="ALTA",
                ),
                ActionableRecommendationSchema(
                    title="Verificar cercanía y captura del micrófono",
                    description=(
                        "Si hablaste durante más tiempo pero la transcripción resultó muy corta, comprueba que "
                        "el micrófono correcto esté seleccionado en tu navegador antes de iniciar."
                    ),
                    priority="MEDIA",
                ),
            ],
            improvedExamples=[],
        )

    def _normalize_and_validate_llm_output(
        self,
        session_id: str,
        rubric: TrainingRubric,
        measured_indicators: list,
        transcript_text: str,
        raw_eval: dict,
    ) -> SpeechEvaluationResponseSchema:
        raw_criteria = raw_eval.get("criteriaScores")
        by_code: dict[str, dict] = {}
        if isinstance(raw_criteria, list):
            for item in raw_criteria:
                if isinstance(item, dict) and isinstance(item.get("code"), str):
                    by_code[item["code"].strip().upper()] = item

        default_quote = transcript_text[:140].strip()
        criteria_scores: list[CriterionEvaluationSchema] = []
        weighted_sum = 0.0
        total_weight = 0.0

        for crit_def in rubric.criteria:
            raw_item = by_code.get(crit_def.code, {})
            raw_score = raw_item.get("score")
            if isinstance(raw_score, (int, float)):
                clamped_score = round(min(100.0, max(0.0, float(raw_score))), 1)
            else:
                clamped_score = 70.0

            weighted_sum += clamped_score * crit_def.weight
            total_weight += crit_def.weight

            justification = _sanitize_judgment_text(
                str(raw_item.get("justification") or ""),
                f"Evaluación del criterio {crit_def.label} según la rúbrica {rubric.title}.",
            )
            raw_quotes = raw_item.get("evidenceQuotes")
            quotes: list[str] = []
            if isinstance(raw_quotes, list):
                for q in raw_quotes[:3]:
                    if isinstance(q, str) and q.strip():
                        quotes.append(q.strip()[:220])
            if not quotes and default_quote:
                quotes.append(default_quote)

            criteria_scores.append(
                CriterionEvaluationSchema(
                    code=crit_def.code,  # type: ignore[arg-type]
                    label=crit_def.label,
                    weight=crit_def.weight,
                    score=clamped_score,
                    level=score_to_level(clamped_score),  # type: ignore[arg-type]
                    justification=justification,
                    evidenceQuotes=quotes,
                    judgmentType="AI_ESTIMATED",
                )
            )

        overall_score = round(weighted_sum / total_weight, 1) if total_weight > 0 else None

        exec_summary = _sanitize_judgment_text(
            str(raw_eval.get("executiveSummary") or ""),
            f"Evaluación discursiva completada bajo la rúbrica de {rubric.title}.",
        )

        strengths = self._extract_string_list(raw_eval.get("strengths"), fallback_item="Intención comunicativa alineada con el tema propuesto.")
        improvements = self._extract_string_list(
            raw_eval.get("areasForImprovement"),
            fallback_item="Profundizar en el desarrollo de conectores lógicos y evidencia de respaldo.",
        )

        recommendations: list[ActionableRecommendationSchema] = []
        raw_recs = raw_eval.get("recommendations")
        if isinstance(raw_recs, list):
            for rec in raw_recs[:5]:
                if not isinstance(rec, dict):
                    continue
                title = _sanitize_judgment_text(str(rec.get("title") or ""), "Fortalecer la estructura del discurso")
                desc = _sanitize_judgment_text(str(rec.get("description") or ""), "")
                prio_raw = str(rec.get("priority") or "MEDIA").strip().upper()
                prio = prio_raw if prio_raw in ("ALTA", "MEDIA", "SUGERIDA") else "MEDIA"
                if desc:
                    recommendations.append(
                        ActionableRecommendationSchema(
                            title=title,
                            description=desc,
                            priority=prio,  # type: ignore[arg-type]
                        )
                    )
        if not recommendations:
            recommendations.append(
                ActionableRecommendationSchema(
                    title="Aplicar estructura tripartita explícita",
                    description=f"En tus próximas prácticas de {rubric.title}, divide tu respuesta en tesis inicial, argumentos con ejemplo y conclusión.",
                    priority="ALTA",
                )
            )

        improved_examples: list[ImprovedResponseExampleSchema] = []
        raw_examples = raw_eval.get("improvedExamples")
        if isinstance(raw_examples, list):
            for ex in raw_examples[:3]:
                if not isinstance(ex, dict):
                    continue
                orig = str(ex.get("originalFragment") or "").strip()
                prop = _sanitize_judgment_text(str(ex.get("improvedProposal") or ""), "")
                rat = _sanitize_judgment_text(str(ex.get("rationale") or ""), "Mejora la precisión y el orden de las ideas.")
                if orig and prop:
                    improved_examples.append(
                        ImprovedResponseExampleSchema(
                            originalFragment=orig,
                            improvedProposal=prop,
                            rationale=rat,
                        )
                    )

        return SpeechEvaluationResponseSchema(
            schemaVersion="v1",
            sessionId=session_id,
            trainingType=rubric.training_type,
            rubricVersion=RUBRIC_VERSION,
            provider=self._provider.provider_name,
            model=self._provider.model_name,
            sufficientEvidence=True,
            insufficientEvidenceReason=None,
            overallScore=overall_score,
            executiveSummary=exec_summary,
            measuredIndicators=measured_indicators,
            criteriaScores=criteria_scores,
            strengths=strengths,
            areasForImprovement=improvements,
            recommendations=recommendations,
            improvedExamples=improved_examples,
        )

    @staticmethod
    def _extract_string_list(raw_list: object, fallback_item: str) -> list[str]:
        result: list[str] = []
        if isinstance(raw_list, list):
            for item in raw_list[:5]:
                if isinstance(item, str) and item.strip():
                    result.append(_sanitize_judgment_text(item.strip(), fallback_item))
        if not result:
            result.append(fallback_item)
        return result

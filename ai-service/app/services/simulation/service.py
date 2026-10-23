from app.core.config import settings
from app.schemas.simulation import (
    SimulationFeedbackRequestSchema,
    SimulationFeedbackResponseSchema,
    SimulationReplyRequestSchema,
    SimulationReplyResponseSchema,
)
from app.services.simulation.provider import (
    SimulationLLMProvider,
    SimulationProviderError,
    create_simulation_provider,
)


class SimulationConversationService:
    def __init__(self, provider: SimulationLLMProvider | None = None,
                 evaluation_provider: SimulationLLMProvider | None = None,
                 min_user_turns: int | None = None) -> None:
        self.provider = provider or create_simulation_provider(settings.simulation_llm_provider)
        self.evaluation_provider = evaluation_provider or (
            provider if provider is not None
            else create_simulation_provider(settings.simulation_evaluation_provider)
        )
        self.min_user_turns = (
            settings.simulation_min_user_turns if min_user_turns is None else min_user_turns
        )

    def reply(self, request: SimulationReplyRequestSchema) -> SimulationReplyResponseSchema:
        raw = self.provider.generate_reply(request)
        try:
            text = str(raw.get("replyText", "")).strip()
            intent = str(raw.get("conversationalIntent", raw.get("intent", "CONTINUE"))).strip()
            focus = str(raw.get("optionalFollowUpFocus", raw.get("focus", ""))).strip()
            should_end = raw.get("shouldEnd", False)
            if not text or not intent or not isinstance(should_end, bool):
                raise ValueError("invalid reply contract")
            return SimulationReplyResponseSchema(
                replyText=text[:1500], intent=intent[:100], shouldEnd=should_end,
                focus=focus[:300], provider=self.provider.provider_name,
                model=self.provider.model_name,
            )
        except (ValueError, TypeError, AttributeError):
            raise SimulationProviderError(
                "LLM_PROVIDER_INVALID_SCHEMA",
                "El proveedor devolvió una respuesta conversacional incompleta.",
                502, True, "simulation-validator", provider=self.provider.provider_name,
                model=self.provider.model_name,
            ) from None

    def feedback(self, request: SimulationFeedbackRequestSchema) -> SimulationFeedbackResponseSchema:
        user_turns = sum(1 for message in request.history
                         if message.speaker == "USER" and message.text.strip())
        if user_turns < self.min_user_turns:
            return SimulationFeedbackResponseSchema(
                sufficientEvidence=False,
                insufficientEvidenceReason=(
                    f"Se requieren al menos {self.min_user_turns} respuestas para emitir "
                    "un puntaje conversacional."
                ),
                overallScore=None,
                summary="La simulación finalizó con evidencia insuficiente para una evaluación confiable.",
                recommendations=[
                    "Completa más turnos en una nueva simulación para recibir retroalimentación detallada."
                ],
                provider="rules",
                model="deterministic-v1",
            )

        raw = self.evaluation_provider.generate_feedback(request)
        try:
            raw_scores = raw.get("criteriaScores", {})
            scores = {
                key: round(max(0, min(100, float(raw_scores.get(key)))), 1)
                for key in ("CLARITY", "RELEVANCE", "THREAD", "ADAPTATION")
            }
            summary = str(raw.get("summary", "")).strip()
            if not summary:
                raise ValueError("missing summary")
            return SimulationFeedbackResponseSchema(
                sufficientEvidence=True,
                overallScore=round(sum(scores.values()) / len(scores), 1),
                summary=summary[:1500],
                strengths=[str(item)[:500] for item in raw.get("strengths", [])][:6],
                areasForImprovement=[str(item)[:500]
                                     for item in raw.get("areasForImprovement", [])][:6],
                recommendations=[str(item)[:500]
                                 for item in raw.get("recommendations", [])][:6],
                criteriaScores=scores,
                provider=self.evaluation_provider.provider_name,
                model=self.evaluation_provider.model_name,
            )
        except (ValueError, TypeError, AttributeError):
            raise SimulationProviderError(
                "LLM_PROVIDER_INVALID_SCHEMA",
                "El proveedor devolvió retroalimentación incompleta.",
                502, True, "simulation-validator",
                provider=self.evaluation_provider.provider_name,
                model=self.evaluation_provider.model_name,
            ) from None

import asyncio
import hmac
import logging
import uuid

from fastapi import APIRouter, Header, Response
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.schemas.evaluation import (
    SpeechEvaluationRequestSchema,
    SpeechEvaluationResponseSchema,
)
from app.services.evaluation.evaluator import SpeechEvaluationService
from app.services.evaluation.provider import EvaluationProviderError

logger = logging.getLogger("oratoria.ai.evaluation")

router = APIRouter(prefix="/api/v1/evaluation", tags=["evaluation"])

_default_evaluation_service = SpeechEvaluationService()


def get_speech_evaluation_service() -> SpeechEvaluationService:
    return _default_evaluation_service


def set_speech_evaluation_service(service: SpeechEvaluationService) -> None:
    global _default_evaluation_service
    _default_evaluation_service = service


def _verify_internal_key(provided_key: str | None) -> bool:
    expected = settings.internal_api_key
    if not expected or not provided_key:
        return False
    return hmac.compare_digest(provided_key.encode("utf-8"), expected.encode("utf-8"))


@router.post("/evaluate", response_model=SpeechEvaluationResponseSchema)
async def evaluate_speech(
    payload: SpeechEvaluationRequestSchema,
    response: Response,
    x_internal_api_key: str | None = Header(default=None),
    x_request_id: str | None = Header(default=None),
):
    request_id = (x_request_id or "").strip() or str(uuid.uuid4())
    response.headers["X-Request-Id"] = request_id

    if not _verify_internal_key(x_internal_api_key):
        logger.warning(
            "Speech evaluation rejected [requestId=%s sessionId=%s status=401 origin=ai-service-auth code=UNAUTHORIZED_INTERNAL_CALL]",
            request_id,
            payload.sessionId,
        )
        return JSONResponse(
            status_code=401,
            headers={"X-Request-Id": request_id},
            content={
                "code": "UNAUTHORIZED_INTERNAL_CALL",
                "message": "No autorizado para invocar el servicio interno de evaluación.",
                "retryable": False,
                "origin": "ai-service-auth",
                "requestId": request_id,
            },
        )

    service = get_speech_evaluation_service()
    try:
        logger.info(
            "Speech evaluation started [requestId=%s sessionId=%s type=%s words=%d]",
            request_id,
            payload.sessionId,
            payload.trainingType,
            payload.metrics.wordCount,
        )
        result = await asyncio.to_thread(service.evaluate, payload)
        logger.info(
            "Speech evaluation completed [requestId=%s sessionId=%s sufficient=%s overallScore=%s]",
            request_id,
            payload.sessionId,
            result.sufficientEvidence,
            result.overallScore,
        )
        return result
    except EvaluationProviderError as exc:
        logger.warning(
            "Speech evaluation failed [requestId=%s sessionId=%s status=%d origin=%s code=%s retryable=%s]",
            request_id,
            payload.sessionId,
            exc.status_code,
            exc.origin,
            exc.code,
            exc.retryable,
        )
        return JSONResponse(
            status_code=exc.status_code,
            headers={"X-Request-Id": request_id},
            content={
                "code": exc.code,
                "message": exc.message,
                "retryable": exc.retryable,
                "origin": exc.origin,
                "requestId": request_id,
            },
        )

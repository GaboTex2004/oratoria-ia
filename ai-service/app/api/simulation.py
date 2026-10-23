import asyncio
import hmac
import logging
import time
import uuid

from fastapi import APIRouter, Header, Response
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.schemas.simulation import (
    SimulationFeedbackRequestSchema,
    SimulationFeedbackResponseSchema,
    SimulationReplyRequestSchema,
    SimulationReplyResponseSchema,
)
from app.services.simulation.provider import SimulationProviderError
from app.services.simulation.service import SimulationConversationService

logger = logging.getLogger("oratoria.ai.simulation")
router = APIRouter(prefix="/api/v1/simulation", tags=["simulation"])
_service = SimulationConversationService()


def get_simulation_service() -> SimulationConversationService:
    return _service


def set_simulation_service(service: SimulationConversationService) -> None:
    global _service
    _service = service


def _authorized(key: str | None) -> bool:
    return bool(settings.internal_api_key and key and
                hmac.compare_digest(settings.internal_api_key.encode(), key.encode()))


def _error(exc: SimulationProviderError, request_id: str, elapsed_ms: int) -> JSONResponse:
    logger.warning(
        "Simulation generation failed [requestId=%s provider=%s model=%s elapsedMs=%d "
        "operation=%s upstreamStatus=%s upstreamErrorStatus=%s upstreamField=%s "
        "code=%s httpStatus=%d retryable=%s status=failed]",
        request_id, exc.provider, exc.model, elapsed_ms, exc.operation or "unknown",
        exc.upstream_status or "n/a", exc.upstream_error_status or "n/a",
        exc.upstream_field or "n/a", exc.code, exc.status_code, exc.retryable,
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
            "provider": exc.provider,
            "model": exc.model,
            "upstreamStatus": exc.upstream_status,
        },
    )


@router.post("/reply", response_model=SimulationReplyResponseSchema)
async def reply(payload: SimulationReplyRequestSchema, response: Response,
                x_internal_api_key: str | None = Header(default=None),
                x_request_id: str | None = Header(default=None)):
    request_id = (x_request_id or "").strip() or str(uuid.uuid4())
    response.headers["X-Request-Id"] = request_id
    if not _authorized(x_internal_api_key):
        return JSONResponse(status_code=401, headers={"X-Request-Id": request_id}, content={
            "code": "UNAUTHORIZED_INTERNAL_CALL", "message": "No autorizado.",
            "retryable": False, "origin": "ai-service-auth", "requestId": request_id,
        })
    service = get_simulation_service()
    started = time.monotonic()
    logger.info("Simulation reply started [requestId=%s provider=%s model=%s status=processing]",
                request_id, service.provider.provider_name, service.provider.model_name)
    try:
        result = await asyncio.to_thread(service.reply, payload)
        logger.info("Simulation reply completed [requestId=%s provider=%s model=%s elapsedMs=%d status=completed]",
                    request_id, result.provider, result.model,
                    int((time.monotonic() - started) * 1000))
        return result
    except SimulationProviderError as exc:
        return _error(exc, request_id, int((time.monotonic() - started) * 1000))


@router.post("/feedback", response_model=SimulationFeedbackResponseSchema)
async def feedback(payload: SimulationFeedbackRequestSchema, response: Response,
                   x_internal_api_key: str | None = Header(default=None),
                   x_request_id: str | None = Header(default=None)):
    request_id = (x_request_id or "").strip() or str(uuid.uuid4())
    response.headers["X-Request-Id"] = request_id
    if not _authorized(x_internal_api_key):
        return JSONResponse(status_code=401, headers={"X-Request-Id": request_id}, content={
            "code": "UNAUTHORIZED_INTERNAL_CALL", "message": "No autorizado.",
            "retryable": False, "origin": "ai-service-auth", "requestId": request_id,
        })
    service = get_simulation_service()
    started = time.monotonic()
    logger.info("Simulation feedback started [requestId=%s provider=%s model=%s status=processing]",
                request_id, service.evaluation_provider.provider_name,
                service.evaluation_provider.model_name)
    try:
        result = await asyncio.to_thread(service.feedback, payload)
        logger.info("Simulation feedback completed [requestId=%s provider=%s model=%s elapsedMs=%d status=completed]",
                    request_id, result.provider, result.model,
                    int((time.monotonic() - started) * 1000))
        return result
    except SimulationProviderError as exc:
        return _error(exc, request_id, int((time.monotonic() - started) * 1000))

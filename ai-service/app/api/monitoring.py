import asyncio
import hmac
import logging
import time

from fastapi import APIRouter, Header
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.infrastructure.ollama import LocalModelError
from app.schemas.monitoring import MonitoringAnalyzeRequestSchema, MonitoringAnalyzeResponseSchema
from app.services.monitoring import analyst

logger = logging.getLogger("oratoria.ai.monitoring")
router = APIRouter(prefix="/api/v1/monitoring", tags=["monitoring"])


def _authorized(key: str | None) -> bool:
    return bool(
        settings.internal_api_key
        and key
        and hmac.compare_digest(settings.internal_api_key.encode(), key.encode())
    )


@router.post("/analyze", response_model=MonitoringAnalyzeResponseSchema)
async def analyze(
    payload: MonitoringAnalyzeRequestSchema, x_internal_api_key: str | None = Header(default=None)
):
    if not _authorized(x_internal_api_key):
        return JSONResponse(
            status_code=401,
            content={"code": "UNAUTHORIZED_INTERNAL_CALL", "message": "No autorizado.", "retryable": False},
        )
    started = time.monotonic()
    try:
        result = await asyncio.to_thread(analyst.analyze, payload)
        logger.info(
            "Monitoring analysis completed [status=%s findings=%d elapsedMs=%d]",
            result.status,
            len(result.findings),
            int((time.monotonic() - started) * 1000),
        )
        return result
    except LocalModelError as exc:
        logger.warning("Monitoring analysis failed [code=%s]", exc.code)
        return JSONResponse(
            status_code=exc.status,
            content={"code": exc.code, "message": exc.message, "retryable": exc.status in (429, 503, 504)},
        )

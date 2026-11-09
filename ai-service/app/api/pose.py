"""Internal, consent-driven snapshots API; Spring verifies session ownership."""

import asyncio
import hmac

from fastapi import APIRouter, Header
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app.core.config import settings
from app.infrastructure.ollama import LocalModelError
from app.services.pose.analyzer import analyze_frames

router = APIRouter(prefix="/api/v1/pose", tags=["pose"])


class PoseRequest(BaseModel):
    frames: list[str] = Field(min_length=1, max_length=12)


@router.post("/analyze")
async def analyze(payload: PoseRequest, x_internal_api_key: str | None = Header(default=None)):
    if not settings.internal_api_key or not hmac.compare_digest(
        (x_internal_api_key or "").encode(), settings.internal_api_key.encode()
    ):
        return JSONResponse(
            status_code=401, content={"code": "UNAUTHORIZED_INTERNAL_CALL", "message": "No autorizado."}
        )
    if any(len(frame) > 600000 for frame in payload.frames):
        return JSONResponse(
            status_code=413, content={"code": "FRAME_TOO_LARGE", "message": "Fotograma demasiado grande."}
        )
    try:
        return await asyncio.to_thread(analyze_frames, payload.frames)
    except LocalModelError as exc:
        return JSONResponse(status_code=exc.status, content={"code": exc.code, "message": exc.message})
    except (RuntimeError, OSError, ValueError):
        return JSONResponse(
            status_code=503,
            content={
                "code": "POSE_RUNTIME_ERROR",
                "message": "No se pudo ejecutar el modelo corporal local.",
            },
        )

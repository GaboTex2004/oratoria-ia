import asyncio
from dataclasses import dataclass
from email.parser import BytesParser
from email.policy import default as default_email_policy
import hmac
import logging
import uuid

from fastapi import APIRouter, Header, Request, Response
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.schemas.speech import SpeechAnalysisResponseSchema
from app.services.speech.analyzer import SpeechAnalysisService
from app.services.speech.provider import SpeechProviderError

logger = logging.getLogger("oratoria.ai.speech")

router = APIRouter(prefix="/api/v1/speech", tags=["speech"])

_default_service = SpeechAnalysisService()


def get_speech_analysis_service() -> SpeechAnalysisService:
    return _default_service


def set_speech_analysis_service(service: SpeechAnalysisService) -> None:
    global _default_service
    _default_service = service


def _verify_internal_key(provided_key: str | None) -> bool:
    expected = settings.internal_api_key
    if not expected or not provided_key:
        return False
    return hmac.compare_digest(provided_key.encode("utf-8"), expected.encode("utf-8"))


@dataclass
class ExtractedSpeechRequest:
    session_id: str | None
    duration_seconds: float | None
    filename: str | None
    mime_type: str | None
    audio_bytes: bytes


def _parse_multipart_payload(
    raw_body: bytes,
    content_type_header: str,
    header_session_id: str | None,
    header_duration: float | None,
    header_filename: str | None,
) -> ExtractedSpeechRequest:
    if not raw_body:
        raise SpeechProviderError(
            code="EMPTY_AUDIO",
            message="El cuerpo multipart de la solicitud está vacío.",
            status_code=400,
            retryable=False,
            origin="ai-service-validation",
        )

    mime_headers = (
        f"Content-Type: {content_type_header}\r\nMIME-Version: 1.0\r\n\r\n".encode("utf-8")
    )
    msg = BytesParser(policy=default_email_policy).parsebytes(mime_headers + raw_body)
    if not msg.is_multipart():
        raise SpeechProviderError(
            code="INVALID_MULTIPART_PAYLOAD",
            message="El cuerpo multipart/form-data no pudo ser interpretado correctamente.",
            status_code=400,
            retryable=False,
            origin="ai-service-validation",
        )

    fields: dict[str, str] = {}
    file_bytes: bytes = b""
    file_mime: str | None = None
    file_name: str | None = None

    for part in msg.iter_parts():
        if part.get_content_disposition() != "form-data":
            continue
        part_name = part.get_param("name", header="content-disposition")
        part_filename = part.get_filename()
        payload = part.get_payload(decode=True) or b""

        if part_filename is not None or part_name in ("file", "audio"):
            file_bytes = payload
            file_name = part_filename
            file_mime = part.get_content_type()
        elif isinstance(part_name, str):
            fields[part_name] = payload.decode("utf-8", errors="replace").strip()

    resolved_session_id = (
        header_session_id
        or fields.get("session_id")
        or fields.get("sessionId")
    )
    resolved_filename = (
        header_filename
        or file_name
        or fields.get("filename")
    )

    resolved_duration = header_duration
    if resolved_duration is None:
        raw_dur = fields.get("duration_seconds") or fields.get("durationSeconds")
        if raw_dur:
            try:
                resolved_duration = float(raw_dur)
            except ValueError:
                raise SpeechProviderError(
                    code="INVALID_AUDIO_DURATION",
                    message="El parámetro duration_seconds debe ser un número válido.",
                    status_code=400,
                    retryable=False,
                    origin="ai-service-validation",
                ) from None

    resolved_mime = file_mime or fields.get("mime_type") or fields.get("mimeType") or "audio/webm"
    return ExtractedSpeechRequest(
        session_id=resolved_session_id,
        duration_seconds=resolved_duration,
        filename=resolved_filename,
        mime_type=resolved_mime,
        audio_bytes=file_bytes,
    )


@router.post("/analyze", response_model=SpeechAnalysisResponseSchema)
async def analyze_speech(
    request: Request,
    response: Response,
    x_internal_api_key: str | None = Header(default=None),
    x_request_id: str | None = Header(default=None),
    x_session_id: str | None = Header(default=None),
    x_audio_duration_seconds: float | None = Header(default=None),
    x_audio_filename: str | None = Header(default=None),
    content_type: str | None = Header(default=None),
):
    request_id = (x_request_id or "").strip() or str(uuid.uuid4())
    response.headers["X-Request-Id"] = request_id

    raw_body = await request.body()

    if not _verify_internal_key(x_internal_api_key):
        logger.warning(
            "Speech analyze rejected [requestId=%s sessionId=%s status=401 origin=ai-service-auth code=UNAUTHORIZED_INTERNAL_CALL]",
            request_id,
            x_session_id or "unknown",
        )
        return JSONResponse(
            status_code=401,
            headers={"X-Request-Id": request_id},
            content={
                "code": "UNAUTHORIZED_INTERNAL_CALL",
                "message": "No autorizado para invocar el servicio interno de IA.",
                "retryable": False,
                "origin": "ai-service-auth",
                "requestId": request_id,
            },
        )

    try:
        normalized_ct = (content_type or "").strip().lower()
        if normalized_ct.startswith("multipart/form-data"):
            extracted = _parse_multipart_payload(
                raw_body=raw_body,
                content_type_header=content_type or "",
                header_session_id=x_session_id,
                header_duration=x_audio_duration_seconds,
                header_filename=x_audio_filename,
            )
        else:
            extracted = ExtractedSpeechRequest(
                session_id=x_session_id,
                duration_seconds=x_audio_duration_seconds,
                filename=x_audio_filename,
                mime_type=content_type,
                audio_bytes=raw_body,
            )

        if extracted.duration_seconds is None:
            logger.warning(
                "Speech analyze validation error [requestId=%s sessionId=%s status=400 origin=ai-service-validation code=MISSING_DURATION_HEADER]",
                request_id,
                extracted.session_id or "unknown",
            )
            return JSONResponse(
                status_code=400,
                headers={"X-Request-Id": request_id},
                content={
                    "code": "MISSING_DURATION_HEADER",
                    "message": "El encabezado X-Audio-Duration-Seconds o campo duration_seconds es obligatorio.",
                    "retryable": False,
                    "origin": "ai-service-validation",
                    "requestId": request_id,
                },
            )

        service = get_speech_analysis_service()
        logger.info(
            "Speech analyze started [requestId=%s sessionId=%s mime=%s bytes=%d duration=%.2fs]",
            request_id,
            extracted.session_id or "unknown",
            extracted.mime_type or "unknown",
            len(extracted.audio_bytes),
            float(extracted.duration_seconds),
        )

        result = await asyncio.to_thread(
            service.analyze,
            session_id=extracted.session_id or "",
            audio_bytes=extracted.audio_bytes,
            mime_type=extracted.mime_type or "",
            duration_seconds=float(extracted.duration_seconds),
            filename=extracted.filename,
        )

        logger.info(
            "Speech analyze completed [requestId=%s sessionId=%s status=200 words=%d wpm=%.2f]",
            request_id,
            extracted.session_id or "unknown",
            result.metrics.wordCount,
            result.metrics.wordsPerMinute,
        )
        return result
    except SpeechProviderError as exc:
        logger.warning(
            "Speech analyze failed [requestId=%s sessionId=%s status=%d origin=%s code=%s retryable=%s]",
            request_id,
            x_session_id or "unknown",
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

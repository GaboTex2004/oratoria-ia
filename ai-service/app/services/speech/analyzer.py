from app.core.config import settings
from app.schemas.speech import (
    SpeechAnalysisResponseSchema,
    TranscriptSegmentSchema,
    WordTimestampSchema,
)
from app.services.speech.metrics import calculate_verbal_metrics, normalize_transcript_text
from app.services.speech.provider import (
    GroqSpeechToTextProvider,
    SpeechProviderError,
    SpeechToTextProvider,
)

ALLOWED_AUDIO_MIMES: dict[str, str] = {
    "audio/webm": ".webm",
    "video/webm": ".webm",
    "audio/ogg": ".ogg",
    "audio/wav": ".wav",
    "audio/wave": ".wav",
    "audio/x-wav": ".wav",
    "audio/mp4": ".m4a",
    "audio/x-m4a": ".m4a",
    "audio/mpeg": ".mp3",
    "audio/mp3": ".mp3",
    "audio/aac": ".aac",
}


def normalize_mime_type(raw_mime: str | None) -> str:
    if not raw_mime:
        return ""
    base = raw_mime.split(";")[0].strip().lower()
    return base


class SpeechAnalysisService:
    def __init__(
        self,
        provider: SpeechToTextProvider | None = None,
        max_size_bytes: int | None = None,
        max_duration_seconds: int | None = None,
    ) -> None:
        self._provider = provider or GroqSpeechToTextProvider(
            api_key=settings.groq_api_key,
            model=settings.groq_model,
            api_url=settings.groq_api_url,
            timeout_seconds=settings.groq_timeout_seconds,
        )
        self._max_size_bytes = max_size_bytes or settings.max_audio_size_bytes
        self._max_duration_seconds = max_duration_seconds or settings.max_audio_duration_seconds

    def analyze(
        self,
        session_id: str,
        audio_bytes: bytes,
        mime_type: str,
        duration_seconds: float,
        filename: str | None = None,
    ) -> SpeechAnalysisResponseSchema:
        if not session_id or not session_id.strip():
            raise SpeechProviderError(
                code="INVALID_SESSION_ID",
                message="El identificador de sesión es obligatorio.",
                status_code=400,
                retryable=False,
                origin="ai-service-validation",
            )

        if duration_seconds <= 0 or duration_seconds > self._max_duration_seconds:
            raise SpeechProviderError(
                code="INVALID_AUDIO_DURATION",
                message=f"La duración del audio debe estar entre 1 y {self._max_duration_seconds} segundos.",
                status_code=400,
                retryable=False,
                origin="ai-service-validation",
            )

        if not audio_bytes or len(audio_bytes) == 0:
            raise SpeechProviderError(
                code="EMPTY_AUDIO",
                message="El archivo de audio enviado está vacío.",
                status_code=400,
                retryable=False,
                origin="ai-service-validation",
            )

        if len(audio_bytes) > self._max_size_bytes:
            raise SpeechProviderError(
                code="AUDIO_TOO_LARGE",
                message="El archivo de audio supera el tamaño máximo permitido.",
                status_code=413,
                retryable=False,
                origin="ai-service-validation",
            )

        normalized_mime = normalize_mime_type(mime_type)
        ext = ALLOWED_AUDIO_MIMES.get(normalized_mime)
        if not ext:
            raise SpeechProviderError(
                code="UNSUPPORTED_AUDIO_MIME",
                message=f"Tipo MIME de audio no soportado: {normalized_mime or 'desconocido'}.",
                status_code=400,
                retryable=False,
                origin="ai-service-validation",
            )

        effective_filename = (filename or "").strip() or f"session-{session_id.strip()}{ext}"
        if not effective_filename.lower().endswith(ext):
            effective_filename = f"{effective_filename}{ext}"

        raw = self._provider.transcribe(
            audio_bytes=audio_bytes,
            filename=effective_filename,
            mime_type=normalized_mime,
            language="es",
        )

        effective_duration = float(duration_seconds)
        if raw.duration_seconds and raw.duration_seconds > effective_duration:
            effective_duration = min(float(self._max_duration_seconds), float(raw.duration_seconds))

        normalized_transcript = normalize_transcript_text(raw.text)
        metrics = calculate_verbal_metrics(
            transcript_text=normalized_transcript,
            recording_duration_seconds=effective_duration,
            words=raw.words,
            segments=raw.segments,
        )

        return SpeechAnalysisResponseSchema(
            schemaVersion="v1",
            sessionId=session_id.strip(),
            provider=raw.provider,
            model=raw.model,
            language=raw.language,
            transcriptText=normalized_transcript,
            segments=[
                TranscriptSegmentSchema(
                    index=s.index,
                    startSeconds=round(s.start_seconds, 2),
                    endSeconds=round(s.end_seconds, 2),
                    text=s.text,
                )
                for s in raw.segments
            ],
            words=[
                WordTimestampSchema(
                    word=w.word,
                    startSeconds=round(w.start_seconds, 2),
                    endSeconds=round(w.end_seconds, 2),
                )
                for w in raw.words
            ],
            metrics=metrics,
        )

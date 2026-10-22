import json
import logging
import socket
import urllib.error
import urllib.request
import uuid
from abc import ABC, abstractmethod
from dataclasses import dataclass, field

logger = logging.getLogger("oratoria.ai.speech")


class SpeechProviderError(Exception):
    def __init__(
        self,
        code: str,
        message: str,
        status_code: int = 502,
        retryable: bool = False,
        origin: str = "groq-provider",
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code
        self.retryable = retryable
        self.origin = origin


@dataclass(frozen=True)
class RawWordTimestamp:
    word: str
    start_seconds: float
    end_seconds: float


@dataclass(frozen=True)
class RawSegmentTimestamp:
    index: int
    start_seconds: float
    end_seconds: float
    text: str


@dataclass(frozen=True)
class RawTranscriptionResult:
    provider: str
    model: str
    language: str
    text: str
    duration_seconds: float | None = None
    segments: list[RawSegmentTimestamp] = field(default_factory=list)
    words: list[RawWordTimestamp] = field(default_factory=list)


class SpeechToTextProvider(ABC):
    @abstractmethod
    def transcribe(
        self,
        audio_bytes: bytes,
        filename: str,
        mime_type: str,
        language: str = "es",
    ) -> RawTranscriptionResult:
        """Transcribe audio bytes and return text with word/segment timestamps if available."""


def _build_multipart_body(
    fields: list[tuple[str, str]],
    file_field: tuple[str, str, bytes, str],
) -> tuple[bytes, str]:
    boundary = f"----OratoriaAIBoundary{uuid.uuid4().hex}"
    chunks: list[bytes] = []

    for name, value in fields:
        chunks.append(f"--{boundary}\r\n".encode("utf-8"))
        chunks.append(
            f'Content-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode("utf-8")
        )

    field_name, filename, file_bytes, content_type = file_field
    safe_filename = filename.replace('"', "").replace("\r", "").replace("\n", "") or "audio.webm"
    chunks.append(f"--{boundary}\r\n".encode("utf-8"))
    chunks.append(
        (
            f'Content-Disposition: form-data; name="{field_name}"; filename="{safe_filename}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n"
        ).encode("utf-8")
    )
    chunks.append(file_bytes)
    chunks.append(f"\r\n--{boundary}--\r\n".encode("utf-8"))

    return b"".join(chunks), f"multipart/form-data; boundary={boundary}"


class GroqSpeechToTextProvider(SpeechToTextProvider):
    def __init__(
        self,
        api_key: str,
        model: str = "whisper-large-v3-turbo",
        api_url: str = "https://api.groq.com/openai/v1/audio/transcriptions",
        timeout_seconds: float = 60.0,
    ) -> None:
        self._api_key = api_key.strip()
        self._model = model.strip() or "whisper-large-v3-turbo"
        self._api_url = api_url.strip()
        self._timeout_seconds = max(5.0, float(timeout_seconds))

    def transcribe(
        self,
        audio_bytes: bytes,
        filename: str,
        mime_type: str,
        language: str = "es",
    ) -> RawTranscriptionResult:
        if not self._api_key:
            raise SpeechProviderError(
                code="GROQ_API_KEY_MISSING",
                message="La clave GROQ_API_KEY no está configurada en el servicio de IA.",
                status_code=503,
                retryable=False,
                origin="groq-provider",
            )

        fields = [
            ("model", self._model),
            ("language", language),
            ("response_format", "verbose_json"),
            ("temperature", "0"),
            ("timestamp_granularities[]", "word"),
            ("timestamp_granularities[]", "segment"),
        ]
        body, content_type_header = _build_multipart_body(
            fields=fields,
            file_field=("file", filename, audio_bytes, mime_type),
        )

        req = urllib.request.Request(
            url=self._api_url,
            data=body,
            method="POST",
            headers={
                "Authorization": f"Bearer {self._api_key}",
                "Content-Type": content_type_header,
                "Accept": "application/json",
                "User-Agent": "OratoriaAI-AIService/0.2",
            },
        )

        try:
            with urllib.request.urlopen(req, timeout=self._timeout_seconds) as response:
                raw_payload = response.read().decode("utf-8", errors="replace")
        except urllib.error.HTTPError as exc:
            raise self._map_http_error(exc) from None
        except (TimeoutError, socket.timeout):
            raise SpeechProviderError(
                code="PROVIDER_TIMEOUT",
                message="El servicio de transcripción agotó el tiempo de espera.",
                status_code=504,
                retryable=True,
                origin="groq-provider",
            ) from None
        except urllib.error.URLError:
            raise SpeechProviderError(
                code="PROVIDER_NETWORK_ERROR",
                message="No fue posible conectar con el proveedor de transcripción.",
                status_code=503,
                retryable=True,
                origin="groq-provider",
            ) from None

        try:
            payload = json.loads(raw_payload)
        except json.JSONDecodeError:
            raise SpeechProviderError(
                code="PROVIDER_INVALID_RESPONSE",
                message="El proveedor de transcripción devolvió una respuesta inválida.",
                status_code=502,
                retryable=True,
                origin="groq-provider",
            ) from None

        return self._parse_payload(payload, language)

    def _parse_payload(self, payload: dict, default_language: str) -> RawTranscriptionResult:
        text = str(payload.get("text") or "").strip()
        duration_raw = payload.get("duration")
        duration_val = float(duration_raw) if isinstance(duration_raw, (int, float)) else None

        words: list[RawWordTimestamp] = []
        raw_words = payload.get("words")
        if isinstance(raw_words, list):
            for item in raw_words:
                if not isinstance(item, dict):
                    continue
                word_str = str(item.get("word") or "").strip()
                start = item.get("start")
                end = item.get("end")
                if word_str and isinstance(start, (int, float)) and isinstance(end, (int, float)):
                    s_val = max(0.0, float(start))
                    e_val = max(s_val, float(end))
                    words.append(RawWordTimestamp(word=word_str, start_seconds=s_val, end_seconds=e_val))

        segments: list[RawSegmentTimestamp] = []
        raw_segments = payload.get("segments")
        if isinstance(raw_segments, list):
            for idx, item in enumerate(raw_segments):
                if not isinstance(item, dict):
                    continue
                seg_text = str(item.get("text") or "").strip()
                start = item.get("start")
                end = item.get("end")
                if seg_text and isinstance(start, (int, float)) and isinstance(end, (int, float)):
                    s_val = max(0.0, float(start))
                    e_val = max(s_val, float(end))
                    seg_id = item.get("id")
                    seg_index = int(seg_id) if isinstance(seg_id, int) and seg_id >= 0 else idx
                    segments.append(
                        RawSegmentTimestamp(
                            index=seg_index,
                            start_seconds=s_val,
                            end_seconds=e_val,
                            text=seg_text,
                        )
                    )

        return RawTranscriptionResult(
            provider="groq",
            model=self._model,
            language=default_language,
            text=text,
            duration_seconds=duration_val,
            segments=segments,
            words=words,
        )

    @staticmethod
    def _extract_safe_upstream_reason(exc: urllib.error.HTTPError) -> str:
        try:
            if getattr(exc, "fp", None) is None:
                return ""
            raw = exc.read(1024)
            if not raw:
                return ""
            parsed = json.loads(raw.decode("utf-8", errors="replace"))
            if isinstance(parsed, dict):
                err = parsed.get("error")
                if isinstance(err, dict) and isinstance(err.get("message"), str):
                    msg = err["message"].strip()
                    if "gsk_" not in msg and "Bearer" not in msg:
                        return msg[:180]
        except Exception:
            pass
        return ""

    @staticmethod
    def _map_http_error(exc: urllib.error.HTTPError) -> SpeechProviderError:
        status = exc.code
        upstream_reason = GroqSpeechToTextProvider._extract_safe_upstream_reason(exc)
        logger.warning(
            "Groq STT HTTP error: status=%s reason=%s",
            status,
            upstream_reason or "n/a",
        )
        if status in (401, 403):
            return SpeechProviderError(
                code="PROVIDER_AUTH_ERROR",
                message="Error de autenticación con el proveedor de transcripción (verifica GROQ_API_KEY).",
                status_code=502,
                retryable=False,
                origin="groq-provider",
            )
        if status == 429:
            return SpeechProviderError(
                code="PROVIDER_RATE_LIMIT",
                message="Se alcanzó el límite de solicitudes del proveedor de transcripción. Intenta más tarde.",
                status_code=429,
                retryable=True,
                origin="groq-provider",
            )
        if status in (400, 413, 415, 422):
            detail = f" Detalle: {upstream_reason}" if upstream_reason else ""
            return SpeechProviderError(
                code="PROVIDER_INVALID_AUDIO",
                message=f"El archivo de audio fue rechazado por el proveedor de transcripción.{detail}",
                status_code=400,
                retryable=False,
                origin="groq-provider",
            )
        return SpeechProviderError(
            code="PROVIDER_UPSTREAM_ERROR",
            message=f"Error temporal del servicio de transcripción (HTTP {status}).",
            status_code=502,
            retryable=status >= 500,
            origin="groq-provider",
        )

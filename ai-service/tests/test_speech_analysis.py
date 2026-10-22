import io
import json
import unittest
import urllib.error
from email.message import Message
from unittest.mock import patch

from app.api.speech import set_speech_analysis_service
from app.core.config import settings
from app.main import app
from app.services.speech.analyzer import SpeechAnalysisService
from app.services.speech.metrics import (
    calculate_verbal_metrics,
    detect_contextual_spanish_fillers,
)
from app.services.speech.provider import (
    GroqSpeechToTextProvider,
    RawSegmentTimestamp,
    RawTranscriptionResult,
    RawWordTimestamp,
    SpeechProviderError,
    SpeechToTextProvider,
    _build_multipart_body,
)


class FakeSpeechProvider(SpeechToTextProvider):
    def __init__(self, result: RawTranscriptionResult | None = None, error: SpeechProviderError | None = None) -> None:
        self._result = result
        self._error = error
        self.call_count = 0

    def transcribe(
        self,
        audio_bytes: bytes,
        filename: str,
        mime_type: str,
        language: str = "es",
    ) -> RawTranscriptionResult:
        self.call_count += 1
        if self._error:
            raise self._error
        assert self._result is not None
        return self._result


class VerbalMetricsUnitTests(unittest.TestCase):
    def test_contextual_fillers_do_not_penalize_grammatical_demonstratives_or_conjunctions(self) -> None:
        clean_speech = (
            "Buenos días jurado académico. Este proyecto de ingeniería resuelve este problema "
            "operativo, pues diseñamos una arquitectura modular con resultados verificables."
        )
        counts = detect_contextual_spanish_fillers(clean_speech)
        self.assertEqual(counts, {})

    def test_contextual_fillers_detect_hesitations_and_isolated_discourse_markers(self) -> None:
        hesitant_speech = (
            "Eh, este, bueno, nosotros desarrollamos este el módulo principal, "
            "o sea, pues, em, logramos terminar este"
        )
        counts = detect_contextual_spanish_fillers(hesitant_speech)
        self.assertEqual(counts.get("eh"), 1)
        self.assertEqual(counts.get("em"), 1)
        self.assertEqual(counts.get("este"), 3)  # "este,", "este el", trailing "este"
        self.assertEqual(counts.get("bueno"), 1)
        self.assertEqual(counts.get("o sea"), 1)
        self.assertEqual(counts.get("pues"), 1)

    def test_metrics_with_word_timestamps_compute_spoken_time_wpm_and_pauses(self) -> None:
        words = [
            RawWordTimestamp("Hola", 0.0, 0.5),
            RawWordTimestamp("equipo", 0.5, 1.0),
            # 1.2s pause (1.0 -> 2.2)
            RawWordTimestamp("presento", 2.2, 2.8),
            RawWordTimestamp("este", 2.8, 3.1),
            # 0.8s pause after "este" (3.1 -> 3.9) -> triggers contextual filler + pause!
            RawWordTimestamp("avance", 3.9, 4.5),
            RawWordTimestamp("final", 4.5, 5.0),
        ]
        metrics = calculate_verbal_metrics(
            transcript_text="Hola equipo presento este avance final",
            recording_duration_seconds=6.0,
            words=words,
        )
        self.assertEqual(metrics.wordCount, 6)
        self.assertEqual(metrics.recordingDurationSeconds, 6.0)
        self.assertTrue(metrics.timestampsAvailable)
        # Spoken intervals: [0.0..1.0] (1.0s) + [2.2..3.1] (0.9s) + [3.9..5.0] (1.1s) = 3.0s
        self.assertEqual(metrics.estimatedSpokenDurationSeconds, 3.0)
        self.assertEqual(metrics.spokenTimeRatio, 0.5)
        self.assertEqual(metrics.wordsPerMinute, 60.0)
        self.assertEqual(metrics.spokenWordsPerMinute, 120.0)
        self.assertEqual(metrics.estimatedPauseCount, 2)
        self.assertEqual(metrics.totalPauseDurationSeconds, 2.0)
        self.assertEqual(metrics.longestPauseSeconds, 1.2)
        self.assertEqual(metrics.fillerWordsTotal, 1)
        self.assertEqual(metrics.fillerWordCounts.get("este"), 1)

    def test_metrics_without_timestamps_mark_timestamps_unavailable_and_prevent_zero_division(self) -> None:
        metrics = calculate_verbal_metrics(
            transcript_text="Texto transcrito sin marcas de tiempo",
            recording_duration_seconds=0.0,
            words=[],
            segments=[],
        )
        self.assertEqual(metrics.wordCount, 6)
        self.assertEqual(metrics.wordsPerMinute, 0.0)
        self.assertFalse(metrics.timestampsAvailable)
        self.assertIsNone(metrics.estimatedSpokenDurationSeconds)
        self.assertIsNone(metrics.spokenTimeRatio)
        self.assertIsNone(metrics.estimatedPauseCount)


class GroqProviderUnitTests(unittest.TestCase):
    def test_missing_api_key_raises_non_retryable_error(self) -> None:
        provider = GroqSpeechToTextProvider(api_key="")
        with self.assertRaises(SpeechProviderError) as ctx:
            provider.transcribe(b"WEBM", "test.webm", "audio/webm")
        self.assertEqual(ctx.exception.code, "GROQ_API_KEY_MISSING")
        self.assertFalse(ctx.exception.retryable)

    def test_groq_provider_maps_401_429_and_timeout_errors(self) -> None:
        provider = GroqSpeechToTextProvider(api_key="test-key-not-real")

        headers = Message()
        err_401 = urllib.error.HTTPError("https://api.groq.com", 401, "Unauthorized", headers, io.BytesIO(b"{}"))
        with patch("urllib.request.urlopen", side_effect=err_401):
            with self.assertRaises(SpeechProviderError) as ctx401:
                provider.transcribe(b"WEBM", "test.webm", "audio/webm")
            self.assertEqual(ctx401.exception.code, "PROVIDER_AUTH_ERROR")
            self.assertFalse(ctx401.exception.retryable)

        err_429 = urllib.error.HTTPError("https://api.groq.com", 429, "Too Many Requests", headers, io.BytesIO(b"{}"))
        with patch("urllib.request.urlopen", side_effect=err_429):
            with self.assertRaises(SpeechProviderError) as ctx429:
                provider.transcribe(b"WEBM", "test.webm", "audio/webm")
            self.assertEqual(ctx429.exception.code, "PROVIDER_RATE_LIMIT")
            self.assertTrue(ctx429.exception.retryable)

        with patch("urllib.request.urlopen", side_effect=TimeoutError("timed out")):
            with self.assertRaises(SpeechProviderError) as ctx_timeout:
                provider.transcribe(b"WEBM", "test.webm", "audio/webm")
            self.assertEqual(ctx_timeout.exception.code, "PROVIDER_TIMEOUT")
            self.assertTrue(ctx_timeout.exception.retryable)


class SpeechApiAsgiTests(unittest.IsolatedAsyncioTestCase):
    async def _post_analyze(
        self,
        body: bytes,
        headers: list[tuple[bytes, bytes]],
    ) -> tuple[int, dict]:
        messages: list[dict] = []
        sent_body = False

        async def receive() -> dict:
            nonlocal sent_body
            if not sent_body:
                sent_body = True
                return {"type": "http.request", "body": body, "more_body": False}
            return {"type": "http.disconnect"}

        async def send(message: dict) -> None:
            messages.append(message)

        scope = {
            "type": "http",
            "asgi": {"version": "3.0"},
            "http_version": "1.1",
            "method": "POST",
            "scheme": "http",
            "path": "/api/v1/speech/analyze",
            "raw_path": b"/api/v1/speech/analyze",
            "query_string": b"",
            "headers": headers,
            "client": ("127.0.0.1", 12345),
            "server": ("127.0.0.1", 8000),
        }
        await app(scope, receive, send)
        start = next(m for m in messages if m["type"] == "http.response.start")
        raw = b"".join(m.get("body", b"") for m in messages if m["type"] == "http.response.body")
        return start["status"], json.loads(raw.decode("utf-8"))

    async def test_analyze_endpoint_enforces_internal_key_and_returns_structured_metrics(self) -> None:
        fake_result = RawTranscriptionResult(
            provider="groq",
            model="whisper-large-v3-turbo",
            language="es",
            text="Buenas tardes, eh, presentamos nuestra propuesta de oratoria.",
            duration_seconds=12.0,
            segments=[RawSegmentTimestamp(0, 0.2, 4.8, "Buenas tardes, eh, presentamos nuestra propuesta de oratoria.")],
            words=[
                RawWordTimestamp("Buenas", 0.2, 0.6),
                RawWordTimestamp("tardes,", 0.6, 1.1),
                RawWordTimestamp("eh,", 1.8, 2.1),
                RawWordTimestamp("presentamos", 2.8, 3.4),
                RawWordTimestamp("nuestra", 3.4, 3.8),
                RawWordTimestamp("propuesta", 3.8, 4.3),
                RawWordTimestamp("de", 4.3, 4.4),
                RawWordTimestamp("oratoria.", 4.4, 4.8),
            ],
        )
        fake_provider = FakeSpeechProvider(result=fake_result)
        set_speech_analysis_service(SpeechAnalysisService(provider=fake_provider))

        # 1. Missing internal key -> 401
        status_unauth, body_unauth = await self._post_analyze(
            b"WEBM_DATA",
            [(b"content-type", b"audio/webm"), (b"x-session-id", b"s-1"), (b"x-audio-duration-seconds", b"12")],
        )
        self.assertEqual(status_unauth, 401)
        self.assertEqual(body_unauth["code"], "UNAUTHORIZED_INTERNAL_CALL")
        self.assertEqual(fake_provider.call_count, 0)

        # 2. Valid internal key -> 200 OK with transcript and metrics
        status_ok, body_ok = await self._post_analyze(
            b"WEBM_DATA",
            [
                (b"x-internal-api-key", settings.internal_api_key.encode("utf-8")),
                (b"x-session-id", b"session-123"),
                (b"x-audio-duration-seconds", b"12"),
                (b"content-type", b"audio/webm;codecs=opus"),
            ],
        )
        self.assertEqual(status_ok, 200)
        self.assertEqual(body_ok["schemaVersion"], "v1")
        self.assertEqual(body_ok["sessionId"], "session-123")
        self.assertEqual(body_ok["provider"], "groq")
        self.assertEqual(body_ok["metrics"]["wordCount"], 8)
        self.assertEqual(body_ok["metrics"]["fillerWordsTotal"], 1)
        self.assertEqual(body_ok["metrics"]["fillerWordCounts"]["eh"], 1)
        self.assertEqual(body_ok["metrics"]["estimatedPauseCount"], 2)

        # 3. Valid multipart/form-data payload (Spring Boot HTTP_1_1 contract) -> 200 OK
        mp_body, mp_content_type = _build_multipart_body(
            fields=[("session_id", "session-mp-456"), ("duration_seconds", "12")],
            file_field=("file", "session-mp-456.webm", b"\x1a\x45\xdf\xa3WEBM_BINARY", "audio/webm"),
        )
        status_mp, body_mp = await self._post_analyze(
            mp_body,
            [
                (b"x-internal-api-key", settings.internal_api_key.encode("utf-8")),
                (b"x-request-id", b"req-mp-001"),
                (b"content-type", mp_content_type.encode("utf-8")),
            ],
        )
        self.assertEqual(status_mp, 200)
        self.assertEqual(body_mp["sessionId"], "session-mp-456")
        self.assertEqual(body_mp["metrics"]["wordCount"], 8)

        # 4. Validation error distinguishes origin=ai-service-validation and requestId
        status_val, body_val = await self._post_analyze(
            b"",
            [
                (b"x-internal-api-key", settings.internal_api_key.encode("utf-8")),
                (b"x-request-id", b"req-val-002"),
                (b"x-session-id", b"session-empty"),
                (b"x-audio-duration-seconds", b"10"),
                (b"content-type", b"audio/webm"),
            ],
        )
        self.assertEqual(status_val, 400)
        self.assertEqual(body_val["code"], "EMPTY_AUDIO")
        self.assertEqual(body_val["origin"], "ai-service-validation")
        self.assertEqual(body_val["requestId"], "req-val-002")
        self.assertFalse(body_val["retryable"])


if __name__ == "__main__":
    unittest.main()

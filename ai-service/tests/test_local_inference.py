"""No weights required: security boundaries, numerical rules and validation."""

import io
import json
import socket
import urllib.error
from dataclasses import replace
from unittest.mock import MagicMock, patch

import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.core.config import settings
from app.infrastructure.ollama import LocalModelError, chat_json, local_base_url
from app.main import app
from app.schemas.evaluation import SpeechEvaluationRequestSchema
from app.schemas.speech import VerbalCommunicationMetricsSchema
from app.services.evaluation.evaluator import SpeechEvaluationService
from app.services.evaluation.provider import EvaluationProviderError
from app.services.speech.analyzer import SpeechAnalysisService
from app.services.speech.nlp import discourse_indicators, rule_scores
from app.services.speech.provider import SpeechProviderError
from app.services.voice.acoustics import analyze_samples


@pytest.mark.parametrize(
    "url",
    [
        "https://api.example.com",
        "http://192.168.1.2:11434",
        "http://localhost.evil.test",
        "http://user@localhost:11434",
        "http://127.0.0.1:11434/redirect",
        "http://127.0.0.1?remote=1",
    ],
)
def test_remote_inference_urls_are_rejected(url):
    with pytest.raises(LocalModelError, match="loopback"):
        local_base_url(url)


@pytest.mark.parametrize("url", ["http://127.0.0.1:11434", "http://localhost:11434/", "http://[::1]:11434"])
def test_loopback_urls_are_allowed(url):
    assert local_base_url(url).startswith("http://")


def test_explicit_internal_ollama_hostname_is_allowed(monkeypatch):
    monkeypatch.setattr("app.infrastructure.ollama.settings", replace(settings, ollama_allowed_hosts=("ollama",)))
    assert local_base_url("http://ollama:11434") == "http://ollama:11434"


@pytest.mark.parametrize("url", ["http://ollama.evil:11434", "https://ollama:11434", "http://user@ollama:11434"])
def test_internal_allowlist_does_not_weaken_transport_rules(monkeypatch, url):
    monkeypatch.setattr("app.infrastructure.ollama.settings", replace(settings, ollama_allowed_hosts=("ollama",)))
    with pytest.raises(LocalModelError):
        local_base_url(url)


@pytest.mark.parametrize(
    "error,code",
    [
        (socket.timeout(), "LOCAL_MODEL_TIMEOUT"),
        (urllib.error.URLError("offline"), "LOCAL_RUNTIME_UNAVAILABLE"),
        (urllib.error.HTTPError("http://localhost", 404, "missing", {}, io.BytesIO()), "LOCAL_MODEL_MISSING"),
    ],
)
def test_runtime_failures_are_explicit_and_safe(error, code):
    with patch("urllib.request.build_opener") as opener:
        opener.return_value.open.side_effect = error
        with pytest.raises(LocalModelError) as raised:
            chat_json("system", "untrusted user text")
    assert raised.value.code == code


def test_local_transport_ignores_proxy_and_rejects_truncated_json():
    with patch("urllib.request.build_opener") as build:
        response = build.return_value.open.return_value.__enter__.return_value
        response.read.return_value = json.dumps(
            {"done": True, "done_reason": "length", "message": {"content": "{}"}}
        ).encode()
        with pytest.raises(LocalModelError) as raised:
            chat_json("system", "user")
        assert raised.value.code == "LOCAL_MODEL_INVALID_JSON"
        request = build.return_value.open.call_args.args[0]
        assert request.full_url.startswith("http://127.0.0.1:")
        assert json.loads(request.data)["options"]["num_gpu"] == settings.llm_num_gpu


def test_silence_has_no_pitch_or_fabricated_db_spl():
    result = analyze_samples(np.zeros(16000), 16000)
    assert result["rmsAmplitude"] == 0
    assert result["relativeLevelDbfs"] is None
    assert result["pitchMedianHz"] is None
    assert "dbSPL" not in result


def test_synthetic_tone_and_clipping_measurements():
    audio = 0.2 * np.sin(2 * np.pi * 200 * np.arange(16000) / 16000)
    result = analyze_samples(audio, 16000)
    assert result["rmsAmplitude"] == pytest.approx(0.2 / np.sqrt(2), abs=1e-5)
    assert result["pitchMedianHz"] == pytest.approx(200, abs=5)
    assert analyze_samples(np.ones(16000), 16000)["clippingRatio"] == 1


def test_spanish_structure_rules_are_bounded_and_reusable():
    indicators = discourse_indicators(
        "Primero explico el objetivo. Además presento datos y un ejemplo. En conclusión tenemos un resultado."
    )
    metrics = VerbalCommunicationMetricsSchema(
        wordCount=20, recordingDurationSeconds=15, wordsPerMinute=80, fillerWordsTotal=0
    )
    scores = rule_scores(indicators, metrics)
    assert indicators["structureMarkers"] == 3
    assert scores["ORGANIZATION"] == 100
    assert all(0 <= score <= 100 for score in scores.values())


def test_missing_semantic_criterion_does_not_become_seventy_points():
    provider = MagicMock()
    provider.evaluate_discourse.return_value = {"criteriaScores": []}
    metrics = VerbalCommunicationMetricsSchema(
        wordCount=20, recordingDurationSeconds=15, wordsPerMinute=80, fillerWordsTotal=0
    )
    request = SpeechEvaluationRequestSchema(
        sessionId="s1",
        trainingType="PRESENTATION",
        transcriptText="Primero explico una propuesta para mejorar las clases con ejemplos concretos y resultados verificables para los estudiantes.",
        metrics=metrics,
    )
    with pytest.raises(EvaluationProviderError) as raised:
        SpeechEvaluationService(provider).evaluate(request)
    assert raised.value.code == "INVALID_CRITERION_SCORE"


@pytest.mark.parametrize("duration", [float("nan"), float("inf"), -1])
def test_untrusted_nonfinite_duration_is_rejected(duration):
    with pytest.raises(SpeechProviderError) as raised:
        SpeechAnalysisService().analyze("s1", b"fake", "audio/wav", duration)
    assert raised.value.code == "INVALID_AUDIO_DURATION"


def test_private_route_authentication_precedes_invalid_json_parsing():
    with TestClient(app) as client:
        response = client.post("/api/v1/evaluation/evaluate", content=b"this is not JSON")
    assert response.status_code == 401


def test_request_memory_limit_applies_to_streamed_bodies():
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/speech/analyze",
            content=b"x" * (settings.max_audio_size_bytes + 65537),
            headers={"X-Internal-Api-Key": settings.internal_api_key},
        )
    assert response.status_code == 413

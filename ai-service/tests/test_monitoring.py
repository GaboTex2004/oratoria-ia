import io
import json
import threading
import time
import unittest
from unittest.mock import patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.monitoring import router
from app.core.config import settings
from app.infrastructure import ollama
from app.infrastructure.ollama import LocalModelError
from app.schemas.monitoring import MonitoringAnalyzeRequestSchema
from app.services.monitoring import analyst

# Solo el router de monitoreo: no carga Whisper, MediaPipe ni el resto de modelos.
app = FastAPI()
app.include_router(router)
client = TestClient(app)

METRICS = {"sesiones_trabadas": 3, "servicio_ia": "UP", "analisis_fallidos_1h": 0}
HEADERS = {"X-Internal-Api-Key": settings.internal_api_key}


def request(alerts=None, question=None):
    return MonitoringAnalyzeRequestSchema(metrics=METRICS, alerts=alerts or [], question=question)


class MonitoringAnalystTests(unittest.TestCase):
    @patch("app.infrastructure.ollama.chat_json")
    def test_findings_must_cite_existing_metrics(self, chat):
        chat.return_value = {
            "status": "ATENCION",
            "summary": "Hay prácticas trabadas.",
            "findings": [
                {
                    "title": "Prácticas trabadas",
                    "detail": "Tres prácticas llevan mucho tiempo.",
                    "evidence": ["sesiones_trabadas"],
                },
                {"title": "Inventado", "detail": "La CPU está al 99 %.", "evidence": ["cpu_inventada"]},
                {"title": "Sin evidencia", "detail": "Algo raro.", "evidence": []},
            ],
            "recommendations": ["Reintentar las prácticas trabadas."],
        }
        result = analyst.analyze(request())
        self.assertEqual([finding.title for finding in result.findings], ["Prácticas trabadas"])
        self.assertEqual(result.findings[0].evidence, ["sesiones_trabadas"])
        self.assertEqual(result.provider, "ollama")
        self.assertIn('"sesiones_trabadas": 3', chat.call_args.args[1])

    @patch("app.infrastructure.ollama.chat_json")
    def test_status_never_goes_below_the_rule_based_alerts(self, chat):
        chat.return_value = {
            "status": "NORMAL",
            "summary": "Todo bien.",
            "findings": [],
            "recommendations": [],
        }
        alerts = [{"code": "AI_DOWN", "severity": "CRITICO", "message": "El servicio de IA no responde."}]
        result = analyst.analyze(request(alerts=alerts))
        self.assertEqual(result.status, "CRITICO")
        # «Todo bien» con una alerta crítica: el resumen del modelo se descarta y se usa el de las reglas.
        self.assertTrue(result.summary_from_rules)
        self.assertIn("El servicio de IA no responde.", result.summary)
        # La respuesta del mismo análisis tampoco se muestra.
        chat.return_value = {
            "status": "NORMAL",
            "summary": "Todo bien.",
            "findings": [],
            "recommendations": [],
            "answer": "No hay alertas activas.",
        }
        self.assertIsNone(analyst.analyze(request(alerts=alerts, question="¿Hay problemas?")).answer)
        self.assertNotIn("Todo bien", result.summary)
        chat.return_value = {"status": "INVENTADO", "summary": "", "findings": [], "recommendations": []}
        result = analyst.analyze(request())
        self.assertEqual(result.status, "NORMAL")
        self.assertTrue(result.summary)
        self.assertFalse(result.summary_from_rules)

    @patch("app.infrastructure.ollama.chat_json")
    def test_the_question_is_sent_and_answered(self, chat):
        chat.return_value = {
            "status": "ATENCION",
            "summary": "s",
            "findings": [],
            "recommendations": [],
            "answer": "Porque el servicio de IA tardó.",
        }
        result = analyst.analyze(request(question="¿Por qué hay prácticas trabadas?"))
        self.assertEqual(result.answer, "Porque el servicio de IA tardó.")
        self.assertIn("¿Por qué hay prácticas trabadas?", chat.call_args.args[1])
        self.assertIn("<pregunta>¿Por qué hay prácticas trabadas?</pregunta>", chat.call_args.args[1])
        self.assertIsNone(analyst.analyze(request()).answer)


class MonitoringSchemaTests(unittest.TestCase):
    def test_evidence_can_only_name_received_metrics(self):
        schema = analyst.response_schema(request())
        evidence = schema["properties"]["findings"]["items"]["properties"]["evidence"]
        self.assertEqual(evidence["items"]["enum"], list(METRICS))
        self.assertNotIn("answer", schema["required"])
        self.assertNotIn("minItems", schema["properties"]["findings"])

    def test_alerts_and_questions_make_findings_and_answer_mandatory(self):
        alerts = [{"code": "AI_DOWN", "severity": "CRITICO", "message": "El servicio de IA no responde."}]
        schema = analyst.response_schema(request(alerts=alerts, question="¿Qué pasa?"))
        self.assertEqual(schema["properties"]["findings"]["minItems"], 1)
        # Límites para que la salida no se corte por tokens con muchas alertas.
        self.assertEqual(schema["properties"]["findings"]["maxItems"], 5)
        self.assertEqual(schema["properties"]["recommendations"]["maxItems"], 5)
        self.assertIn("answer", schema["required"])


class MonitoringApiTests(unittest.TestCase):
    def test_requires_the_internal_key(self):
        response = client.post("/api/v1/monitoring/analyze", json={"metrics": METRICS})
        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.json()["code"], "UNAUTHORIZED_INTERNAL_CALL")

    def test_rejects_oversized_or_unknown_input(self):
        too_many = {f"m{i}": i for i in range(81)}
        for body in (
            {"metrics": too_many},
            {"metrics": METRICS, "extra": 1},
            {"metrics": METRICS, "question": "x" * 301},
        ):
            self.assertEqual(
                client.post("/api/v1/monitoring/analyze", json=body, headers=HEADERS).status_code, 422
            )

    @patch("app.infrastructure.ollama.chat_json")
    def test_busy_model_is_reported_without_inventing_an_answer(self, chat):
        chat.side_effect = LocalModelError("LOCAL_MODEL_BUSY", "El modelo local está ocupado.", 429)
        response = client.post("/api/v1/monitoring/analyze", json={"metrics": METRICS}, headers=HEADERS)
        self.assertEqual(response.status_code, 429)
        self.assertEqual(
            response.json(),
            {"code": "LOCAL_MODEL_BUSY", "message": "El modelo local está ocupado.", "retryable": True},
        )

    @patch("app.infrastructure.ollama.chat_json")
    def test_successful_analysis(self, chat):
        chat.return_value = {
            "status": "NORMAL",
            "summary": "Todo funciona.",
            "findings": [],
            "recommendations": [],
        }
        response = client.post("/api/v1/monitoring/analyze", json={"metrics": METRICS}, headers=HEADERS)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "NORMAL")


class ModelPriorityTests(unittest.TestCase):
    """El monitoreo nunca hace esperar a una práctica: no espera turno, y las prácticas sí lo esperan."""

    @staticmethod
    def fake_ollama(*_args, **_kwargs):
        body = {"done": True, "done_reason": "stop", "message": {"content": json.dumps({"ok": True})}}
        response = io.BytesIO(json.dumps(body).encode())
        response.__enter__ = lambda self=response: self
        response.__exit__ = lambda *exc: False
        return response

    def test_monitoring_gives_up_at_once_while_a_practice_uses_the_model(self):
        self.assertTrue(ollama._generation_slot.acquire(blocking=False))
        try:
            started = time.monotonic()
            with self.assertRaises(LocalModelError) as busy:
                ollama.chat_json("s", "u", wait_seconds=0)
            self.assertEqual(busy.exception.code, "LOCAL_MODEL_BUSY")
            self.assertLess(time.monotonic() - started, 0.5)
        finally:
            ollama._generation_slot.release()

    def test_a_practice_waits_for_its_turn(self):
        self.assertTrue(ollama._generation_slot.acquire(blocking=False))
        threading.Timer(0.2, ollama._generation_slot.release).start()
        with patch("urllib.request.OpenerDirector.open", side_effect=self.fake_ollama):
            self.assertEqual(ollama.chat_json("s", "u", wait_seconds=5), {"ok": True})

    @patch("app.infrastructure.ollama.chat_json")
    def test_the_analyst_asks_for_low_priority_and_a_short_timeout(self, chat):
        chat.return_value = {"status": "NORMAL", "summary": "ok", "findings": [], "recommendations": []}
        analyst.analyze(request())
        self.assertEqual(chat.call_args.kwargs["wait_seconds"], 0)
        self.assertLess(chat.call_args.kwargs["timeout_seconds"], 150)


if __name__ == "__main__":
    unittest.main()

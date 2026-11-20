import unittest
from unittest.mock import patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.monitoring import router
from app.core.config import settings
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


if __name__ == "__main__":
    unittest.main()

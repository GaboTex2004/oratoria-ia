import json
import unittest

from app.api.health import health
from app.main import app


async def call_asgi_get(path: str) -> tuple[int, dict[str, str]]:
    messages: list[dict] = []

    async def receive() -> dict:
        return {"type": "http.request", "body": b"", "more_body": False}

    async def send(message: dict) -> None:
        messages.append(message)

    scope = {
        "type": "http",
        "asgi": {"version": "3.0"},
        "http_version": "1.1",
        "method": "GET",
        "scheme": "http",
        "path": path,
        "raw_path": path.encode("utf-8"),
        "query_string": b"",
        "headers": [(b"host", b"localhost:8000")],
        "client": ("127.0.0.1", 12345),
        "server": ("localhost", 8000),
    }
    await app(scope, receive, send)
    start = next(m for m in messages if m["type"] == "http.response.start")
    body = b"".join(m.get("body", b"") for m in messages if m["type"] == "http.response.body")
    return start["status"], json.loads(body.decode("utf-8"))


class HealthEndpointTests(unittest.IsolatedAsyncioTestCase):
    async def test_health_endpoint_responds_via_asgi_and_single_handler(self) -> None:
        status, payload = await call_asgi_get("/health")
        self.assertEqual(status, 200)
        self.assertEqual(payload, {"status": "ok", "service": "ai-service"})
        self.assertEqual(health(), payload)
        self.assertIn("/health", app.openapi()["paths"])

    async def test_root_endpoint_responds_via_asgi(self) -> None:
        status, payload = await call_asgi_get("/")
        self.assertEqual(status, 200)
        self.assertEqual(payload, {"message": "Oratoria IA Service funcionando"})


if __name__ == "__main__":
    unittest.main()

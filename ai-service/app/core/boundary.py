"""Authenticate before parsing, bound request memory and normalize request IDs."""

import hmac
import re
import uuid

from starlette.responses import JSONResponse

from app.core.config import settings


class LocalBoundaryMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or not scope["path"].startswith("/api/v1/"):
            return await self.app(scope, receive, send)
        headers = dict(scope.get("headers", []))
        raw_id = headers.get(b"x-request-id", b"").decode("ascii", errors="ignore")
        request_id = raw_id if re.fullmatch(r"[A-Za-z0-9_-]{1,80}", raw_id) else str(uuid.uuid4())

        async def reject(status, code, message):
            response = JSONResponse(
                status_code=status,
                headers={"X-Request-Id": request_id},
                content={
                    "code": code,
                    "message": message,
                    "retryable": False,
                    "origin": "ai-service-auth" if status == 401 else "ai-service-validation",
                    "requestId": request_id,
                },
            )
            await response(scope, receive, send)

        if not settings.internal_api_key or not hmac.compare_digest(
            headers.get(b"x-internal-api-key", b""), settings.internal_api_key.encode()
        ):
            return await reject(401, "UNAUTHORIZED_INTERNAL_CALL", "No autorizado.")
        chunks, size = [], 0
        while True:
            message = await receive()
            if message["type"] == "http.disconnect":
                return
            chunk = message.get("body", b"")
            size += len(chunk)
            if size > settings.max_audio_size_bytes + 65536:
                return await reject(413, "REQUEST_TOO_LARGE", "La solicitud supera el límite permitido.")
            chunks.append(chunk)
            if not message.get("more_body", False):
                break
        scope = dict(scope)
        scope["headers"] = [
            (name, value) for name, value in scope.get("headers", []) if name != b"x-request-id"
        ]
        scope["headers"].append((b"x-request-id", request_id.encode()))
        sent = False

        async def bounded_receive():
            nonlocal sent
            if not sent:
                sent = True
                return {"type": "http.request", "body": b"".join(chunks), "more_body": False}
            return await receive()

        await self.app(scope, bounded_receive, send)

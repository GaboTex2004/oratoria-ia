"""Bounded loopback-only JSON transport. No proxy, redirects or cloud fallback."""

import ipaddress
import json
import socket
import threading
import urllib.error
import urllib.parse
import urllib.request

from app.core.config import settings

_generation_slot = threading.BoundedSemaphore(1)


class LocalModelError(Exception):
    def __init__(self, code: str, message: str, status: int = 503):
        super().__init__(message)
        self.code, self.message, self.status = code, message, status


def local_base_url(raw: str) -> str:
    """Restrict inference to the same machine, without URL credentials."""
    parsed = urllib.parse.urlsplit(raw)
    try:
        loopback = ipaddress.ip_address(parsed.hostname or "").is_loopback
    except ValueError:
        loopback = parsed.hostname == "localhost"
    if (
        parsed.scheme != "http"
        or not loopback
        or parsed.username
        or parsed.password
        or parsed.query
        or parsed.fragment
        or parsed.path not in ("", "/")
    ):
        raise LocalModelError("REMOTE_INFERENCE_FORBIDDEN", "Ollama debe usar HTTP en loopback.")
    return raw.rstrip("/")


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise LocalModelError("INFERENCE_REDIRECT_FORBIDDEN", "Redirección de inferencia bloqueada.")


def chat_json(system: str, user: str, schema: dict | None = None, max_tokens: int = 1200) -> dict:
    """Generate a bounded object; fail explicitly if Ollama/model is unavailable."""
    base = local_base_url(settings.ollama_url)
    if not _generation_slot.acquire(blocking=False):
        raise LocalModelError(
            "LOCAL_MODEL_BUSY", "El modelo local está ocupado. Reintenta al finalizar.", 429
        )
    try:
        body = {
            "model": settings.ollama_model,
            "stream": False,
            "format": schema or "json",
            "keep_alive": "2m",
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user[:18000]}],
            "options": {
                "temperature": 0,
                "seed": 42,
                "num_ctx": settings.llm_context_tokens,
                "num_predict": max_tokens,
                "num_thread": settings.inference_threads,
                "num_gpu": settings.llm_num_gpu,
                "num_batch": 128,
            },
        }
        request = urllib.request.Request(
            base + "/api/chat",
            data=json.dumps(body, ensure_ascii=False).encode(),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), _NoRedirect())
        with opener.open(request, timeout=settings.llm_timeout_seconds) as response:
            outer = json.loads(response.read(262145))
        if not outer.get("done") or outer.get("done_reason") == "length":
            raise ValueError("truncated output")
        result = json.loads(outer["message"]["content"])
        if not isinstance(result, dict):
            raise ValueError("expected object")
        return result
    except urllib.error.HTTPError as exc:
        code = "LOCAL_MODEL_MISSING" if exc.code == 404 else "LOCAL_RUNTIME_ERROR"
        raise LocalModelError(code, "Revisa Ollama y el modelo local configurado.") from None
    except (TimeoutError, socket.timeout):
        raise LocalModelError(
            "LOCAL_MODEL_TIMEOUT", "El modelo local agotó el tiempo de espera.", 504
        ) from None
    except urllib.error.URLError:
        raise LocalModelError("LOCAL_RUNTIME_UNAVAILABLE", "Inicia el servidor local de Ollama.") from None
    except (ValueError, KeyError, TypeError):
        raise LocalModelError(
            "LOCAL_MODEL_INVALID_JSON", "El modelo local devolvió JSON incompleto.", 502
        ) from None
    finally:
        _generation_slot.release()

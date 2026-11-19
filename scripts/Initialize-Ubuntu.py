"""Create server-only settings without importing or displaying local secrets."""

import argparse
import os
from pathlib import Path
import re
import secrets


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--domain", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?", args.domain):
        raise SystemExit("Dominio inválido")
    root = Path(__file__).resolve().parents[1]
    target = root / ".env"
    if target.exists():
        raise SystemExit("La configuración ya existe; se conserva sin sobrescribirla.")
    values = {
        "FRONTEND_PORT": "15173",
        "PUBLIC_DOMAIN": args.domain,
        "PUBLIC_ORIGIN": f"https://{args.domain}",
        "ORATORIA_DB_NAME": "oratoriaai_demo",
        "ORATORIA_DB_USER": "oratoria",
        "ORATORIA_DB_PASSWORD": secrets.token_urlsafe(36),
        "JWT_SECRET": secrets.token_urlsafe(64),
        "INTERNAL_API_KEY": secrets.token_urlsafe(48),
        "ADMIN_EMAIL": "admin@oratoria.local",
        "ADMIN_PASSWORD": secrets.token_urlsafe(24),
        "APP_PROFILE": "demo",
        "BILLING_DEMO_ENABLED": "true",
        "OLLAMA_MODEL": "qwen2.5:1.5b",
        "LLM_CONTEXT_TOKENS": "4096",
        "LLM_NUM_GPU": "0",
        "AUDIO_MAX_DURATION_SECONDS": "300",
    }
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
        output.write("".join(f"{key}={value}\n" for key, value in values.items()))
    print("Configuración privada del servidor creada. No se imprimen credenciales.")


if __name__ == "__main__":
    main()

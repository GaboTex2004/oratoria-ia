# AI Service

Esqueleto FastAPI para análisis futuro de voz, postura, rostro, discurso y evaluación.
No contiene modelos de IA ni está conectado todavía al backend.

```bash
python -m venv .venv
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Endpoint inicial: `GET /health`.

La configuración se centraliza en `app/core/config.py` y se obtiene del entorno.
Consulta `.env.example`; el servicio no carga archivos `.env` automáticamente.
# Simulaciones conversacionales

Spring Boot invoca internamente, usando `X-Internal-Api-Key`:

- `POST /api/v1/simulation/reply`: continúa un escenario a partir de la transcripción.
- `POST /api/v1/simulation/feedback`: emite la evaluación conversacional final.

El historial se limita mediante `SIMULATION_MAX_HISTORY_MESSAGES` y cada mensaje
mediante `SIMULATION_MAX_MESSAGE_CHARS`. Las transcripciones se tratan como datos no
confiables y nunca como instrucciones. Si existen menos de
`SIMULATION_MIN_USER_TURNS` respuestas, el servicio devuelve evidencia insuficiente
sin invocar al LLM ni generar puntuación.

La selección es independiente para turnos y evaluación final:

```dotenv
SIMULATION_LLM_PROVIDER=gemini
SIMULATION_EVALUATION_PROVIDER=gemini
SIMULATION_GEMINI_MODEL=gemini-3.8-flash
SIMULATION_GEMINI_REPLY_THINKING_LEVEL=low
SIMULATION_GEMINI_FEEDBACK_THINKING_LEVEL=medium
GEMINI_API_KEY=<CLAVE_PRIVADA>
```

Groq continúa realizando Whisper STT, análisis de voz y evaluación tradicional. Si no
se define `SIMULATION_LLM_PROVIDER`, las simulaciones también continúan con Groq. No
hay fallback silencioso: una clave ausente, proveedor desconocido o modelo no
disponible produce un código de error explícito.

En PowerShell, configura Gemini sólo en la terminal que iniciará FastAPI:

```powershell
$env:SIMULATION_LLM_PROVIDER="gemini"
$env:SIMULATION_EVALUATION_PROVIDER="gemini"
$env:SIMULATION_GEMINI_MODEL="gemini-3.8-flash"
$env:SIMULATION_GEMINI_REPLY_THINKING_LEVEL="low"
$env:SIMULATION_GEMINI_FEEDBACK_THINKING_LEVEL="medium"
$env:GEMINI_API_KEY="<TU_CLAVE_DE_GOOGLE_AI_STUDIO>"
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

Para diagnóstico compatible con la instalación anterior, establece ambos proveedores
en `groq`. Las respuestas 401/403, 404, 429, timeout, parámetros inválidos y 5xx se
registran con códigos distintos, `requestId`, proveedor, modelo, duración y estado HTTP
upstream, sin registrar claves, prompts completos ni transcripciones.

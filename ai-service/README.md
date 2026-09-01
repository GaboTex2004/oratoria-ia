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

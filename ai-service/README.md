# AI Service

Esqueleto FastAPI para análisis futuro de voz, postura, rostro, discurso y evaluación.
No contiene modelos de IA ni está conectado todavía al backend.

```bash
python -m venv .venv
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Endpoint inicial: `GET /health`.

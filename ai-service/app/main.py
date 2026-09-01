from fastapi import FastAPI

from app.core.config import settings

app = FastAPI(
    title="Oratoria IA Service",
    version="0.1.0",
    debug=settings.app_env == "development",
)


@app.get("/")
def root():
    return {
        "message": "Oratoria IA Service funcionando"
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "oratoria-ai"
    }

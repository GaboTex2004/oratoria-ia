from fastapi import FastAPI

app = FastAPI(
    title="Oratoria IA Service",
    version="0.1.0"
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
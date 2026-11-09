from fastapi import FastAPI
from app.core.boundary import LocalBoundaryMiddleware

from app.api.evaluation import router as evaluation_router
from app.api.health import router as health_router
from app.api.speech import router as speech_router
from app.api.simulation import router as simulation_router
from app.api.pose import router as pose_router

app = FastAPI(
    title="Oratoria IA Service",
    version="0.3.0",
    debug=False,
)

app.include_router(health_router)
app.include_router(speech_router)
app.include_router(evaluation_router)
app.include_router(simulation_router)
app.include_router(pose_router)
app.add_middleware(LocalBoundaryMiddleware)


@app.get("/")
def root() -> dict[str, str]:
    return {"message": "Oratoria IA Service funcionando"}

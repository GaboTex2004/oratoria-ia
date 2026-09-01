import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    host: str = os.getenv("AI_SERVICE_HOST", "0.0.0.0")
    port: int = int(os.getenv("AI_SERVICE_PORT", "8000"))
    spring_api_url: str = os.getenv(
        "SPRING_API_URL", "http://localhost:8081/api"
    ).rstrip("/")
    app_env: str = os.getenv("APP_ENV", "development")


settings = Settings()

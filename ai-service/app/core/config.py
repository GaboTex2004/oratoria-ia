import os
from dataclasses import dataclass
from pathlib import Path

try:
    from dotenv import load_dotenv
    env_path = Path(__file__).resolve().parents[2] / ".env"
    if env_path.exists():
        load_dotenv(dotenv_path=env_path, override=False)
except ImportError:
    pass


@dataclass(frozen=True)
class Settings:
    host: str = os.getenv("AI_SERVICE_HOST", "127.0.0.1")
    port: int = int(os.getenv("AI_SERVICE_PORT", "8000"))
    spring_api_url: str = os.getenv(
        "SPRING_API_URL", "http://localhost:8081/api"
    ).rstrip("/")
    app_env: str = os.getenv("APP_ENV", "development")
    internal_api_key: str = os.getenv(
        "INTERNAL_API_KEY", "dev-internal-oratoria-key-change-in-prod"
    )
    groq_api_key: str = os.getenv("GROQ_API_KEY", "").strip()
    groq_model: str = os.getenv("GROQ_MODEL", "whisper-large-v3-turbo").strip()
    groq_api_url: str = os.getenv(
        "GROQ_API_URL", "https://api.groq.com/openai/v1/audio/transcriptions"
    ).strip()
    groq_timeout_seconds: float = float(os.getenv("GROQ_TIMEOUT_SECONDS", "60"))
    groq_llm_model: str = os.getenv("GROQ_LLM_MODEL", "openai/gpt-oss-120b").strip()
    groq_chat_api_url: str = os.getenv(
        "GROQ_CHAT_API_URL", "https://api.groq.com/openai/v1/chat/completions"
    ).strip()
    groq_llm_timeout_seconds: float = float(os.getenv("GROQ_LLM_TIMEOUT_SECONDS", "45"))
    evaluation_min_words: int = int(os.getenv("EVALUATION_MIN_WORDS", "10"))
    evaluation_min_duration_seconds: float = float(
        os.getenv("EVALUATION_MIN_DURATION_SECONDS", "4.0")
    )
    max_audio_size_bytes: int = int(os.getenv("AUDIO_MAX_SIZE_BYTES", "15728640"))
    max_audio_duration_seconds: int = int(os.getenv("AUDIO_MAX_DURATION_SECONDS", "300"))


settings = Settings()

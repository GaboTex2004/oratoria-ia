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
    simulation_llm_provider: str = os.getenv("SIMULATION_LLM_PROVIDER", "groq").strip().lower()
    simulation_evaluation_provider: str = os.getenv(
        "SIMULATION_EVALUATION_PROVIDER", os.getenv("SIMULATION_LLM_PROVIDER", "groq")
    ).strip().lower()
    simulation_groq_model: str = os.getenv(
        "SIMULATION_GROQ_MODEL", os.getenv("GROQ_LLM_MODEL", "openai/gpt-oss-120b")
    ).strip()
    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "").strip()
    simulation_gemini_model: str = os.getenv("SIMULATION_GEMINI_MODEL", "gemini-3.8-flash").strip()
    simulation_gemini_reply_thinking_level: str = os.getenv(
        "SIMULATION_GEMINI_REPLY_THINKING_LEVEL", "low"
    ).strip().lower()
    simulation_gemini_feedback_thinking_level: str = os.getenv(
        "SIMULATION_GEMINI_FEEDBACK_THINKING_LEVEL", "medium"
    ).strip().lower()
    gemini_api_url: str = os.getenv(
        "GEMINI_API_URL", "https://generativelanguage.googleapis.com/v1beta"
    ).strip().rstrip("/")
    simulation_llm_timeout_seconds: float = float(os.getenv("SIMULATION_LLM_TIMEOUT_SECONDS", "45"))
    simulation_reply_max_output_tokens: int = int(os.getenv("SIMULATION_REPLY_MAX_OUTPUT_TOKENS", "700"))
    simulation_feedback_max_output_tokens: int = int(os.getenv("SIMULATION_FEEDBACK_MAX_OUTPUT_TOKENS", "1600"))
    evaluation_min_words: int = int(os.getenv("EVALUATION_MIN_WORDS", "10"))
    evaluation_min_duration_seconds: float = float(
        os.getenv("EVALUATION_MIN_DURATION_SECONDS", "4.0")
    )
    simulation_min_user_turns: int = int(os.getenv("SIMULATION_MIN_USER_TURNS", "2"))
    simulation_max_history_messages: int = int(os.getenv("SIMULATION_MAX_HISTORY_MESSAGES", "12"))
    simulation_max_message_chars: int = int(os.getenv("SIMULATION_MAX_MESSAGE_CHARS", "2000"))
    max_audio_size_bytes: int = int(os.getenv("AUDIO_MAX_SIZE_BYTES", "15728640"))
    max_audio_duration_seconds: int = int(os.getenv("AUDIO_MAX_DURATION_SECONDS", "300"))


settings = Settings()

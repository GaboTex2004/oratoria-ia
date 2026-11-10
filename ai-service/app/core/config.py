"""Configuration for local-only inference; no implicit downloads."""

import os
from dataclasses import dataclass
from pathlib import Path
from dotenv import load_dotenv

SERVICE_ROOT = Path(__file__).resolve().parents[2]
load_dotenv(SERVICE_ROOT / ".env", override=False)


@dataclass(frozen=True)
class Settings:
    host: str = os.getenv("AI_SERVICE_HOST", "127.0.0.1")
    port: int = int(os.getenv("AI_SERVICE_PORT", "8000"))
    app_env: str = os.getenv("APP_ENV", "development")
    internal_api_key: str = os.getenv("INTERNAL_API_KEY", "")
    whisper_model_path: str = os.getenv("WHISPER_MODEL_PATH", str(SERVICE_ROOT / "models" / "whisper-base"))
    whisper_device: str = os.getenv("WHISPER_DEVICE", "cpu")
    whisper_compute_type: str = os.getenv("WHISPER_COMPUTE_TYPE", "int8")
    inference_threads: int = int(os.getenv("INFERENCE_THREADS", "4"))
    llm_num_gpu: int = int(os.getenv("LLM_NUM_GPU", "0"))
    ollama_url: str = os.getenv("OLLAMA_URL", "http://127.0.0.1:11434")
    ollama_allowed_hosts: tuple[str, ...] = tuple(
        host.strip().lower().rstrip(".")
        for host in os.getenv("OLLAMA_ALLOWED_HOSTS", "").split(",")
        if host.strip()
    )
    ollama_model: str = os.getenv("OLLAMA_MODEL", "qwen2.5:1.5b")
    llm_timeout_seconds: float = float(os.getenv("LLM_TIMEOUT_SECONDS", "240"))
    llm_context_tokens: int = int(os.getenv("LLM_CONTEXT_TOKENS", "4096"))
    pose_model_path: str = os.getenv(
        "POSE_MODEL_PATH", str(SERVICE_ROOT / "models" / "pose_landmarker_lite.task")
    )
    simulation_llm_provider: str = os.getenv("SIMULATION_LLM_PROVIDER", "ollama")
    simulation_evaluation_provider: str = os.getenv("SIMULATION_EVALUATION_PROVIDER", "ollama")
    simulation_reply_max_output_tokens: int = 350
    simulation_feedback_max_output_tokens: int = 1200
    evaluation_min_words: int = 10
    evaluation_min_duration_seconds: float = 4.0
    simulation_min_user_turns: int = 2
    simulation_max_history_messages: int = 10
    simulation_max_message_chars: int = 1200
    max_audio_size_bytes: int = int(os.getenv("AUDIO_MAX_SIZE_BYTES", "15728640"))
    max_audio_duration_seconds: int = int(os.getenv("AUDIO_MAX_DURATION_SECONDS", "300"))


settings = Settings()

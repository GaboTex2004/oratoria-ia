from abc import ABC, abstractmethod
from dataclasses import dataclass, field


class SpeechProviderError(Exception):
    def __init__(
        self,
        code: str,
        message: str,
        status_code: int = 502,
        retryable: bool = False,
        origin: str = "local-whisper",
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code
        self.retryable = retryable
        self.origin = origin


@dataclass(frozen=True)
class RawWordTimestamp:
    word: str
    start_seconds: float
    end_seconds: float


@dataclass(frozen=True)
class RawSegmentTimestamp:
    index: int
    start_seconds: float
    end_seconds: float
    text: str


@dataclass(frozen=True)
class RawTranscriptionResult:
    provider: str
    model: str
    language: str
    text: str
    duration_seconds: float | None = None
    segments: list[RawSegmentTimestamp] = field(default_factory=list)
    words: list[RawWordTimestamp] = field(default_factory=list)
    acoustic_metrics: dict[str, float | None] = field(default_factory=dict)


class SpeechToTextProvider(ABC):
    @abstractmethod
    def transcribe(
        self,
        audio_bytes: bytes,
        filename: str,
        mime_type: str,
        language: str = "es",
    ) -> RawTranscriptionResult:
        """Transcribe audio bytes and return text with word/segment timestamps if available."""

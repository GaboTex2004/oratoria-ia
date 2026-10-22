from pydantic import BaseModel, Field


class WordTimestampSchema(BaseModel):
    word: str
    startSeconds: float = Field(ge=0.0)
    endSeconds: float = Field(ge=0.0)


class TranscriptSegmentSchema(BaseModel):
    index: int = Field(ge=0)
    startSeconds: float = Field(ge=0.0)
    endSeconds: float = Field(ge=0.0)
    text: str


class VerbalCommunicationMetricsSchema(BaseModel):
    wordCount: int = Field(ge=0)
    recordingDurationSeconds: float = Field(ge=0.0)
    estimatedSpokenDurationSeconds: float | None = None
    spokenTimeRatio: float | None = None
    wordsPerMinute: float = Field(ge=0.0)
    spokenWordsPerMinute: float | None = None
    fillerWordsTotal: int = Field(ge=0)
    fillerWordCounts: dict[str, int] = Field(default_factory=dict)
    estimatedPauseCount: int | None = None
    totalPauseDurationSeconds: float | None = None
    longestPauseSeconds: float | None = None
    timestampsAvailable: bool = False


class SpeechAnalysisResponseSchema(BaseModel):
    schemaVersion: str = "v1"
    sessionId: str
    provider: str
    model: str
    language: str
    transcriptText: str
    segments: list[TranscriptSegmentSchema] = Field(default_factory=list)
    words: list[WordTimestampSchema] = Field(default_factory=list)
    metrics: VerbalCommunicationMetricsSchema

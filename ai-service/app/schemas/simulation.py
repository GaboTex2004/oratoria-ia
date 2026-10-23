from typing import Literal
from pydantic import BaseModel, Field

class SimulationHistoryMessageSchema(BaseModel):
    speaker: Literal["USER", "AI"]
    text: str = Field(..., min_length=1, max_length=4000)

class SimulationReplyRequestSchema(BaseModel):
    simulationId: str = Field(..., min_length=1)
    scenarioId: str = Field(..., min_length=1)
    trainingType: str = Field(..., min_length=1)
    difficulty: Literal["BASIC", "INTERMEDIATE", "ADVANCED"]
    aiRole: str = Field(..., min_length=1, max_length=120)
    context: str = Field(..., min_length=1, max_length=1000)
    objective: str = Field(..., min_length=1, max_length=1000)
    rules: list[str] = Field(default_factory=list, max_length=12)
    history: list[SimulationHistoryMessageSchema] = Field(default_factory=list, max_length=30)
    userTranscript: str = Field(..., min_length=1, max_length=8000)
    turnNumber: int = Field(..., ge=1, le=20)
    maxTurns: int = Field(..., ge=1, le=20)

class SimulationReplyResponseSchema(BaseModel):
    replyText: str = Field(..., min_length=1, max_length=1500)
    intent: str = Field(..., min_length=1, max_length=100)
    shouldEnd: bool = False
    focus: str = Field(default="", max_length=300)
    provider: str
    model: str

class SimulationFeedbackRequestSchema(BaseModel):
    simulationId: str = Field(..., min_length=1)
    scenarioId: str = Field(..., min_length=1)
    trainingType: str = Field(..., min_length=1)
    difficulty: Literal["BASIC", "INTERMEDIATE", "ADVANCED"]
    context: str = Field(..., min_length=1, max_length=1000)
    objective: str = Field(..., min_length=1, max_length=1000)
    history: list[SimulationHistoryMessageSchema] = Field(default_factory=list, max_length=40)

class SimulationFeedbackResponseSchema(BaseModel):
    sufficientEvidence: bool
    insufficientEvidenceReason: str | None = None
    overallScore: float | None = Field(default=None, ge=0, le=100)
    summary: str
    strengths: list[str] = Field(default_factory=list, max_length=6)
    areasForImprovement: list[str] = Field(default_factory=list, max_length=6)
    recommendations: list[str] = Field(default_factory=list, max_length=6)
    criteriaScores: dict[str, float] = Field(default_factory=dict)
    provider: str
    model: str

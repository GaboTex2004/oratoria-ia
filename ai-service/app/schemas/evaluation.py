from typing import Literal
from pydantic import BaseModel, Field

from app.schemas.speech import VerbalCommunicationMetricsSchema


class SpeechEvaluationRequestSchema(BaseModel):
    sessionId: str = Field(..., min_length=1)
    trainingType: str = Field(..., min_length=1)
    exerciseTitle: str | None = None
    exercisePrompt: str | None = None
    transcriptText: str = Field(default="")
    metrics: VerbalCommunicationMetricsSchema


class MeasuredIndicatorSchema(BaseModel):
    code: str
    label: str
    value: float | int | None = None
    unit: str
    interpretation: str
    source: Literal["MEASURED"] = "MEASURED"


class CriterionEvaluationSchema(BaseModel):
    code: Literal["CLARITY", "COHERENCE", "ORGANIZATION", "ARGUMENTATION"]
    label: str
    weight: float = Field(..., ge=0.0, le=1.0)
    score: float | None = Field(default=None, ge=0.0, le=100.0)
    level: Literal["INSUFFICIENT_EVIDENCE", "INICIAL", "EN_DESARROLLO", "COMPETENTE", "SOBRESALIENTE"]
    justification: str
    evidenceQuotes: list[str] = Field(default_factory=list)
    judgmentType: Literal["AI_ESTIMATED"] = "AI_ESTIMATED"


class ActionableRecommendationSchema(BaseModel):
    title: str
    description: str
    priority: Literal["ALTA", "MEDIA", "SUGERIDA"] = "MEDIA"


class ImprovedResponseExampleSchema(BaseModel):
    originalFragment: str
    improvedProposal: str
    rationale: str


class SpeechEvaluationResponseSchema(BaseModel):
    schemaVersion: str = "v1"
    sessionId: str
    trainingType: str
    rubricVersion: str
    provider: str
    model: str
    sufficientEvidence: bool
    insufficientEvidenceReason: str | None = None
    overallScore: float | None = Field(default=None, ge=0.0, le=100.0)
    executiveSummary: str
    measuredIndicators: list[MeasuredIndicatorSchema] = Field(default_factory=list)
    criteriaScores: list[CriterionEvaluationSchema] = Field(default_factory=list)
    strengths: list[str] = Field(default_factory=list)
    areasForImprovement: list[str] = Field(default_factory=list)
    recommendations: list[ActionableRecommendationSchema] = Field(default_factory=list)
    improvedExamples: list[ImprovedResponseExampleSchema] = Field(default_factory=list)

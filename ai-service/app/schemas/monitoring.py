from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

Severity = Literal["NORMAL", "ATENCION", "CRITICO"]
MetricValue = int | float | bool | str


class MonitoringAlertSchema(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: str = Field(min_length=1, max_length=60)
    severity: Severity
    message: str = Field(min_length=1, max_length=300)


class MonitoringAnalyzeRequestSchema(BaseModel):
    """Métricas ya calculadas por el backend. El modelo solo las interpreta; nunca recibe datos personales."""

    model_config = ConfigDict(extra="forbid")

    metrics: dict[str, MetricValue] = Field(min_length=1, max_length=80)
    alerts: list[MonitoringAlertSchema] = Field(default_factory=list, max_length=30)
    question: str | None = Field(default=None, max_length=300)

    @field_validator("metrics")
    @classmethod
    def bounded_metrics(cls, metrics: dict[str, MetricValue]) -> dict[str, MetricValue]:
        for key, value in metrics.items():
            if not key or len(key) > 60:
                raise ValueError("Nombre de métrica inválido.")
            if isinstance(value, str) and len(value) > 120:
                raise ValueError("Valor de métrica demasiado largo.")
        return metrics


class MonitoringFindingSchema(BaseModel):
    title: str
    detail: str
    evidence: list[str]


class MonitoringAnalyzeResponseSchema(BaseModel):
    status: Severity
    summary: str
    findings: list[MonitoringFindingSchema]
    recommendations: list[str]
    answer: str | None = None
    # True cuando el resumen del modelo contradecía las alertas y se reemplazó por uno basado solo en ellas.
    summary_from_rules: bool = False
    provider: str
    model: str

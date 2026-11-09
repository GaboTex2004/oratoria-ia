"""Reusable deterministic Spanish text indicators; no claims of learned accuracy."""

import re
import unicodedata
from collections import Counter


def discourse_indicators(text: str) -> dict:
    normalized = "".join(
        char for char in unicodedata.normalize("NFD", text.lower()) if unicodedata.category(char) != "Mn"
    )
    tokens = re.findall(r"[a-zñ]+", normalized)
    sentences = [part for part in re.split(r"[.!?]+", normalized) if part.strip()]
    counts = Counter(tokens)
    connectors = ("porque", "por tanto", "sin embargo", "ademas", "por ejemplo", "en conclusion")
    structure = (
        any(mark in normalized for mark in ("primero", "introduccion", "objetivo", "hoy")),
        any(mark in normalized for mark in ("ademas", "por ejemplo", "segundo", "porque")),
        any(mark in normalized for mark in ("conclusion", "finalmente", "por tanto", "para terminar")),
    )
    return {
        "wordCount": len(tokens),
        "sentenceCount": len(sentences),
        "meanSentenceWords": len(tokens) / max(1, len(sentences)),
        "lexicalDiversity": len(counts) / max(1, len(tokens)),
        "connectorCount": sum(normalized.count(mark) for mark in connectors),
        "structureMarkers": sum(structure),
        "evidenceMarkers": sum(
            normalized.count(mark) for mark in ("ejemplo", "resultado", "datos", "evidencia", "porque")
        ),
        "frequentTerms": [word for word, count in counts.most_common(20) if len(word) > 4 and count > 2],
    }


def rule_scores(indicators: dict, metrics) -> dict[str, float]:
    """Provisional teaching rubric v2: explicit bounded features, not a trained model."""
    n = max(1, metrics.wordCount)
    clarity = max(
        0,
        100 - max(0, indicators["meanSentenceWords"] - 25) * 2 - min(40, metrics.fillerWordsTotal / n * 200),
    )
    # Provisional practice range, not a universal or clinically validated norm.
    rate_distance = max(0, 100 - metrics.wordsPerMinute, metrics.wordsPerMinute - 180)
    rhythm = max(0, 100 - rate_distance)
    pause_ratio = (metrics.totalPauseDurationSeconds or 0) / max(1, metrics.recordingDurationSeconds)
    fluency = max(
        0, 100 - min(40, metrics.fillerWordsTotal / n * 200) - min(40, max(0, pause_ratio - 0.35) * 150)
    )
    return {
        "CLARITY": round(0.7 * clarity + 0.15 * rhythm + 0.15 * fluency, 1),
        "COHERENCE": min(100, 40 + indicators["connectorCount"] * 12),
        "ORGANIZATION": round(indicators["structureMarkers"] * 100 / 3, 1),
        "ARGUMENTATION": min(100, 30 + indicators["evidenceMarkers"] * 14),
    }

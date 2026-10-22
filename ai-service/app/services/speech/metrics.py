import re
import unicodedata
from dataclasses import dataclass

from app.schemas.speech import VerbalCommunicationMetricsSchema
from app.services.speech.provider import RawSegmentTimestamp, RawWordTimestamp

PAUSE_THRESHOLD_SECONDS = 0.60
HESITATION_GAP_SECONDS = 0.35

# Pure hesitation vocalizations in Spanish: always filler words when standalone tokens
PURE_HESITATION_TOKENS: dict[str, str] = {
    "eh": "eh",
    "ehh": "eh",
    "ehhh": "eh",
    "em": "em",
    "emm": "em",
    "mmm": "mmm",
    "mm": "mmm",
    "uh": "uh",
    "um": "um",
}

# Words where preceding "este" cannot be a grammatical masculine singular demonstrative determiner
NON_MASCULINE_NOUN_FOLLOWERS: set[str] = {
    "el", "la", "los", "las", "un", "una", "unos", "unas",
    "yo", "tu", "tú", "el", "él", "ella", "nosotros", "ustedes", "ellos", "ellas",
    "mi", "mis", "su", "sus", "nuestro", "nuestra",
    "de", "en", "con", "por", "para", "sin", "sobre", "hacia", "hasta", "desde",
    "que", "qué", "como", "cómo", "cuando", "cuándo", "donde", "dónde", "porque", "pero", "sino",
    "es", "era", "fue", "son", "estoy", "esta", "está", "estamos", "voy", "vamos", "hay", "tiene",
    "eh", "ehh", "em", "emm", "mmm", "bueno", "pues", "entonces", "digamos", "osea",
}

CONTEXTUAL_SINGLE_FILLERS: set[str] = {
    "este",
    "pues",
    "bueno",
    "entonces",
    "digamos",
    "basicamente",
    "literalmente",
}

CANONICAL_FILLER_LABELS: dict[str, str] = {
    "eh": "eh",
    "em": "em",
    "mmm": "mmm",
    "uh": "uh",
    "um": "um",
    "este": "este",
    "pues": "pues",
    "bueno": "bueno",
    "entonces": "entonces",
    "digamos": "digamos",
    "basicamente": "básicamente",
    "literalmente": "literalmente",
    "o sea": "o sea",
    "es decir": "es decir",
}


@dataclass(frozen=True)
class _TokenInfo:
    raw: str
    normalized: str
    stripped_Key: str
    has_trailing_pause_punct: bool
    has_leading_clause_boundary: bool
    start_seconds: float | None = None
    end_seconds: float | None = None


def normalize_transcript_text(text: str) -> str:
    if not text:
        return ""
    normalized = unicodedata.normalize("NFC", text)
    return re.sub(r"\s+", " ", normalized).strip()


def _strip_accents(value: str) -> str:
    decomposed = unicodedata.normalize("NFD", value)
    return "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")


def _clean_word_token(raw_token: str) -> str:
    cleaned = re.sub(r"^[^\wáéíóúüñÁÉÍÓÚÜÑ]+|[^\wáéíóúüñÁÉÍÓÚÜÑ]+$", "", raw_token, flags=re.UNICODE)
    return cleaned.lower()


def _tokenize_with_context(
    transcript_text: str,
    words: list[RawWordTimestamp],
) -> list[_TokenInfo]:
    raw_Whitespace_tokens = transcript_text.split() if transcript_text else []
    tokens: list[_TokenInfo] = []

    use_word_times = len(words) == len(raw_Whitespace_tokens) and len(words) > 0

    prev_ended_clause = True
    for idx, raw in enumerate(raw_Whitespace_tokens):
        normalized = _clean_word_token(raw)
        if not normalized:
            continue
        stripped_key = _strip_accents(normalized)
        has_trailing_punct = bool(re.search(r"[,;:.…!?]+$", raw))
        w_start = words[idx].start_seconds if use_word_times else None
        w_end = words[idx].end_seconds if use_word_times else None

        tokens.append(
            _TokenInfo(
                raw=raw,
                normalized=normalized,
                stripped_Key=stripped_key,
                has_trailing_pause_punct=has_trailing_punct,
                has_leading_clause_boundary=prev_ended_clause,
                start_seconds=w_start,
                end_seconds=w_end,
            )
        )
        prev_ended_clause = has_trailing_punct

    if not tokens and words:
        for idx, item in enumerate(words):
            normalized = _clean_word_token(item.word)
            if not normalized:
                continue
            stripped_key = _strip_accents(normalized)
            has_trailing_punct = bool(re.search(r"[,;:.…!?]+$", item.word))
            tokens.append(
                _TokenInfo(
                    raw=item.word,
                    normalized=normalized,
                    stripped_Key=stripped_key,
                    has_trailing_pause_punct=has_trailing_punct,
                    has_leading_clause_boundary=(idx == 0),
                    start_seconds=item.start_seconds,
                    end_seconds=item.end_seconds,
                )
            )

    return tokens


def _has_timestamp_gap_after(tokens: list[_TokenInfo], idx: int) -> bool:
    if idx + 1 >= len(tokens):
        return True
    current_end = tokens[idx].end_seconds
    next_start = tokens[idx + 1].start_seconds
    if current_end is not None and next_start is not None:
        return (next_start - current_end) >= HESITATION_GAP_SECONDS
    return False


def _has_timestamp_gap_before(tokens: list[_TokenInfo], idx: int) -> bool:
    if idx == 0:
        return True
    prev_end = tokens[idx - 1].end_seconds
    current_start = tokens[idx].start_seconds
    if prev_end is not None and current_start is not None:
        return (current_start - prev_end) >= HESITATION_GAP_SECONDS
    return False


def detect_contextual_spanish_fillers(
    transcript_text: str,
    words: list[RawWordTimestamp] | None = None,
) -> dict[str, int]:
    tokens = _tokenize_with_context(transcript_text, words or [])
    counts: dict[str, int] = {}
    n = len(tokens)
    i = 0

    def increment(key: str) -> None:
        label = CANONICAL_FILLER_LABELS.get(key, key)
        counts[label] = counts.get(label, 0) + 1

    while i < n:
        tok = tokens[i]
        key = tok.stripped_Key

        # 1. Pure hesitation sounds ("eh", "em", "mmm", "uh", "um")
        if key in PURE_HESITATION_TOKENS:
            increment(PURE_HESITATION_TOKENS[key])
            i += 1
            continue

        # 2. Multi-word phrase "o sea"
        if key == "o" and (i + 1) < n and tokens[i + 1].stripped_Key == "sea":
            second = tokens[i + 1]
            prev_key = tokens[i - 1].stripped_Key if i > 0 else ""
            next_key = tokens[i + 2].stripped_Key if (i + 2) < n else ""
            is_hesitation_context = (
                tok.has_leading_clause_boundary
                or second.has_trailing_pause_punct
                or _has_timestamp_gap_before(tokens, i)
                or _has_timestamp_gap_after(tokens, i + 1)
                or prev_key in PURE_HESITATION_TOKENS
                or prev_key in CONTEXTUAL_SINGLE_FILLERS
                or next_key in PURE_HESITATION_TOKENS
                or next_key in CONTEXTUAL_SINGLE_FILLERS
                or (i + 2 >= n)
            )
            if is_hesitation_context:
                increment("o sea")
                i += 2
                continue

        # 3. Multi-word phrase "es decir" (only when isolated by hesitation/pause or repeated)
        if key == "es" and (i + 1) < n and tokens[i + 1].stripped_Key == "decir":
            second = tokens[i + 1]
            prev_key = tokens[i - 1].stripped_Key if i > 0 else ""
            if (
                prev_key in PURE_HESITATION_TOKENS
                or prev_key in CONTEXTUAL_SINGLE_FILLERS
                or (_has_timestamp_gap_before(tokens, i) and _has_timestamp_gap_after(tokens, i + 1))
            ):
                increment("es decir")
                i += 2
                continue

        # 4. Contextual evaluation for "este"
        if key == "este":
            next_key = tokens[i + 1].stripped_Key if (i + 1) < n else ""
            prev_key = tokens[i - 1].stripped_Key if i > 0 else ""
            is_filler_este = (
                (i + 1 == n)  # trailing "este" at end of speech
                or tok.has_trailing_pause_punct  # "este," / "este..."
                or _has_timestamp_gap_after(tokens, i)  # followed by an audible pause
                or next_key == "este"  # repeated "este este"
                or prev_key in PURE_HESITATION_TOKENS
                or next_key in NON_MASCULINE_NOUN_FOLLOWERS  # ungrammatical as demonstrative
            )
            if is_filler_este:
                increment("este")
            i += 1
            continue

        # 5. Contextual evaluation for "pues", "bueno", "entonces", "digamos", "básicamente", "literalmente"
        if key in CONTEXTUAL_SINGLE_FILLERS:
            next_key = tokens[i + 1].stripped_Key if (i + 1) < n else ""
            prev_key = tokens[i - 1].stripped_Key if i > 0 else ""
            repeated_nearby = any(
                tokens[j].stripped_Key == key
                for j in range(max(0, i - 5), i)
            )
            is_isolated_hesitation = (
                tok.has_trailing_pause_punct
                or (tok.has_leading_clause_boundary and _has_timestamp_gap_after(tokens, i))
                or (_has_timestamp_gap_before(tokens, i) and _has_timestamp_gap_after(tokens, i))
                or next_key == key
                or prev_key in PURE_HESITATION_TOKENS
                or next_key in PURE_HESITATION_TOKENS
                or (i + 1 == n)
                or repeated_nearby
            )
            if is_isolated_hesitation:
                increment(key)
            i += 1
            continue

        i += 1

    return counts


def _compute_timing_and_pauses(
    recording_duration_seconds: float,
    words: list[RawWordTimestamp],
    segments: list[RawSegmentTimestamp],
) -> tuple[bool, float | None, int | None, float | None, float | None]:
    intervals: list[tuple[float, float]] = []
    if len(words) >= 2:
        intervals = [
            (max(0.0, w.start_seconds), max(w.start_seconds, w.end_seconds))
            for w in words
        ]
    elif len(segments) >= 1:
        intervals = [
            (max(0.0, s.start_seconds), max(s.start_seconds, s.end_seconds))
            for s in segments
        ]
    elif len(words) == 1:
        w = words[0]
        intervals = [(max(0.0, w.start_seconds), max(w.start_seconds, w.end_seconds))]

    if not intervals:
        return False, None, None, None, None

    sorted_intervals = sorted(intervals, key=lambda pair: pair[0])

    # Calculate pauses between consecutive intervals
    pause_durations: list[float] = []
    current_max_end = sorted_intervals[0][1]
    for start, end in sorted_intervals[1:]:
        gap = start - current_max_end
        if gap >= PAUSE_THRESHOLD_SECONDS:
            pause_durations.append(gap)
        if end > current_max_end:
            current_max_end = end

    # Merge overlapping intervals to estimate spoken time accurately
    merged: list[tuple[float, float]] = [sorted_intervals[0]]
    for start, end in sorted_intervals[1:]:
        last_start, last_end = merged[-1]
        if start <= last_end + 0.15:
            merged[-1] = (last_start, max(last_end, end))
        else:
            merged.append((start, end))

    raw_spoken_seconds = sum(max(0.0, end - start) for start, end in merged)
    effective_recording = max(recording_duration_seconds, current_max_end)
    spoken_seconds = round(min(raw_spoken_seconds, effective_recording), 2)

    pause_count = len(pause_durations)
    total_pause = round(sum(pause_durations), 2)
    longest_pause = round(max(pause_durations), 2) if pause_durations else 0.0

    return True, spoken_seconds, pause_count, total_pause, longest_pause


def calculate_verbal_metrics(
    transcript_text: str,
    recording_duration_seconds: float,
    words: list[RawWordTimestamp] | None = None,
    segments: list[RawSegmentTimestamp] | None = None,
) -> VerbalCommunicationMetricsSchema:
    safe_words = words or []
    safe_segments = segments or []
    normalized_text = normalize_transcript_text(transcript_text)
    tokens = _tokenize_with_context(normalized_text, safe_words)
    word_count = len(tokens)

    safe_recording_duration = round(max(0.0, float(recording_duration_seconds or 0.0)), 2)

    if safe_recording_duration > 0.0:
        wpm = round((word_count / safe_recording_duration) * 60.0, 2)
    else:
        wpm = 0.0

    filler_counts = detect_contextual_spanish_fillers(normalized_text, safe_words)
    filler_total = sum(filler_counts.values())

    (
        timestamps_available,
        spoken_seconds,
        pause_count,
        total_pause_seconds,
        longest_pause_seconds,
    ) = _compute_timing_and_pauses(safe_recording_duration, safe_words, safe_segments)

    spoken_ratio: float | None = None
    spoken_wpm: float | None = None
    if timestamps_available and spoken_seconds is not None:
        if safe_recording_duration > 0.0:
            spoken_ratio = round(min(1.0, max(0.0, spoken_seconds / safe_recording_duration)), 4)
        if spoken_seconds >= 1.0:
            spoken_wpm = round((word_count / spoken_seconds) * 60.0, 2)

    return VerbalCommunicationMetricsSchema(
        wordCount=word_count,
        recordingDurationSeconds=safe_recording_duration,
        estimatedSpokenDurationSeconds=spoken_seconds,
        spokenTimeRatio=spoken_ratio,
        wordsPerMinute=wpm,
        spokenWordsPerMinute=spoken_wpm,
        fillerWordsTotal=filler_total,
        fillerWordCounts=filler_counts,
        estimatedPauseCount=pause_count,
        totalPauseDurationSeconds=total_pause_seconds,
        longestPauseSeconds=longest_pause_seconds,
        timestampsAvailable=timestamps_available,
    )

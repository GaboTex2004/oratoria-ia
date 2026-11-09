"""Relative recording metrics; never calibrated SPL or clinical voice measurements."""

import math

import numpy as np


def analyze_samples(audio: np.ndarray, sample_rate: int) -> dict[str, float | None]:
    samples = np.asarray(audio, dtype=np.float64)
    if samples.size == 0 or not np.isfinite(samples).all():
        raise ValueError("finite nonempty audio required")
    rms = float(np.sqrt(np.mean(samples**2)))
    pitches = []
    frame_length = int(sample_rate * 0.04)
    for start in range(0, len(samples) - frame_length + 1, sample_rate // 50):
        frame = samples[start : start + frame_length]
        if np.sqrt(np.mean(frame**2)) < max(0.01, rms * 0.4):
            continue
        frame = (frame - frame.mean()) * np.hanning(frame_length)
        correlation = np.correlate(frame, frame, mode="full")[frame_length - 1 :]
        low, high = sample_rate // 400, sample_rate // 80
        lag = low + int(np.argmax(correlation[low : high + 1]))
        if correlation[0] > 0 and correlation[lag] / correlation[0] > 0.65:
            pitches.append(sample_rate / lag)
    return {
        "rmsAmplitude": round(rms, 6),
        "relativeLevelDbfs": round(20 * math.log10(rms), 2) if rms > 0 else None,
        "clippingRatio": round(float(np.mean(np.abs(samples) >= 0.99)), 6),
        "pitchMedianHz": round(float(np.median(pitches)), 2) if len(pitches) >= 5 else None,
        "pitchStdHz": round(float(np.std(pitches)), 2) if len(pitches) >= 5 else None,
        "pitchFrames": float(len(pitches)),
    }

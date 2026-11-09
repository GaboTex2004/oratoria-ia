"""Local faster-whisper adapter with explicit weights and decoded duration limits."""

import io
import threading
from pathlib import Path

from app.core.config import settings
from app.services.speech.provider import (
    RawSegmentTimestamp,
    RawTranscriptionResult,
    RawWordTimestamp,
    SpeechProviderError,
    SpeechToTextProvider,
)


class LocalWhisperProvider(SpeechToTextProvider):
    def __init__(self):
        self._model = None
        self._slot = threading.BoundedSemaphore(1)

    def transcribe(
        self, audio_bytes: bytes, filename: str, mime_type: str, language: str = "es"
    ) -> RawTranscriptionResult:
        if not self._slot.acquire(blocking=False):
            raise SpeechProviderError(
                "LOCAL_STT_BUSY", "La transcripción local está ocupada.", 429, True, "local-whisper"
            )
        try:
            model_path = Path(settings.whisper_model_path)
            if not (model_path / "model.bin").is_file():
                raise SpeechProviderError(
                    "LOCAL_STT_MODEL_MISSING",
                    "Descarga primero el modelo Whisper local.",
                    503,
                    False,
                    "local-whisper",
                )
            import av
            import numpy as np
            from faster_whisper import WhisperModel

            frames, count = [], 0
            with av.open(io.BytesIO(audio_bytes)) as container:
                resampler = av.AudioResampler(format="s16", layout="mono", rate=16000)
                for frame in container.decode(audio=0):
                    for converted in resampler.resample(frame):
                        count += converted.samples
                        if count > settings.max_audio_duration_seconds * 16000:
                            raise SpeechProviderError(
                                "AUDIO_DURATION_EXCEEDED",
                                "La duración real supera el límite permitido.",
                                400,
                                False,
                                "local-whisper",
                            )
                        frames.append(converted.to_ndarray().reshape(-1))
                for converted in resampler.resample(None):
                    count += converted.samples
                    frames.append(converted.to_ndarray().reshape(-1))
            if count > settings.max_audio_duration_seconds * 16000:
                raise SpeechProviderError(
                    "AUDIO_DURATION_EXCEEDED",
                    "La duración real supera el límite permitido.",
                    400,
                    False,
                    "local-whisper",
                )
            if not count:
                raise ValueError("no audio samples")
            audio = np.concatenate(frames).astype(np.float32) / 32768.0
            if self._model is None:
                self._model = WhisperModel(
                    str(model_path),
                    device=settings.whisper_device,
                    compute_type=settings.whisper_compute_type,
                    cpu_threads=settings.inference_threads,
                    local_files_only=True,
                )
            segments, info = self._model.transcribe(
                audio,
                language=language,
                beam_size=3,
                word_timestamps=True,
                vad_filter=True,
                condition_on_previous_text=False,
            )
            raw_segments, words = [], []
            for index, segment in enumerate(segments):
                raw_segments.append(
                    RawSegmentTimestamp(index, segment.start, segment.end, segment.text.strip())
                )
                words.extend(
                    RawWordTimestamp(word.word.strip(), word.start, word.end)
                    for word in (segment.words or [])
                )
            from app.services.voice.acoustics import analyze_samples

            return RawTranscriptionResult(
                provider="faster-whisper",
                model=model_path.name,
                language=info.language,
                text=" ".join(segment.text for segment in raw_segments),
                duration_seconds=len(audio) / 16000,
                segments=raw_segments,
                words=words,
                acoustic_metrics=analyze_samples(audio, 16000),
            )
        except SpeechProviderError:
            raise
        except (ImportError, OSError, RuntimeError) as exc:
            raise SpeechProviderError(
                "LOCAL_STT_UNAVAILABLE",
                "No se pudo cargar el motor local de voz.",
                503,
                False,
                "local-whisper",
            ) from exc
        except Exception as exc:
            raise SpeechProviderError(
                "INVALID_AUDIO_CONTENT",
                "No se pudo decodificar el contenido del audio.",
                400,
                False,
                "local-whisper",
            ) from exc
        finally:
            self._slot.release()

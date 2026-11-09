"""Observable 2D posture indicators from local MediaPipe, without identity/emotions."""

import base64
import io
import math
import threading
from pathlib import Path

import numpy as np

from app.core.config import settings
from app.infrastructure.ollama import LocalModelError

_slot = threading.BoundedSemaphore(1)


def analyze_frames(frames: list[str]) -> dict:
    if not _slot.acquire(blocking=False):
        raise LocalModelError("POSE_BUSY", "El análisis corporal está ocupado.", 429)
    try:
        if not Path(settings.pose_model_path).is_file():
            raise LocalModelError("POSE_MODEL_MISSING", "Descarga primero el modelo corporal local.")
        import mediapipe as mp
        from PIL import Image, UnidentifiedImageError
        from mediapipe.tasks import python
        from mediapipe.tasks.python import vision

        options = vision.PoseLandmarkerOptions(
            base_options=python.BaseOptions(model_asset_path=settings.pose_model_path),
            running_mode=vision.RunningMode.IMAGE,
            num_poses=1,
            min_pose_detection_confidence=0.5,
            min_pose_presence_confidence=0.5,
        )
        shoulder_angles, head_angles, centers, wrist_positions = [], [], [], []
        detected = 0
        with vision.PoseLandmarker.create_from_options(options) as detector:
            for encoded in frames:
                try:
                    decoded = base64.b64decode(encoded, validate=True)
                    with Image.open(io.BytesIO(decoded)) as image:
                        if image.height > 1080 or image.width > 1920 or image.format not in ("JPEG", "PNG"):
                            raise ValueError("image dimensions or format")
                        rgb = np.asarray(image.convert("RGB"))
                except (ValueError, TypeError, OSError, UnidentifiedImageError, Image.DecompressionBombError):
                    raise LocalModelError(
                        "INVALID_POSE_FRAME", "Fotograma inválido o demasiado grande.", 400
                    ) from None
                height, width = rgb.shape[:2]
                result = detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
                if not result.pose_landmarks:
                    continue
                points = result.pose_landmarks[0]
                if min(points[i].visibility for i in (0, 11, 12)) < 0.6:
                    continue
                detected += 1
                left, right = points[11], points[12]
                dx, dy = (right.x - left.x) * width, (right.y - left.y) * height
                angle = math.degrees(math.atan2(abs(dy), abs(dx)))
                shoulder_angles.append(angle)
                center = ((left.x + right.x) / 2, (left.y + right.y) / 2)
                centers.append(center)
                nose = points[0]
                head_angles.append(
                    math.degrees(
                        math.atan2(abs(nose.x - center[0]) * width, max(1, abs(nose.y - center[1]) * height))
                    )
                )
                visible_wrists = [points[i] for i in (15, 16) if points[i].visibility >= 0.6]
                wrist_positions.append([(point.x, point.y) for point in visible_wrists])
        movement = [math.dist(a, b) for a, b in zip(centers, centers[1:])]
        wrist_movement = [
            math.dist(a, b)
            for prev, curr in zip(wrist_positions, wrist_positions[1:])
            if len(prev) == len(curr) == 2
            for a, b in zip(prev, curr)
        ]
        return {
            "schemaVersion": "v1",
            "provider": "mediapipe-local",
            "framesAnalyzed": len(frames),
            "framesWithPerson": detected,
            "presenceRatio": round(detected / len(frames), 3),
            "shoulderTiltDegrees": round(float(np.mean(shoulder_angles)), 2) if detected else None,
            "headOffsetDegrees": round(float(np.mean(head_angles)), 2) if detected else None,
            "bodyMovementNormalized": round(float(np.mean(movement)), 4) if movement else None,
            "wristMovementNormalized": round(float(np.mean(wrist_movement)), 4) if wrist_movement else None,
            "limitations": [
                "Indicadores 2D orientativos; dependen del encuadre y de la cámara.",
                "No se mide identidad, emoción ni contacto visual exacto.",
                "Los fotogramas se procesan en memoria y no se conservan.",
            ],
        }
    finally:
        _slot.release()

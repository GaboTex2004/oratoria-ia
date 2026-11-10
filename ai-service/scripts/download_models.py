"""Explicit one-time download; never imported during server inference."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import urllib.request

from huggingface_hub import HfApi, snapshot_download

ROOT = Path(__file__).resolve().parents[1]
WHISPER_REQUIRED = {
    "model.bin": 10_000_000,
    "config.json": 100,
    "tokenizer.json": 1_000,
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def valid_file(path: Path, minimum_size: int, expected_sha256: str | None = None) -> bool:
    return (
        path.is_file()
        and path.stat().st_size >= minimum_size
        and (expected_sha256 is None or sha256(path) == expected_sha256)
    )


def load_manifest(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return {}


def whisper_is_valid(target: Path, manifest: dict) -> bool:
    recorded = manifest.get("whisperFiles", {})
    return all(
        valid_file(target / name, minimum, recorded.get(name, {}).get("sha256"))
        for name, minimum in WHISPER_REQUIRED.items()
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--whisper", choices=["tiny", "base", "small"], default="base")
    parser.add_argument("--pose", action="store_true")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    repo = f"Systran/faster-whisper-{args.whisper}"
    model_root = Path(os.getenv("MODEL_ROOT", str(ROOT / "models"))).resolve()
    model_root.mkdir(parents=True, exist_ok=True)
    target = model_root / f"whisper-{args.whisper}"
    manifest_path = model_root / "download-manifest.json"
    provenance = load_manifest(manifest_path)
    whisper_valid = whisper_is_valid(target, provenance)
    if not whisper_valid and not args.verify_only:
        revision = HfApi().model_info(repo).sha
        snapshot_download(
            repo,
            revision=revision,
            local_dir=target,
            allow_patterns=[
                "model.bin",
                "config.json",
                "tokenizer.json",
                "vocabulary.*",
                "preprocessor_config.json",
            ],
        )
        provenance.update(
            {
                "repository": repo,
                "revision": revision,
                "source": f"https://huggingface.co/{repo}",
                "license": "MIT (Whisper); verify the source model card",
            }
        )
        provenance["whisperFiles"] = {
            name: {"size": (target / name).stat().st_size, "sha256": sha256(target / name)}
            for name in WHISPER_REQUIRED
        }
        whisper_valid = whisper_is_valid(target, provenance)
    if not whisper_valid:
        raise SystemExit("Whisper model is missing or failed integrity verification")
    if args.pose:
        url = (
            "https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
            "pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
        )
        pose_path = model_root / "pose_landmarker_lite.task"
        pose_record = provenance.get("pose", {})
        pose_valid = valid_file(pose_path, 1_000_000, pose_record.get("sha256"))
        if not pose_valid and not args.verify_only:
            temporary_path = pose_path.with_suffix(".task.download")
            urllib.request.urlretrieve(url, temporary_path)
            if not valid_file(temporary_path, 1_000_000):
                temporary_path.unlink(missing_ok=True)
                raise SystemExit("MediaPipe pose model download failed integrity verification")
            temporary_path.replace(pose_path)
            provenance["pose"] = {
                "url": url,
                "size": pose_path.stat().st_size,
                "sha256": sha256(pose_path),
            }
            pose_valid = True
        if not pose_valid:
            raise SystemExit("MediaPipe pose model is missing or failed integrity verification")
    if not args.verify_only:
        manifest_path.write_text(json.dumps(provenance, indent=2), encoding="utf-8")
    print("Local model files verified")


if __name__ == "__main__":
    main()

"""Explicit one-time download; never imported during server inference."""

import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

from huggingface_hub import HfApi, snapshot_download

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--whisper", choices=["tiny", "base", "small"], default="base")
    parser.add_argument("--pose", action="store_true")
    args = parser.parse_args()
    repo = f"Systran/faster-whisper-{args.whisper}"
    model_root = ROOT / "models"
    target = model_root / f"whisper-{args.whisper}"
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
    provenance = {
        "repository": repo,
        "revision": revision,
        "source": f"https://huggingface.co/{repo}",
        "license": "MIT (Whisper); verify the source model card",
    }
    if args.pose:
        url = (
            "https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
            "pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
        )
        pose_path = model_root / "pose_landmarker_lite.task"
        urllib.request.urlretrieve(url, pose_path)
        provenance["pose"] = {"url": url, "sha256": hashlib.sha256(pose_path.read_bytes()).hexdigest()}
    (model_root / "download-manifest.json").write_text(json.dumps(provenance, indent=2), encoding="utf-8")
    print(json.dumps(provenance, indent=2))


if __name__ == "__main__":
    main()

"""Validate voluntary human annotations and split by speaker, without creating labels."""

import argparse
import csv
import hashlib
import json
import math
from datetime import date
from pathlib import Path

PRIVATE_ROOT = Path(__file__).resolve().parents[1] / "datasets" / "private"
LABELS = ("clarity", "coherence", "organization", "argumentation")


def speaker_split(speaker_id: str) -> str:
    bucket = int(hashlib.sha256(speaker_id.encode()).hexdigest()[:8], 16) % 10
    return "test" if bucket == 0 else "validation" if bucket == 1 else "train"


def validate_rows(rows: list[dict], private_root: Path) -> dict[str, list]:
    result = {"train": [], "validation": [], "test": []}
    seen = set()
    for row in rows:
        if not row.get("sample_id") or row["sample_id"] in seen:
            raise ValueError("sample_id ausente o duplicado")
        seen.add(row["sample_id"])
        if row.get("consent") != "yes" or not row.get("consent_version"):
            raise ValueError("Se requiere consentimiento documentado, no inferido")
        date.fromisoformat(row["consent_date"])
        if not row.get("speaker_id") or not row.get("training_type"):
            raise ValueError("speaker_id y training_type obligatorios")
        path = (private_root / row["audio_path"]).resolve()
        if not path.is_relative_to(private_root.resolve()) or not path.is_file():
            raise ValueError("Audio ausente o fuera del almacenamiento privado")
        for label in LABELS:
            value = float(row[label])
            if not math.isfinite(value) or not 0 <= value <= 100:
                raise ValueError("Las etiquetas humanas deben estar entre 0 y 100")
        result[speaker_split(row["speaker_id"])].append(row)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_path", type=Path)
    args = parser.parse_args()
    with args.csv_path.open(encoding="utf-8-sig", newline="") as source:
        rows = list(csv.DictReader(source))
    if not rows:
        raise SystemExit("No hay anotaciones: no se generan datos ni etiquetas ficticias.")
    splits = validate_rows(rows, PRIVATE_ROOT)
    PRIVATE_ROOT.mkdir(parents=True, exist_ok=True)
    for name, samples in splits.items():
        (PRIVATE_ROOT / f"{name}.json").write_text(
            json.dumps(samples, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    print(json.dumps({name: len(samples) for name, samples in splits.items()}))
    if any(not samples for samples in splits.values()):
        print("Datos insuficientes para entrenar y evaluar: al menos una partición está vacía.")


if __name__ == "__main__":
    main()

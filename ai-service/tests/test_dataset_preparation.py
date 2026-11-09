"""Synthetic metadata only: consent validation and speaker-level leakage prevention."""

import importlib.util
from pathlib import Path

import pytest

spec = importlib.util.spec_from_file_location(
    "prepare_dataset", Path(__file__).resolve().parents[1] / "scripts" / "prepare_dataset.py"
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def row(consent="yes"):
    return {
        "sample_id": "synthetic-metadata-only",
        "speaker_id": "speaker-test",
        "audio_path": "synthetic.wav",
        "consent": consent,
        "consent_version": "test-v1",
        "consent_date": "2026-11-08",
        "training_type": "PRESENTATION",
        "clarity": "70",
        "coherence": "70",
        "organization": "70",
        "argumentation": "70",
    }


def test_recordings_by_the_same_speaker_never_cross_partitions(tmp_path):
    (tmp_path / "synthetic.wav").write_bytes(b"synthetic test fixture, not human audio")
    first = row()
    second = {**first, "sample_id": "synthetic-metadata-2"}
    partitions = module.validate_rows([first, second], tmp_path)
    assert sorted(len(values) for values in partitions.values()) == [0, 0, 2]


def test_missing_consent_prevents_dataset_use(tmp_path):
    with pytest.raises(ValueError, match="consentimiento"):
        module.validate_rows([row("no")], tmp_path)


def test_private_audio_path_cannot_escape_dataset_root(tmp_path):
    with pytest.raises(ValueError, match="privado"):
        module.validate_rows([{**row(), "audio_path": "../outside.wav"}], tmp_path)

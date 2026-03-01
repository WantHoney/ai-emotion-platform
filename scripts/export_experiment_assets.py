#!/usr/bin/env python3
"""Export thesis figure source data from training artifacts."""

from __future__ import annotations

import csv
import json
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
TRAINING_ROOT = REPO_ROOT / "backend" / "ser-service" / "training" / "fusion"
FIGURES_DIR = REPO_ROOT / "docs" / "figures"


def read_json(path: Path):
    with path.open("r", encoding="utf-8-sig") as f:
        return json.load(f)


def write_csv(path: Path, rows: list[dict], fieldnames: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def export_ablation() -> None:
    payload = read_json(TRAINING_ROOT / "ablation_exp01" / "ablation_summary.json")
    rows = sorted(payload["rows"], key=lambda x: x["mode"])
    write_csv(
        FIGURES_DIR / "ablation_metrics.csv",
        rows,
        [
            "mode",
            "best_epoch",
            "temperature",
            "val_macro_f1_cal",
            "val_accuracy_cal",
            "test_macro_f1_cal",
            "test_accuracy_cal",
            "test_ece_cal",
            "test_macro_f1_zh",
            "test_macro_f1_en",
            "calibration_mode",
        ],
    )


def export_selection() -> None:
    payload = read_json(TRAINING_ROOT / "models" / "model_selection_exp01.json")
    rows = sorted(payload, key=lambda x: x["test_macro_f1"], reverse=True)
    write_csv(
        FIGURES_DIR / "model_selection.csv",
        rows,
        [
            "run",
            "best_epoch",
            "temperature",
            "val_macro_f1",
            "test_macro_f1",
            "test_accuracy",
            "test_ece",
        ],
    )


def export_calibration() -> None:
    payload = read_json(TRAINING_ROOT / "models" / "fusion_exp01" / "train_report.json")
    rows = []
    for split in ("val", "test"):
        unc = payload[f"{split}_metrics_uncalibrated"]
        cal = payload[f"{split}_metrics_calibrated"]
        for metric in ("nll", "brier", "ece"):
            rows.append(
                {
                    "split": split,
                    "metric": metric,
                    "uncalibrated": unc[metric],
                    "calibrated": cal[metric],
                    "delta": cal[metric] - unc[metric],
                }
            )
    write_csv(
        FIGURES_DIR / "calibration_metrics.csv",
        rows,
        ["split", "metric", "uncalibrated", "calibrated", "delta"],
    )


def export_dataset_composition() -> None:
    payload = read_json(TRAINING_ROOT / "features_exp01_full" / "summary.json")
    rows = []
    for split in ("train", "val", "test"):
        block = payload[split]
        total = int(block["count"])
        zh = int(block["language_count"].get("zh", 0))
        en = int(block["language_count"].get("en", 0))
        rows.append(
            {
                "split": split,
                "total": total,
                "en": en,
                "zh": zh,
                "zh_ratio": zh / total if total > 0 else 0.0,
                "asr_missing_transcript": int(block.get("asr_missing_transcript", 0)),
                "asr_failed": int(block.get("asr_failed", 0)),
            }
        )
    write_csv(
        FIGURES_DIR / "dataset_composition.csv",
        rows,
        [
            "split",
            "total",
            "en",
            "zh",
            "zh_ratio",
            "asr_missing_transcript",
            "asr_failed",
        ],
    )


def main() -> int:
    export_ablation()
    export_selection()
    export_calibration()
    export_dataset_composition()
    print(f"Exported figure sources to {FIGURES_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

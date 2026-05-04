from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from thesis_v4_common import (  # noqa: E402
    CONTROLLED_POOL_FREEZE_PATH,
    CONTROLLED_POOL_ROOT,
    LOCAL_TEXT_ROOT,
    REAL_TEST_FREEZE_PATH,
    REAL_WORLD_POOL_FREEZE_PATH,
    REAL_POOL_ROOT,
    REVIEW_MANIFEST_PATH,
    TEACHER_ROOT,
    TEXT_BASE_MODEL,
    SERVICE_ROOT,
    ensure_dirs,
    now_iso,
    read_json,
    read_jsonl,
    read_samples_csv,
    write_csv,
    write_json,
)


EMOTION_LABELS = ("ANG", "HAP", "NEU", "SAD")
VENV_PYTHON = (SERVICE_ROOT / "venv" / "Scripts" / "python.exe").resolve()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prepare thesis v4 frozen train/val/test splits for the local text model.")
    parser.add_argument("--teacher-version-id", required=True)
    parser.add_argument("--val-ratio", type=float, default=0.20)
    parser.add_argument("--audio-model-version", default="ser_multilingual_xlsr_stageB_exp04_hardfix_v1")
    parser.add_argument("--text-model-version", default="v4_teacher_local_candidate")
    parser.add_argument("--decision-version", default="decision_v4")
    return parser.parse_args()


def stable_bucket(sample_id: str) -> int:
    return sum(ord(ch) for ch in sample_id) % 100


def split_train_val(rows: list[dict[str, Any]], val_ratio: float) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    grouped: dict[str, list[dict[str, Any]]] = {}
    for row in rows:
        grouped.setdefault(str(row.get("label") or ""), []).append(row)

    train_rows: list[dict[str, Any]] = []
    val_rows: list[dict[str, Any]] = []
    for label, bucket in grouped.items():
        ordered = sorted(bucket, key=lambda item: (stable_bucket(str(item.get("sample_id") or "")), str(item.get("sample_id") or "")))
        if len(ordered) <= 1:
            val_target = 0
        else:
            val_target = max(1, int(round(len(ordered) * val_ratio)))
            val_target = min(val_target, len(ordered) - 1)
        val_ids = {row["sample_id"] for row in ordered[:val_target]}
        for row in ordered:
            if row["sample_id"] in val_ids:
                row["split"] = "val"
                val_rows.append(row)
            else:
                row["split"] = "train"
                train_rows.append(row)
    train_rows.sort(key=lambda item: (item["label"], item["sample_id"]))
    val_rows.sort(key=lambda item: (item["label"], item["sample_id"]))
    return train_rows, val_rows


def require_frozen_payload(path: Path, expected_key: str = "frozen") -> dict[str, Any]:
    payload = read_json(path, default=None)
    if not isinstance(payload, dict):
        raise ValueError(f"missing freeze payload: {path}")
    if not payload.get(expected_key):
        raise ValueError(f"freeze payload is not frozen: {path}")
    return payload


def build_teacher_map(version_id: str) -> dict[str, dict[str, Any]]:
    rows = read_jsonl(TEACHER_ROOT / f"{version_id}.jsonl")
    if not rows:
        raise ValueError(f"teacher artifact not found or empty: {version_id}")
    return {str(row.get("sample_id") or ""): row for row in rows if row.get("sample_id")}


def one_hot_scores4(label: str | None) -> dict[str, float]:
    normalized = str(label or "").strip().upper()
    return {emotion: 1.0 if emotion == normalized else 0.0 for emotion in EMOTION_LABELS}


def resolve_real_test_labels(real_test_freeze: dict[str, Any]) -> dict[str, str]:
    label_map: dict[str, str] = {}
    for row in real_test_freeze.get("samples", []):
        sample_id = str(row.get("sampleId") or "").strip()
        label = str(row.get("humanLabel") or "").strip().upper()
        if sample_id and label:
            label_map[sample_id] = label
    if not label_map:
        raise ValueError("real_test freeze does not contain any labeled samples")
    return label_map


def build_dataset_rows(
    *,
    samples: list[Any],
    teacher_rows: dict[str, dict[str, Any]],
    teacher_version_id: str,
    split: str,
    label_overrides: dict[str, str] | None = None,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    label_overrides = label_overrides or {}
    for sample in samples:
        teacher = teacher_rows.get(sample.sample_id)
        if teacher is None:
            raise ValueError(f"teacher label missing for sample: {sample.sample_id}")
        label = label_overrides.get(sample.sample_id) or sample.human_label or teacher.get("label4")
        teacher_label4 = str(teacher.get("label4") or "").strip().upper()
        teacher_scores4 = teacher.get("scores4") or {}
        teacher_confidence = float(teacher.get("teacher_confidence") or 0.0)
        teacher_scores4_source = "teacher_label"
        if sample.source_kind == "intake_recording" and sample.human_label:
            teacher_label4 = str(sample.human_label).strip().upper()
            teacher_scores4 = one_hot_scores4(sample.human_label)
            teacher_confidence = 1.0
            teacher_scores4_source = "human_label_override"
        label_source = (
            "real_test_human_label"
            if sample.sample_id in label_overrides
            else "human_label"
            if sample.human_label
            else "teacher_label"
        )
        rows.append(
            {
                "sample_id": sample.sample_id,
                "path": sample.path,
                "language": sample.language or "zh",
                "transcript": sample.transcript,
                "label": label,
                "label3": teacher.get("label3"),
                "teacher_label4": teacher_label4,
                "teacher_confidence": teacher_confidence,
                "teacher_conflict_hint": teacher.get("conflict_hint") or "",
                "teacher_scores3_json": json.dumps(teacher.get("scores3") or {}, ensure_ascii=False, sort_keys=True),
                "teacher_scores4_json": json.dumps(teacher_scores4, ensure_ascii=False, sort_keys=True),
                "teacher_scores4_source": teacher_scores4_source,
                "source": sample.source,
                "source_kind": sample.source_kind,
                "speaker": sample.speaker,
                "is_key_hard_case": sample.is_key_hard_case,
                "is_real_world": sample.source == "real_world",
                "label_source": label_source,
                "human_label": sample.human_label or label_overrides.get(sample.sample_id) or "",
                "teacher_version_id": teacher_version_id,
                "split": split,
                "metadata_json": json.dumps(sample.metadata, ensure_ascii=False),
            }
        )
    return rows


def main() -> None:
    args = parse_args()
    ensure_dirs()

    real_world_freeze = require_frozen_payload(REAL_WORLD_POOL_FREEZE_PATH)
    real_test_freeze = require_frozen_payload(REAL_TEST_FREEZE_PATH)
    controlled_freeze = require_frozen_payload(CONTROLLED_POOL_FREEZE_PATH)

    for payload_path, payload in (
        (REAL_WORLD_POOL_FREEZE_PATH, real_world_freeze),
        (REAL_TEST_FREEZE_PATH, real_test_freeze),
        (CONTROLLED_POOL_FREEZE_PATH, controlled_freeze),
    ):
        teacher_version = str(payload.get("teacherVersionId") or "").strip()
        if teacher_version and teacher_version != args.teacher_version_id:
            raise ValueError(
                f"freeze payload teacherVersionId mismatch for {payload_path.name}: {teacher_version!r} != {args.teacher_version_id!r}"
            )

    teacher_rows = build_teacher_map(args.teacher_version_id)
    review_ids = {sample.sample_id for sample in read_samples_csv(REVIEW_MANIFEST_PATH)}

    real_pool = {sample.sample_id: sample for sample in read_samples_csv(REAL_POOL_ROOT / "real_world_pool.csv")}
    controlled_pool = read_samples_csv(CONTROLLED_POOL_ROOT / "controlled_zh_pool.csv")
    if not controlled_pool:
        raise ValueError("controlled_zh_pool.csv is missing or empty; freeze stage must run first")

    real_test_labels = resolve_real_test_labels(real_test_freeze)
    real_test_ids = set(real_test_labels)
    real_test_samples = []
    for sample_id in real_test_ids:
        sample = real_pool.get(sample_id)
        if sample is None:
            raise ValueError(f"real_test sample missing from real_world_pool.csv: {sample_id}")
        if sample_id in review_ids:
            raise ValueError(f"teacher_review_set sample leaked into real_test: {sample_id}")
        real_test_samples.append(sample)

    real_dev_ids = set(real_world_freeze.get("realDevSampleIds") or [])
    real_dev_samples = []
    for sample_id in sorted(real_dev_ids):
        sample = real_pool.get(sample_id)
        if sample is None:
            raise ValueError(f"real_dev sample missing from real_world_pool.csv: {sample_id}")
        if sample_id in review_ids:
            raise ValueError(f"teacher_review_set sample leaked into real_dev: {sample_id}")
        real_dev_samples.append(sample)

    for sample in controlled_pool:
        if sample.sample_id in review_ids:
            raise ValueError(f"teacher_review_set sample leaked into controlled_zh_pool: {sample.sample_id}")

    test_rows = build_dataset_rows(
        samples=sorted(real_test_samples, key=lambda item: item.sample_id),
        teacher_rows=teacher_rows,
        teacher_version_id=args.teacher_version_id,
        split="test",
        label_overrides=real_test_labels,
    )
    train_val_pool = build_dataset_rows(
        samples=sorted(real_dev_samples, key=lambda item: item.sample_id),
        teacher_rows=teacher_rows,
        teacher_version_id=args.teacher_version_id,
        split="train_val",
    ) + build_dataset_rows(
        samples=sorted(controlled_pool, key=lambda item: item.sample_id),
        teacher_rows=teacher_rows,
        teacher_version_id=args.teacher_version_id,
        split="train_val",
    )

    train_rows, val_rows = split_train_val(train_val_pool, args.val_ratio)

    fieldnames = [
        "sample_id",
        "path",
        "language",
        "transcript",
        "label",
        "label3",
        "teacher_label4",
        "teacher_confidence",
        "teacher_conflict_hint",
        "teacher_scores3_json",
        "teacher_scores4_json",
        "teacher_scores4_source",
        "source",
        "source_kind",
        "speaker",
        "is_key_hard_case",
        "is_real_world",
        "label_source",
        "human_label",
        "teacher_version_id",
        "split",
        "metadata_json",
    ]
    write_csv(LOCAL_TEXT_ROOT / "teacher_train_pool.csv", train_val_pool, fieldnames)
    write_csv(LOCAL_TEXT_ROOT / "train.csv", train_rows, fieldnames)
    write_csv(LOCAL_TEXT_ROOT / "val.csv", val_rows, fieldnames)
    write_csv(LOCAL_TEXT_ROOT / "test.csv", test_rows, fieldnames)

    run_metadata = {
        "schemaVersion": "thesis_v4.local_text_prepare.v3",
        "generatedAt": now_iso(),
        "teacherVersionId": args.teacher_version_id,
        "audio_model_version": args.audio_model_version,
        "text_model_version": args.text_model_version,
        "decision_version": args.decision_version,
        "realWorldFreezeVersion": real_world_freeze.get("freezeId"),
        "realTestFreezeVersion": real_test_freeze.get("freezeId"),
        "controlledFreezeVersion": controlled_freeze.get("freezeId"),
        "manifestsUsed": {
            "realWorldPool": str((REAL_POOL_ROOT / "real_world_pool.csv").resolve()),
            "controlledZhPool": str((CONTROLLED_POOL_ROOT / "controlled_zh_pool.csv").resolve()),
            "reviewSet": str(REVIEW_MANIFEST_PATH.resolve()),
            "realWorldFreeze": str(REAL_WORLD_POOL_FREEZE_PATH.resolve()),
            "realTestFreeze": str(REAL_TEST_FREEZE_PATH.resolve()),
            "controlledFreeze": str(CONTROLLED_POOL_FREEZE_PATH.resolve()),
        },
        "dataset": {
            "teacherTrainPool": len(train_val_pool),
            "train": len(train_rows),
            "val": len(val_rows),
            "test": len(test_rows),
            "trainByLabel": dict(Counter(row["label"] for row in train_rows)),
            "valByLabel": dict(Counter(row["label"] for row in val_rows)),
            "testByLabel": dict(Counter(row["label"] for row in test_rows)),
        },
        "teacherReviewLeakCheck": {
            "reviewIdsExcluded": True,
            "reviewCount": len(review_ids),
        },
        "teacherSoftTargetsIncluded": True,
        "teacherSoftTargetOverrides": {
            "intakeRecordingHumanOverrides": sum(
                1 for row in (train_rows + val_rows + test_rows) if row.get("teacher_scores4_source") == "human_label_override"
            )
        },
    }
    run_metadata_path = LOCAL_TEXT_ROOT / "run_metadata.json"
    write_json(run_metadata_path, run_metadata)

    python_exec = str(VENV_PYTHON) if VENV_PYTHON.exists() else "python"

    baseline_train_cmd = " ".join(
        [
            python_exec,
            str((CURRENT_DIR / "train_text_emotion_4class_from_features.py").resolve()),
            "--train-features",
            str((LOCAL_TEXT_ROOT / "train.csv").resolve()),
            "--val-features",
            str((LOCAL_TEXT_ROOT / "val.csv").resolve()),
            "--test-features",
            str((LOCAL_TEXT_ROOT / "test.csv").resolve()),
            "--output-dir",
            str((LOCAL_TEXT_ROOT / "model_v4_teacher").resolve()),
            "--base-model",
            TEXT_BASE_MODEL,
            "--language-filter",
            "zh",
            "--epochs",
            "6",
            "--batch-size",
            "8",
            "--learning-rate",
            "2e-5",
            "--patience",
            "3",
            "--run-metadata",
            str(run_metadata_path.resolve()),
        ]
    )
    distill_train_cmd = " ".join(
        [
            python_exec,
            str((CURRENT_DIR / "train_text_emotion_4class_from_features.py").resolve()),
            "--train-features",
            str((LOCAL_TEXT_ROOT / "train.csv").resolve()),
            "--val-features",
            str((LOCAL_TEXT_ROOT / "val.csv").resolve()),
            "--test-features",
            str((LOCAL_TEXT_ROOT / "test.csv").resolve()),
            "--output-dir",
            str((LOCAL_TEXT_ROOT / "model_v4_teacher_distill_humanfix_rw35").resolve()),
            "--base-model",
            TEXT_BASE_MODEL,
            "--language-filter",
            "zh",
            "--epochs",
            "6",
            "--batch-size",
            "8",
            "--learning-rate",
            "2e-5",
            "--patience",
            "3",
            "--distill-alpha",
            "0.35",
            "--distill-temperature",
            "2.0",
            "--real-world-sample-weight",
            "3.5",
            "--controlled-sample-weight",
            "1.0",
            "--run-metadata",
            str(run_metadata_path.resolve()),
        ]
    )
    (LOCAL_TEXT_ROOT / "train_text_model_v4_baseline.ps1").write_text(baseline_train_cmd + "\n", encoding="utf-8")
    (LOCAL_TEXT_ROOT / "train_text_model_v4.ps1").write_text(distill_train_cmd + "\n", encoding="utf-8")
    (LOCAL_TEXT_ROOT / "train_text_model_v4_distill.ps1").write_text(distill_train_cmd + "\n", encoding="utf-8")
    (CURRENT_DIR / "train_text_model_v4.ps1").write_text(distill_train_cmd + "\n", encoding="utf-8")
    write_json(LOCAL_TEXT_ROOT / "metadata.json", run_metadata)
    print(json.dumps(run_metadata, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

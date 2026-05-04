from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from thesis_v4_common import (  # noqa: E402
    CONTROLLED_POOL_FREEZE_PATH,
    CONTROLLED_POOL_ROOT,
    LOG_ROOT,
    REAL_POOL_ROOT,
    REAL_TEST_FREEZE_PATH,
    REAL_WORLD_POOL_FREEZE_PATH,
    REVIEW_MANIFEST_PATH,
    TEACHER_ROOT,
    SampleRecord,
    ensure_dirs,
    now_iso,
    read_json,
    read_jsonl,
    read_samples_csv,
    write_json,
    write_samples_csv,
)


EMOTION_LABELS = ("ANG", "HAP", "NEU", "SAD")
BOUNDARY_CUES = ("但是", "不过", "可是", "虽然", "有点", "还行", "其实", "只是", "一般", "还好", "有些")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Freeze thesis v4 real-world and controlled pools after teacher approval.")
    parser.add_argument("--teacher-version-id", required=True)
    parser.add_argument("--teacher-eval-json", default=str((LOG_ROOT / "teacher_eval.json").resolve()))
    parser.add_argument("--real-test-size", type=int, default=8)
    parser.add_argument("--real-test-per-label", type=int, default=2)
    parser.add_argument("--controlled-per-label", type=int, default=6)
    parser.add_argument("--speaker-cap-per-pool", type=int, default=2)
    parser.add_argument("--min-confidence", type=float, default=0.65)
    return parser.parse_args()


def truncate_excerpt(text: str, limit: int = 80) -> str:
    text = re.sub(r"\s+", " ", text or "").strip()
    if len(text) <= limit:
        return text
    return text[: max(limit - 3, 0)] + "..."


def load_teacher_rows(version_id: str) -> dict[str, dict[str, Any]]:
    rows = read_jsonl(TEACHER_ROOT / f"{version_id}.jsonl")
    if not rows:
        raise ValueError(f"teacher artifact missing or empty: {version_id}")
    return {str(row.get("sample_id") or ""): row for row in rows if row.get("sample_id")}


def teacher_eval_gate(path: Path, teacher_version_id: str) -> dict[str, Any]:
    payload = read_json(path, default=None)
    if not isinstance(payload, dict):
        raise ValueError(f"teacher eval payload missing: {path}")
    preferred = payload.get("preferredTeacherVersion")
    passed = bool(payload.get("selectedTeacherPassed"))
    ready = bool(payload.get("trainingReady"))
    if preferred != teacher_version_id:
        raise ValueError(
            f"teacher version mismatch: preferred={preferred!r}, requested={teacher_version_id!r}"
        )
    if not passed or not ready:
        raise ValueError(
            f"teacher version not approved for freezing/training: passed={passed}, trainingReady={ready}"
        )
    return payload


def hard_case_reasons(sample: SampleRecord, teacher: dict[str, Any] | None) -> list[str]:
    reasons: list[str] = []
    human_label = sample.human_label
    teacher_label = None if teacher is None else str(teacher.get("label4") or "")
    teacher_conf = 0.0 if teacher is None else float(teacher.get("teacher_confidence") or 0.0)
    hint = "" if teacher is None else str(teacher.get("conflict_hint") or "").strip()
    transcript = sample.transcript or ""

    if sample.is_key_hard_case:
        reasons.append("seeded_hard_case")
    if human_label and sample.heuristic_label and sample.heuristic_label != human_label:
        reasons.append("heuristic_label_mismatch")
    if human_label and teacher_label and teacher_label != human_label:
        reasons.append("teacher_label_mismatch")
    if teacher_conf and teacher_conf < 0.80:
        reasons.append("teacher_confidence_below_0_80")
    if hint:
        reasons.append("teacher_conflict_hint_present")
    if any(cue in transcript for cue in BOUNDARY_CUES):
        reasons.append("boundary_language_cue")
    if len(transcript) <= 20:
        reasons.append("short_transcript")
    overall_code = str(sample.metadata.get("overallEmotionCode") or "").upper()
    if human_label and overall_code and overall_code != human_label:
        reasons.append("historical_pipeline_mismatch")
    return reasons


def rank_real_test_candidates(samples: list[SampleRecord], teacher_rows: dict[str, dict[str, Any]]) -> dict[str, list[tuple[SampleRecord, list[str]]]]:
    grouped: dict[str, list[tuple[SampleRecord, list[str]]]] = defaultdict(list)
    for sample in samples:
        if sample.human_label not in EMOTION_LABELS:
            continue
        reasons = hard_case_reasons(sample, teacher_rows.get(sample.sample_id))
        grouped[sample.human_label].append((sample, reasons))

    def sort_key(item: tuple[SampleRecord, list[str]]) -> tuple[Any, ...]:
        sample, reasons = item
        teacher_conf = float((teacher_rows.get(sample.sample_id) or {}).get("teacher_confidence") or 0.0)
        return (
            -len(reasons),
            0 if sample.source_kind == "intake_recording" else 1,
            teacher_conf,
            sample.sample_id,
        )

    for label in grouped:
        grouped[label].sort(key=sort_key)
    return grouped


def freeze_real_test(
    real_samples: list[SampleRecord],
    teacher_rows: dict[str, dict[str, Any]],
    review_ids: set[str],
    teacher_version_id: str,
    total_target: int,
    per_label_target: int,
) -> tuple[list[SampleRecord], dict[str, Any], list[SampleRecord]]:
    eligible = [
        sample
        for sample in real_samples
        if sample.sample_id not in review_ids and sample.human_label in EMOTION_LABELS
    ]
    grouped = rank_real_test_candidates(eligible, teacher_rows)
    selected: list[SampleRecord] = []
    selected_meta: list[dict[str, Any]] = []

    for label in EMOTION_LABELS:
        candidates = grouped.get(label, [])
        if len(candidates) < per_label_target:
            raise ValueError(f"not enough eligible real-world samples for label={label}: need {per_label_target}, got {len(candidates)}")
        for sample, reasons in candidates[:per_label_target]:
            selected.append(sample)
            selected_meta.append(
                {
                    "sampleId": sample.sample_id,
                    "humanLabel": sample.human_label,
                    "sourceKind": sample.source_kind,
                    "path": sample.path,
                    "isKeyHardCase": bool(reasons),
                    "hardCaseReasons": reasons,
                    "transcriptExcerpt": truncate_excerpt(sample.transcript),
                }
            )

    if len(selected) != total_target:
        raise ValueError(f"real_test size mismatch: expected={total_target}, got={len(selected)}")

    selected_ids = {sample.sample_id for sample in selected}
    real_dev = [
        sample for sample in real_samples
        if sample.sample_id not in selected_ids and sample.sample_id not in review_ids
    ]
    speaker_overlap_check = "not_applicable" if not any(sample.speaker for sample in selected) else "pending_check"
    freeze_payload = {
        "schemaVersion": "thesis_v4.real_test_freeze.v2",
        "freezeId": f"real_test_freeze_{now_iso().replace(':', '-').replace('T', '_')}",
        "generatedAt": now_iso(),
        "frozen": True,
        "status": "FROZEN",
        "teacherVersionId": teacher_version_id,
        "targetSize": total_target,
        "perLabelTarget": per_label_target,
        "speakerOverlapCheck": speaker_overlap_check,
        "notes": [
            "Frozen real_test is reserved for final evaluation, thesis comparison, and result reporting only.",
            "Frozen real_test must not be reused for prompt tuning, threshold tuning, or training parameter updates.",
            "Selection prioritizes hard/boundary candidates within each label where available.",
        ],
        "samples": selected_meta,
        "byLabel": dict(Counter(item["humanLabel"] for item in selected_meta)),
        "excludedTeacherReviewIds": sorted(review_ids),
        "realDevSampleIds": [sample.sample_id for sample in real_dev],
    }
    return selected, freeze_payload, real_dev


def freeze_real_world_pool(
    real_samples: list[SampleRecord],
    real_test: list[SampleRecord],
    real_dev: list[SampleRecord],
    review_ids: set[str],
    teacher_version_id: str,
) -> dict[str, Any]:
    return {
        "schemaVersion": "thesis_v4.real_world_pool_freeze.v1",
        "freezeId": f"real_world_pool_freeze_{now_iso().replace(':', '-').replace('T', '_')}",
        "generatedAt": now_iso(),
        "frozen": True,
        "status": "FROZEN",
        "teacherVersionId": teacher_version_id,
        "poolSampleIds": [sample.sample_id for sample in real_samples],
        "realTestSampleIds": [sample.sample_id for sample in real_test],
        "realDevSampleIds": [sample.sample_id for sample in real_dev],
        "excludedTeacherReviewIds": sorted(review_ids),
        "notes": [
            "Only new intake recordings use folder-based human labels by rule.",
            "Historical samples remain unlabeled unless separately human-reviewed outside this freeze artifact.",
        ],
    }


def freeze_controlled_pool(
    controlled_candidates: list[SampleRecord],
    teacher_rows: dict[str, dict[str, Any]],
    review_ids: set[str],
    teacher_version_id: str,
    controlled_per_label: int,
    speaker_cap_per_pool: int,
    min_confidence: float,
) -> tuple[list[SampleRecord], dict[str, Any]]:
    grouped: dict[str, list[tuple[SampleRecord, dict[str, Any]]]] = defaultdict(list)
    for sample in controlled_candidates:
        if sample.sample_id in review_ids:
            continue
        teacher = teacher_rows.get(sample.sample_id)
        if not teacher:
            continue
        label = str(teacher.get("label4") or "")
        conf = float(teacher.get("teacher_confidence") or 0.0)
        if label not in EMOTION_LABELS or conf < min_confidence:
            continue
        grouped[label].append((sample, teacher))

    for label in grouped:
        grouped[label].sort(
            key=lambda item: (
                -float(item[1].get("teacher_confidence") or 0.0),
                item[0].speaker,
                item[0].sample_id,
            )
        )

    selected: list[SampleRecord] = []
    freeze_rows: list[dict[str, Any]] = []
    speaker_counts: Counter[str] = Counter()
    used_transcripts: set[str] = set()

    for label in EMOTION_LABELS:
        chosen = 0
        for sample, teacher in grouped.get(label, []):
            if sample.transcript_norm in used_transcripts:
                continue
            if sample.speaker and speaker_counts[sample.speaker] >= speaker_cap_per_pool:
                continue
            teacher_conf = float(teacher.get("teacher_confidence") or 0.0)
            metadata = dict(sample.metadata)
            metadata.update(
                {
                    "teacherLabel4": label,
                    "teacherLabel3": teacher.get("label3"),
                    "teacherConfidence": teacher_conf,
                    "teacherConflictHint": teacher.get("conflict_hint") or "",
                    "teacherVersionId": teacher_version_id,
                }
            )
            selected_sample = SampleRecord(
                **{
                    **sample.__dict__,
                    "human_label": label,
                    "metadata": metadata,
                }
            )
            selected.append(selected_sample)
            freeze_rows.append(
                {
                    "sampleId": selected_sample.sample_id,
                    "label": label,
                    "teacherConfidence": teacher_conf,
                    "speaker": selected_sample.speaker,
                    "sourceKind": selected_sample.source_kind,
                    "transcriptExcerpt": truncate_excerpt(selected_sample.transcript),
                }
            )
            used_transcripts.add(sample.transcript_norm)
            if sample.speaker:
                speaker_counts[sample.speaker] += 1
            chosen += 1
            if chosen >= controlled_per_label:
                break
        if chosen < controlled_per_label:
            raise ValueError(f"controlled_zh_pool insufficient for label={label}: need {controlled_per_label}, got {chosen}")

    freeze_payload = {
        "schemaVersion": "thesis_v4.controlled_zh_pool_freeze.v1",
        "freezeId": f"controlled_zh_pool_freeze_{now_iso().replace(':', '-').replace('T', '_')}",
        "generatedAt": now_iso(),
        "frozen": True,
        "status": "FROZEN",
        "teacherVersionId": teacher_version_id,
        "speakerCapPerPool": speaker_cap_per_pool,
        "minConfidence": min_confidence,
        "speakerOverlapCheck": "not_applicable",
        "notes": [
            "controlled_zh_pool is teacher-labeled and transcript-deduplicated.",
            "teacher_review_set samples are excluded from train/val/test splits.",
            "speaker overlap with real_test is marked not_applicable when real-world speaker ids are unavailable.",
        ],
        "samples": freeze_rows,
        "byLabel": dict(Counter(item["label"] for item in freeze_rows)),
        "excludedTeacherReviewIds": sorted(review_ids),
    }
    return selected, freeze_payload


def main() -> None:
    args = parse_args()
    ensure_dirs()

    teacher_eval_gate(Path(args.teacher_eval_json), args.teacher_version_id)
    teacher_rows = load_teacher_rows(args.teacher_version_id)
    real_samples = read_samples_csv(REAL_POOL_ROOT / "real_world_pool.csv")
    controlled_candidates = read_samples_csv(CONTROLLED_POOL_ROOT / "controlled_zh_candidates.csv")
    review_rows = read_samples_csv(REVIEW_MANIFEST_PATH)
    review_ids = {sample.sample_id for sample in review_rows}

    real_test, real_test_freeze, real_dev = freeze_real_test(
        real_samples=real_samples,
        teacher_rows=teacher_rows,
        review_ids=review_ids,
        teacher_version_id=args.teacher_version_id,
        total_target=args.real_test_size,
        per_label_target=args.real_test_per_label,
    )
    real_world_freeze = freeze_real_world_pool(
        real_samples=real_samples,
        real_test=real_test,
        real_dev=real_dev,
        review_ids=review_ids,
        teacher_version_id=args.teacher_version_id,
    )
    controlled_pool, controlled_freeze = freeze_controlled_pool(
        controlled_candidates=controlled_candidates,
        teacher_rows=teacher_rows,
        review_ids=review_ids,
        teacher_version_id=args.teacher_version_id,
        controlled_per_label=args.controlled_per_label,
        speaker_cap_per_pool=args.speaker_cap_per_pool,
        min_confidence=args.min_confidence,
    )

    write_samples_csv(REAL_POOL_ROOT / "real_dev.csv", real_dev)
    write_samples_csv(REAL_POOL_ROOT / "real_test.csv", real_test)
    write_samples_csv(CONTROLLED_POOL_ROOT / "controlled_zh_pool.csv", controlled_pool)
    write_json(REAL_TEST_FREEZE_PATH, real_test_freeze)
    write_json(REAL_WORLD_POOL_FREEZE_PATH, real_world_freeze)
    write_json(CONTROLLED_POOL_FREEZE_PATH, controlled_freeze)

    payload = {
        "schemaVersion": "thesis_v4.freeze_summary.v1",
        "generatedAt": now_iso(),
        "teacherVersionId": args.teacher_version_id,
        "realWorldPool": {
            "total": len(real_samples),
            "realDev": len(real_dev),
            "realTest": len(real_test),
            "byTestLabel": dict(Counter(sample.human_label for sample in real_test if sample.human_label)),
        },
        "controlledZhPool": {
            "total": len(controlled_pool),
            "byLabel": dict(Counter(sample.human_label for sample in controlled_pool if sample.human_label)),
        },
        "artifacts": {
            "realWorldFreeze": str(REAL_WORLD_POOL_FREEZE_PATH.resolve()),
            "realTestFreeze": str(REAL_TEST_FREEZE_PATH.resolve()),
            "controlledFreeze": str(CONTROLLED_POOL_FREEZE_PATH.resolve()),
            "controlledPoolCsv": str((CONTROLLED_POOL_ROOT / "controlled_zh_pool.csv").resolve()),
        },
    }
    write_json(LOG_ROOT / "freeze_summary.json", payload)
    print(json.dumps(payload, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

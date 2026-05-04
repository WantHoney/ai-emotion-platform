from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from thesis_v4_common import (  # noqa: E402
    CONTROLLED_POOL_ROOT,
    LOG_ROOT,
    REAL_POOL_ROOT,
    TEACHER_DIFF_PATH,
    expected_sample_ids_from_manifests,
    now_iso,
    read_samples_csv,
    validate_teacher_artifact,
    write_json,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate Gemma teacher versions on the thesis v4 human review set.")
    parser.add_argument("--review-manifest", required=True)
    parser.add_argument("--teacher-v1", required=True)
    parser.add_argument("--teacher-v2", default="")
    parser.add_argument("--teacher-v1-name", default="")
    parser.add_argument("--teacher-v2-name", default="")
    parser.add_argument("--expected-manifest", action="append", default=[])
    parser.add_argument("--emotion4-consistency-min", type=float, default=0.80)
    parser.add_argument("--hard-case-pass-min", type=float, default=0.85)
    parser.add_argument("--conflict-hint-quality-min", type=float, default=0.75)
    return parser.parse_args()


def safe_get(table: dict[str, dict[str, Any]], sample_id: str) -> dict[str, Any] | None:
    return table.get(sample_id)


def rate_hint(conflict_hint: str, is_key_hard_case: bool) -> bool:
    text = (conflict_hint or "").strip()
    if is_key_hard_case:
        return len(text) >= 4
    return True


def evaluate_version(review_rows: list[Any], teacher_rows: dict[str, dict[str, Any]], version_name: str) -> dict[str, Any]:
    sample_rows = []
    total = 0
    label_matches = 0
    hard_total = 0
    hard_pass = 0
    hint_total = 0
    hint_pass = 0

    for sample in review_rows:
        teacher = safe_get(teacher_rows, sample.sample_id)
        teacher_label = None if teacher is None else teacher.get("label4")
        teacher_conf = None if teacher is None else teacher.get("teacher_confidence")
        conflict_hint = "" if teacher is None else str(teacher.get("conflict_hint") or "")
        label_ok = bool(teacher_label == sample.human_label)
        hint_ok = rate_hint(conflict_hint, sample.is_key_hard_case)
        if teacher is not None:
            total += 1
            label_matches += int(label_ok)
            hint_total += 1
            hint_pass += int(hint_ok)
            if sample.is_key_hard_case:
                hard_total += 1
                hard_pass += int(label_ok)
        sample_rows.append(
            {
                "sample_id": sample.sample_id,
                "human_label": sample.human_label,
                "teacher_label": teacher_label,
                "teacher_confidence": teacher_conf,
                "conflict_hint": conflict_hint,
                "label_match": label_ok,
                "hint_ok": hint_ok,
                "is_key_hard_case": sample.is_key_hard_case,
                "version": version_name,
            }
        )

    return {
        "version": version_name,
        "sampleCount": total,
        "emotion4Consistency": None if total == 0 else label_matches / total,
        "hardCasesPassRate": None if hard_total == 0 else hard_pass / hard_total,
        "conflictHintQuality": None if hint_total == 0 else hint_pass / hint_total,
        "sampleRows": sample_rows,
    }


def version_passed(summary: dict[str, Any] | None, args: argparse.Namespace) -> bool:
    if not summary:
        return False
    return bool(
        (summary.get("emotion4Consistency") or 0.0) >= args.emotion4_consistency_min
        and (summary.get("hardCasesPassRate") or 0.0) >= args.hard_case_pass_min
        and (summary.get("conflictHintQuality") or 0.0) >= args.conflict_hint_quality_min
    )


def decide_preferred(
    e2b: dict[str, Any],
    e4b: dict[str, Any] | None,
    args: argparse.Namespace,
    teacher_v1_name: str,
    teacher_v2_name: str | None,
) -> str | None:
    e2b_pass = version_passed(e2b, args)
    e4b_pass = version_passed(e4b, args) if e4b else False
    if not e4b:
        return teacher_v1_name if e2b_pass else None
    e2b_score = (
        float(e2b.get("emotion4Consistency") or 0.0),
        float(e2b.get("hardCasesPassRate") or 0.0),
        float(e2b.get("conflictHintQuality") or 0.0),
    )
    e4b_score = (
        float(e4b.get("emotion4Consistency") or 0.0),
        float(e4b.get("hardCasesPassRate") or 0.0),
        float(e4b.get("conflictHintQuality") or 0.0),
    )
    if e4b_pass and (not e2b_pass or e4b_score > e2b_score):
        return teacher_v2_name
    if e2b_pass:
        return teacher_v1_name
    return None


def build_diff_rows(
    review_rows: list[Any],
    by_id_v1: dict[str, dict[str, Any]],
    by_id_v2: dict[str, dict[str, Any]],
    teacher_v1_name: str,
    teacher_v2_name: str,
) -> dict[str, Any]:
    rows = []
    e2b_wrong_e4b_right = []
    both_wrong = []
    hint_changed = []
    for sample in review_rows:
        row_v1 = by_id_v1.get(sample.sample_id, {})
        row_v2 = by_id_v2.get(sample.sample_id, {})
        e2b_label = row_v1.get("teacher_label")
        e4b_label = row_v2.get("teacher_label")
        e2b_correct = bool(e2b_label == sample.human_label)
        e4b_correct = bool(e4b_label == sample.human_label)
        hint_v1 = row_v1.get("conflict_hint") or ""
        hint_v2 = row_v2.get("conflict_hint") or ""
        changed = hint_v1 != hint_v2
        delta_summary = []
        if e2b_correct != e4b_correct:
            delta_summary.append("label_correctness_changed")
        if changed:
            delta_summary.append("hint_changed")
        row = {
            "sample_id": sample.sample_id,
            "human_label": sample.human_label,
            "teacher_v1_name": teacher_v1_name,
            "teacher_v2_name": teacher_v2_name,
            "teacher_label_v1": e2b_label,
            "teacher_label_v2": e4b_label,
            "v1_correct": e2b_correct,
            "v2_correct": e4b_correct,
            "conflict_hint_v1": hint_v1,
            "conflict_hint_v2": hint_v2,
            "hint_changed": changed,
            "delta_summary": delta_summary,
        }
        rows.append(row)
        if not e2b_correct and e4b_correct:
            e2b_wrong_e4b_right.append(row)
        if not e2b_correct and not e4b_correct:
            both_wrong.append(row)
        if changed:
            hint_changed.append(row)
    return {
        "schemaVersion": "thesis_v4.teacher_diff.v1",
        "generatedAt": now_iso(),
        "teacherV1Name": teacher_v1_name,
        "teacherV2Name": teacher_v2_name,
        "v1WrongV2Right": e2b_wrong_e4b_right,
        "bothWrong": both_wrong,
        "hintChanged": hint_changed,
        "samples": rows,
    }


def main() -> None:
    args = parse_args()
    teacher_v1_name = args.teacher_v1_name or Path(args.teacher_v1).stem
    teacher_v2_name = args.teacher_v2_name or (Path(args.teacher_v2).stem if args.teacher_v2 else "")
    expected_manifest_paths = [Path(item).resolve() for item in args.expected_manifest]
    if not expected_manifest_paths:
        expected_manifest_paths = [
            (REAL_POOL_ROOT / "real_world_pool.csv").resolve(),
            (CONTROLLED_POOL_ROOT / "controlled_zh_candidates.csv").resolve(),
            Path(args.review_manifest).resolve(),
        ]
    expected_sample_ids = expected_sample_ids_from_manifests(expected_manifest_paths)
    review_rows = read_samples_csv(Path(args.review_manifest))
    artifact_v1 = validate_teacher_artifact(Path(args.teacher_v1), expected_sample_ids)
    if not artifact_v1["isComplete"]:
        raise ValueError(f"teacher_v1 artifact incomplete: {json.dumps({k: v for k, v in artifact_v1.items() if k not in {'rows', 'rowById'}}, ensure_ascii=False)}")
    artifact_v2 = validate_teacher_artifact(Path(args.teacher_v2), expected_sample_ids) if args.teacher_v2 else None
    if artifact_v2 and not artifact_v2["isComplete"]:
        raise ValueError(f"teacher_v2 artifact incomplete: {json.dumps({k: v for k, v in artifact_v2.items() if k not in {'rows', 'rowById'}}, ensure_ascii=False)}")
    teacher_v1_rows = artifact_v1["rowById"]
    teacher_v2_rows = artifact_v2["rowById"] if artifact_v2 else {}

    v1_summary = evaluate_version(review_rows, teacher_v1_rows, teacher_v1_name)
    v2_summary = evaluate_version(review_rows, teacher_v2_rows, teacher_v2_name) if teacher_v2_rows else None

    preferred = decide_preferred(v1_summary, v2_summary, args, teacher_v1_name, teacher_v2_name or None)
    upgrade_recommended = not version_passed(v1_summary, args)
    training_ready = preferred is not None
    selected_teacher_passed = bool(
        preferred == teacher_v1_name and version_passed(v1_summary, args)
        or preferred == teacher_v2_name and version_passed(v2_summary, args)
    )

    by_id_v1 = {row["sample_id"]: row for row in v1_summary["sampleRows"]}
    by_id_v2 = {row["sample_id"]: row for row in (v2_summary["sampleRows"] if v2_summary else [])}
    teacher_eval_rows = []
    for sample in review_rows:
        row_v1 = by_id_v1.get(sample.sample_id, {})
        row_v2 = by_id_v2.get(sample.sample_id, {})
        preferred_row = row_v2 if preferred == teacher_v2_name and row_v2 else row_v1
        label_match = bool(preferred_row.get("teacher_label") == sample.human_label)
        if label_match and sample.is_key_hard_case and preferred_row.get("hint_ok"):
            review_result = "PASS_WITH_HINT"
        elif label_match:
            review_result = "PASS"
        else:
            review_result = "FAIL_LABEL"
        teacher_eval_rows.append(
            {
                "sample_id": sample.sample_id,
                "transcript": sample.transcript,
                "human_label": sample.human_label,
                "teacher_label_v1": row_v1.get("teacher_label"),
                "teacher_label_v2": row_v2.get("teacher_label"),
                "teacher_confidence": preferred_row.get("teacher_confidence"),
                "teacher_confidence_v1": row_v1.get("teacher_confidence"),
                "teacher_confidence_v2": row_v2.get("teacher_confidence"),
                "conflict_hint": preferred_row.get("conflict_hint") or "",
                "conflict_hint_v1": row_v1.get("conflict_hint") or "",
                "conflict_hint_v2": row_v2.get("conflict_hint") or "",
                "is_key_hard_case": sample.is_key_hard_case,
                "review_result": review_result,
            }
        )

    diff_payload = None
    if v2_summary:
        diff_payload = build_diff_rows(review_rows, by_id_v1, by_id_v2, teacher_v1_name, teacher_v2_name)
        write_json(TEACHER_DIFF_PATH, diff_payload)

    payload = {
        "schemaVersion": "thesis_v4.teacher_eval.v1",
        "generatedAt": now_iso(),
        "thresholds": {
            "emotion4ConsistencyMin": args.emotion4_consistency_min,
            "hardCasesPassMin": args.hard_case_pass_min,
            "conflictHintQualityMin": args.conflict_hint_quality_min,
        },
        "expectedSampleCount": len(expected_sample_ids),
        "preferredTeacherVersion": preferred,
        "selectedTeacherPassed": selected_teacher_passed,
        "trainingReady": training_ready,
        "upgradeRecommended": upgrade_recommended,
        teacher_v1_name: {
            "artifactCompleteness": {k: v for k, v in artifact_v1.items() if k not in {"rows", "rowById"}},
            "emotion4Consistency": v1_summary["emotion4Consistency"],
            "hardCasesPassRate": v1_summary["hardCasesPassRate"],
            "conflictHintQuality": v1_summary["conflictHintQuality"],
        },
        teacher_v2_name: None if not v2_summary else {
            "artifactCompleteness": {k: v for k, v in artifact_v2.items() if k not in {"rows", "rowById"}},
            "emotion4Consistency": v2_summary["emotion4Consistency"],
            "hardCasesPassRate": v2_summary["hardCasesPassRate"],
            "conflictHintQuality": v2_summary["conflictHintQuality"],
        },
        "diffArtifact": None if not diff_payload else str(TEACHER_DIFF_PATH.resolve()),
        "samples": teacher_eval_rows,
    }
    output_path = LOG_ROOT / "teacher_eval.json"
    write_json(output_path, payload)
    print(json.dumps(payload, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

import argparse
import csv
import json
import sys
from dataclasses import dataclass
from pathlib import Path

CURRENT_DIR = Path(__file__).resolve().parent
PARENT_DIR = CURRENT_DIR.parent
if str(PARENT_DIR) not in sys.path:
    sys.path.insert(0, str(PARENT_DIR))

from hf_wav2vec2_runtime import HFWav2Vec2Runtime


CSV_FIELDS = [
    "path",
    "label",
    "source_label",
    "duration_sec",
    "source_dataset",
    "speaker",
    "file_id",
    "text",
    "selection_reason",
    "baseline_pred",
    "baseline_confidence",
]


@dataclass(frozen=True)
class CandidateRow:
    path: str
    label: str
    source_label: str
    duration_sec: str
    source_dataset: str
    speaker: str
    file_id: str
    text: str
    selection_reason: str
    baseline_pred: str
    baseline_confidence: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build a small hard-example manifest for sadness-bias repair.")
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--source-manifests", required=True, help="Comma-separated CSV manifests to mine.")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--extra-manifest", default="", help="Optional CSV with manually labeled extra rows.")
    parser.add_argument("--device", default="cpu", choices=["auto", "cpu", "cuda"])
    parser.add_argument("--max-hard-hap-to-sad", type=int, default=18)
    parser.add_argument("--max-hard-hap-to-neu", type=int, default=10)
    parser.add_argument("--max-hard-neu-to-sad", type=int, default=10)
    parser.add_argument("--max-anchor-ang", type=int, default=12)
    parser.add_argument("--max-anchor-hap", type=int, default=12)
    parser.add_argument("--max-anchor-neu", type=int, default=12)
    parser.add_argument("--max-anchor-sad", type=int, default=16)
    parser.add_argument("--val-every", type=int, default=5, help="Place every Nth non-extra sample into val.")
    return parser.parse_args()


def read_manifest_rows(path: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for raw in reader:
            row = {key: (value or "").strip() for key, value in raw.items()}
            if row.get("path") and row.get("label"):
                rows.append(row)
    if not rows:
        raise ValueError(f"empty manifest: {path}")
    return rows


def write_manifest(path: Path, rows: list[CandidateRow]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    "path": row.path,
                    "label": row.label,
                    "source_label": row.source_label,
                    "duration_sec": row.duration_sec,
                    "source_dataset": row.source_dataset,
                    "speaker": row.speaker,
                    "file_id": row.file_id,
                    "text": row.text,
                    "selection_reason": row.selection_reason,
                    "baseline_pred": row.baseline_pred,
                    "baseline_confidence": f"{row.baseline_confidence:.6f}",
                }
            )


def to_candidate(row: dict[str, str], reason: str, pred: str, confidence: float) -> CandidateRow:
    return CandidateRow(
        path=row.get("path", ""),
        label=row.get("label", "").upper(),
        source_label=row.get("source_label", ""),
        duration_sec=row.get("duration_sec", ""),
        source_dataset=row.get("source_dataset", "") or "unknown",
        speaker=row.get("speaker", ""),
        file_id=row.get("file_id", ""),
        text=row.get("text", ""),
        selection_reason=reason,
        baseline_pred=pred.upper(),
        baseline_confidence=float(confidence),
    )


def deduplicate(rows: list[CandidateRow]) -> list[CandidateRow]:
    seen: set[str] = set()
    unique: list[CandidateRow] = []
    for row in rows:
        key = str(Path(row.path).resolve())
        if key in seen:
            continue
        seen.add(key)
        unique.append(row)
    return unique


def split_rows(rows: list[CandidateRow], val_every: int) -> tuple[list[CandidateRow], list[CandidateRow]]:
    train_rows: list[CandidateRow] = []
    val_rows: list[CandidateRow] = []
    by_label: dict[str, list[CandidateRow]] = {}
    for row in rows:
        by_label.setdefault(row.label, []).append(row)

    for label, label_rows in sorted(by_label.items()):
        extra_rows = [row for row in label_rows if row.selection_reason == "extra_manual"]
        mined_rows = [row for row in label_rows if row.selection_reason != "extra_manual"]
        train_rows.extend(extra_rows)
        for index, row in enumerate(mined_rows, start=1):
            if val_every > 0 and index % val_every == 0:
                val_rows.append(row)
            else:
                train_rows.append(row)
    return train_rows, val_rows


def summarize(rows: list[CandidateRow]) -> dict[str, dict[str, int]]:
    summary: dict[str, dict[str, int]] = {}
    for row in rows:
        bucket = summary.setdefault(row.selection_reason, {})
        bucket[row.label] = bucket.get(row.label, 0) + 1
    return summary


def main() -> None:
    args = parse_args()
    runtime = HFWav2Vec2Runtime(model_dir=args.model_dir, sample_rate=16000, device=args.device)
    source_manifests = [Path(item.strip()).resolve() for item in args.source_manifests.split(",") if item.strip()]
    if not source_manifests:
        raise ValueError("source-manifests is empty")

    scored_rows: list[CandidateRow] = []
    for manifest_path in source_manifests:
        for row in read_manifest_rows(manifest_path):
            pred, confidence = runtime.predict_file(row["path"])
            scored_rows.append(to_candidate(row, reason="baseline_scan", pred=pred, confidence=confidence))

    def pick(limit: int, truth: str, pred: str, reason: str, require_correct: bool = False) -> list[CandidateRow]:
        bucket = [
            row
            for row in scored_rows
            if row.label == truth and ((row.baseline_pred == pred) if not require_correct else (row.baseline_pred == truth))
        ]
        bucket.sort(key=lambda item: item.baseline_confidence, reverse=True)
        return [
            CandidateRow(
                **{
                    **row.__dict__,
                    "selection_reason": reason,
                }
            )
            for row in bucket[:limit]
        ]

    selected_rows: list[CandidateRow] = []
    selected_rows.extend(pick(args.max_hard_hap_to_sad, truth="HAP", pred="SAD", reason="hard_hap_to_sad"))
    selected_rows.extend(pick(args.max_hard_hap_to_neu, truth="HAP", pred="NEU", reason="hard_hap_to_neu"))
    selected_rows.extend(pick(args.max_hard_neu_to_sad, truth="NEU", pred="SAD", reason="hard_neu_to_sad"))
    selected_rows.extend(pick(args.max_anchor_ang, truth="ANG", pred="ANG", reason="anchor_ang", require_correct=True))
    selected_rows.extend(pick(args.max_anchor_hap, truth="HAP", pred="HAP", reason="anchor_hap", require_correct=True))
    selected_rows.extend(pick(args.max_anchor_neu, truth="NEU", pred="NEU", reason="anchor_neu", require_correct=True))
    selected_rows.extend(pick(args.max_anchor_sad, truth="SAD", pred="SAD", reason="anchor_sad", require_correct=True))

    if args.extra_manifest:
        extra_rows = read_manifest_rows(Path(args.extra_manifest).resolve())
        for row in extra_rows:
            selected_rows.append(
                CandidateRow(
                    path=row.get("path", ""),
                    label=row.get("label", "").upper(),
                    source_label=row.get("source_label", ""),
                    duration_sec=row.get("duration_sec", ""),
                    source_dataset=row.get("source_dataset", "") or "local_real_world",
                    speaker=row.get("speaker", ""),
                    file_id=row.get("file_id", ""),
                    text=row.get("text", ""),
                    selection_reason="extra_manual",
                    baseline_pred=(row.get("baseline_pred") or "").upper(),
                    baseline_confidence=float(row.get("baseline_confidence") or 0.0),
                )
            )

    unique_rows = deduplicate(selected_rows)
    train_rows, val_rows = split_rows(unique_rows, args.val_every)

    output_dir = Path(args.output_dir).resolve()
    write_manifest(output_dir / "all.csv", unique_rows)
    write_manifest(output_dir / "train.csv", train_rows)
    write_manifest(output_dir / "val.csv", val_rows)
    summary = {
        "modelDir": str(Path(args.model_dir).resolve()),
        "sourceManifests": [str(path) for path in source_manifests],
        "extraManifest": str(Path(args.extra_manifest).resolve()) if args.extra_manifest else None,
        "selectedTotal": len(unique_rows),
        "trainTotal": len(train_rows),
        "valTotal": len(val_rows),
        "selectedByReason": summarize(unique_rows),
        "trainByReason": summarize(train_rows),
        "valByReason": summarize(val_rows),
    }
    (output_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

import argparse
import csv
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

import soundfile as sf


SUPPORTED_EXT = {".wav", ".mp3", ".m4a", ".flac", ".ogg", ".webm"}

IEMOCAP_DEFAULT_MAP = {
    "ang": "ANGRY",
    "anger": "ANGRY",
    "hap": "HAPPY",
    "happiness": "HAPPY",
    "exc": "HAPPY",
    "sad": "SAD",
    "sadness": "SAD",
    "neu": "NEUTRAL",
    "neutral": "NEUTRAL",
    "calm": "CALM",
    "fear": "FEAR",
    "fea": "FEAR",
}


@dataclass(frozen=True)
class Sample:
    path: str
    split: str
    source_label: str
    label: str
    duration_sec: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build train/val/test manifest CSV files for speech emotion training."
    )
    parser.add_argument("--input-dir", required=True, help="Dataset root directory.")
    parser.add_argument("--output-dir", required=True, help="Manifest output directory.")
    parser.add_argument(
        "--mode",
        default="auto",
        choices=["auto", "pre_split", "flat"],
        help="auto: detect train/val/test subdirs first; otherwise use flat mode.",
    )
    parser.add_argument("--train-ratio", type=float, default=0.8)
    parser.add_argument("--val-ratio", type=float, default=0.1)
    parser.add_argument("--test-ratio", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--label-map-json",
        default=None,
        help="Optional JSON file mapping source label to canonical label.",
    )
    parser.add_argument(
        "--use-iemocap-map",
        action="store_true",
        help="Apply built-in IEMOCAP code mapping first (ang/hap/exc/sad/neu...).",
    )
    parser.add_argument(
        "--allowed-labels",
        default="",
        help="Comma-separated canonical labels. Empty means keep all.",
    )
    parser.add_argument(
        "--extensions",
        default=",".join(sorted(SUPPORTED_EXT)),
        help="Comma-separated file extensions.",
    )
    return parser.parse_args()


def normalize_label(raw_label: str, label_map: dict[str, str]) -> str:
    key = raw_label.strip().lower()
    mapped = label_map.get(key, raw_label)
    return mapped.strip().upper()


def deterministic_bucket(key: str, seed: int) -> float:
    digest = hashlib.sha1(f"{seed}:{key}".encode("utf-8")).hexdigest()
    value = int(digest[:12], 16)
    return value / float(16**12 - 1)


def read_duration_sec(path: Path) -> float:
    try:
        info = sf.info(str(path))
        if info.frames <= 0 or info.samplerate <= 0:
            return 0.0
        return float(info.frames) / float(info.samplerate)
    except Exception:
        return 0.0


def list_audio_files(root: Path, allowed_ext: set[str]) -> list[Path]:
    return [
        p
        for p in root.rglob("*")
        if p.is_file() and p.suffix.lower() in allowed_ext
    ]


def detect_mode(input_dir: Path, requested_mode: str) -> str:
    if requested_mode != "auto":
        return requested_mode
    has_pre_split = all((input_dir / split).is_dir() for split in ("train", "val", "test"))
    return "pre_split" if has_pre_split else "flat"


def build_samples_pre_split(
    input_dir: Path,
    label_map: dict[str, str],
    allowed_labels: set[str] | None,
    allowed_ext: set[str],
) -> list[Sample]:
    rows: list[Sample] = []
    for split in ("train", "val", "test"):
        split_dir = input_dir / split
        if not split_dir.is_dir():
            continue
        for label_dir in split_dir.iterdir():
            if not label_dir.is_dir():
                continue
            source_label = label_dir.name
            label = normalize_label(source_label, label_map)
            if allowed_labels and label not in allowed_labels:
                continue
            for audio_path in list_audio_files(label_dir, allowed_ext):
                rows.append(
                    Sample(
                        path=str(audio_path.resolve()),
                        split=split,
                        source_label=source_label,
                        label=label,
                        duration_sec=read_duration_sec(audio_path),
                    )
                )
    return rows


def build_samples_flat(
    input_dir: Path,
    label_map: dict[str, str],
    allowed_labels: set[str] | None,
    allowed_ext: set[str],
    train_ratio: float,
    val_ratio: float,
    test_ratio: float,
    seed: int,
) -> list[Sample]:
    total = train_ratio + val_ratio + test_ratio
    if total <= 0:
        raise ValueError("train/val/test ratio sum must be > 0")
    train_ratio /= total
    val_ratio /= total
    test_ratio /= total

    rows: list[Sample] = []
    for label_dir in input_dir.iterdir():
        if not label_dir.is_dir():
            continue
        source_label = label_dir.name
        label = normalize_label(source_label, label_map)
        if allowed_labels and label not in allowed_labels:
            continue
        files = list_audio_files(label_dir, allowed_ext)
        for audio_path in files:
            bucket = deterministic_bucket(str(audio_path.resolve()), seed=seed)
            if bucket < train_ratio:
                split = "train"
            elif bucket < (train_ratio + val_ratio):
                split = "val"
            else:
                split = "test"
            rows.append(
                Sample(
                    path=str(audio_path.resolve()),
                    split=split,
                    source_label=source_label,
                    label=label,
                    duration_sec=read_duration_sec(audio_path),
                )
            )
    return rows


def write_manifest(path: Path, rows: list[Sample]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["path", "label", "source_label", "duration_sec"])
        for row in rows:
            writer.writerow([row.path, row.label, row.source_label, f"{row.duration_sec:.6f}"])


def summarize(rows: list[Sample]) -> dict[str, object]:
    split_counts: dict[str, int] = {"train": 0, "val": 0, "test": 0}
    label_counts: dict[str, int] = {}
    for row in rows:
        split_counts[row.split] = split_counts.get(row.split, 0) + 1
        label_counts[row.label] = label_counts.get(row.label, 0) + 1
    return {"total": len(rows), "split_counts": split_counts, "label_counts": label_counts}


def main() -> None:
    args = parse_args()
    input_dir = Path(args.input_dir).resolve()
    output_dir = Path(args.output_dir).resolve()
    if not input_dir.is_dir():
        raise FileNotFoundError(f"input-dir not found: {input_dir}")

    allowed_ext = {("." + ext.strip().lstrip(".")).lower() for ext in args.extensions.split(",") if ext.strip()}
    label_map: dict[str, str] = {}
    if args.use_iemocap_map:
        label_map.update(IEMOCAP_DEFAULT_MAP)
    if args.label_map_json:
        with Path(args.label_map_json).open("r", encoding="utf-8") as f:
            custom_map = json.load(f)
        label_map.update({str(k).lower(): str(v) for k, v in custom_map.items()})

    allowed_labels = {x.strip().upper() for x in args.allowed_labels.split(",") if x.strip()}
    if not allowed_labels:
        allowed_labels = None

    mode = detect_mode(input_dir, args.mode)
    if mode == "pre_split":
        rows = build_samples_pre_split(
            input_dir=input_dir,
            label_map=label_map,
            allowed_labels=allowed_labels,
            allowed_ext=allowed_ext,
        )
    else:
        rows = build_samples_flat(
            input_dir=input_dir,
            label_map=label_map,
            allowed_labels=allowed_labels,
            allowed_ext=allowed_ext,
            train_ratio=args.train_ratio,
            val_ratio=args.val_ratio,
            test_ratio=args.test_ratio,
            seed=args.seed,
        )

    grouped = {
        "train": [r for r in rows if r.split == "train"],
        "val": [r for r in rows if r.split == "val"],
        "test": [r for r in rows if r.split == "test"],
    }

    for split, split_rows in grouped.items():
        write_manifest(output_dir / f"{split}.csv", split_rows)
    summary = summarize(rows)
    with (output_dir / "summary.json").open("w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print(json.dumps({"mode": mode, **summary}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

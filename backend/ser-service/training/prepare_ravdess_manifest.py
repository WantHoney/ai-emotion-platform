import argparse
import csv
import json
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

import soundfile as sf


# RAVDESS emotion id mapping by filename token #3:
# 01 neutral, 02 calm, 03 happy, 04 sad, 05 angry, 06 fearful, 07 disgust, 08 surprised
PRESET_MAP = {
    "4class": {
        "01": "neu",
        "02": "neu",
        "03": "hap",
        "04": "sad",
        "05": "ang",
    },
    "6class": {
        "01": "neu",
        "02": "neu",
        "03": "hap",
        "04": "sad",
        "05": "ang",
        "06": "fea",
    },
}


@dataclass(frozen=True)
class Row:
    path: str
    label: str
    source_label: str
    duration_sec: float
    actor_id: int
    file_id: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build train/val/test manifests from raw RAVDESS.")
    parser.add_argument("--raw-dir", required=True, help="RAVDESS root (contains Actor_01..Actor_24).")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--preset", default="4class", choices=sorted(PRESET_MAP.keys()))
    parser.add_argument("--train-actors", default="1-18")
    parser.add_argument("--val-actors", default="19-21")
    parser.add_argument("--test-actors", default="22-24")
    parser.add_argument("--min-duration-sec", type=float, default=0.2)
    return parser.parse_args()


def parse_actor_ranges(text: str) -> set[int]:
    result: set[int] = set()
    for token in [x.strip() for x in text.split(",") if x.strip()]:
        if "-" in token:
            s, e = token.split("-", 1)
            start = int(s)
            end = int(e)
            for v in range(min(start, end), max(start, end) + 1):
                result.add(v)
        else:
            result.add(int(token))
    return result


def read_duration(path: Path) -> float:
    try:
        info = sf.info(str(path))
        if info.frames <= 0 or info.samplerate <= 0:
            return 0.0
        return float(info.frames) / float(info.samplerate)
    except Exception:
        return 0.0


def parse_file_tokens(wav_path: Path) -> tuple[str, str]:
    # Example: 03-01-05-01-01-01-01.wav
    tokens = wav_path.stem.split("-")
    if len(tokens) != 7:
        raise ValueError(f"unexpected RAVDESS file pattern: {wav_path.name}")
    emo_id = tokens[2]
    actor_id = tokens[6]
    return emo_id, actor_id


def write_manifest(path: Path, rows: list[Row]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["path", "label", "source_label", "duration_sec", "actor_id", "file_id"])
        for row in rows:
            writer.writerow(
                [
                    row.path,
                    row.label,
                    row.source_label,
                    f"{row.duration_sec:.6f}",
                    row.actor_id,
                    row.file_id,
                ]
            )


def main() -> None:
    args = parse_args()
    raw_dir = Path(args.raw_dir).resolve()
    output_dir = Path(args.output_dir).resolve()
    if not raw_dir.is_dir():
        raise FileNotFoundError(f"raw-dir not found: {raw_dir}")

    train_actors = parse_actor_ranges(args.train_actors)
    val_actors = parse_actor_ranges(args.val_actors)
    test_actors = parse_actor_ranges(args.test_actors)
    overlap = (train_actors & val_actors) | (train_actors & test_actors) | (val_actors & test_actors)
    if overlap:
        raise ValueError(f"actor split overlap detected: {sorted(overlap)}")

    label_map = PRESET_MAP[args.preset]
    rows_by_split: dict[str, list[Row]] = {"train": [], "val": [], "test": []}
    dropped = Counter()
    source_count = Counter()
    target_count = Counter()
    split_count = Counter()

    wav_files = sorted(raw_dir.rglob("*.wav"))
    if not wav_files:
        raise ValueError(f"no wav files found under {raw_dir}")

    for wav_path in wav_files:
        try:
            emo_id, actor_id_text = parse_file_tokens(wav_path)
        except Exception:
            dropped["bad_filename"] += 1
            continue

        if emo_id not in label_map:
            dropped[f"label_{emo_id}"] += 1
            continue
        actor_id = int(actor_id_text)

        if actor_id in train_actors:
            split = "train"
        elif actor_id in val_actors:
            split = "val"
        elif actor_id in test_actors:
            split = "test"
        else:
            dropped["unassigned_actor"] += 1
            continue

        duration_sec = read_duration(wav_path)
        if duration_sec < args.min_duration_sec:
            dropped["too_short"] += 1
            continue

        target_label = label_map[emo_id]
        source_count[emo_id] += 1
        target_count[target_label] += 1
        split_count[split] += 1
        rows_by_split[split].append(
            Row(
                path=str(wav_path.resolve()),
                label=target_label.upper(),
                source_label=emo_id,
                duration_sec=duration_sec,
                actor_id=actor_id,
                file_id=wav_path.stem,
            )
        )

    for split in ("train", "val", "test"):
        write_manifest(output_dir / f"{split}.csv", rows_by_split[split])

    split_label_counts: dict[str, dict[str, int]] = {}
    for split, rows in rows_by_split.items():
        c = Counter(r.label for r in rows)
        split_label_counts[split] = dict(sorted(c.items()))

    summary = {
        "preset": args.preset,
        "raw_dir": str(raw_dir),
        "output_dir": str(output_dir),
        "split_actors": {
            "train": sorted(train_actors),
            "val": sorted(val_actors),
            "test": sorted(test_actors),
        },
        "counts": {
            "total": int(sum(split_count.values())),
            "split": dict(split_count),
            "source_label": dict(source_count),
            "target_label": dict(target_count),
            "dropped": dict(dropped),
        },
        "split_label_counts": split_label_counts,
    }
    with (output_dir / "summary.json").open("w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

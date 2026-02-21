import argparse
import csv
import json
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

import soundfile as sf


PRESET_MAP = {
    "4class": {
        "angry": "ang",
        "happy": "hap",
        "neutral": "neu",
        "sad": "sad",
    },
    "6class": {
        "angry": "ang",
        "happy": "hap",
        "neutral": "neu",
        "sad": "sad",
        "fear": "fea",
        "surprise": "sur",
    },
}


@dataclass(frozen=True)
class Row:
    path: str
    label: str
    source_label: str
    duration_sec: float
    speaker: str
    file_id: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build train/val/test manifests from raw CASIA emotion corpus.")
    parser.add_argument("--raw-dir", required=True, help="CASIA root.")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--preset", default="4class", choices=sorted(PRESET_MAP.keys()))
    parser.add_argument("--train-speakers", default="liuchanhg,wangzhe")
    parser.add_argument("--val-speakers", default="zhaoquanyin")
    parser.add_argument("--test-speakers", default="ZhaoZuoxiang")
    parser.add_argument("--min-duration-sec", type=float, default=0.2)
    return parser.parse_args()


def parse_csv_set(text: str) -> set[str]:
    return {x.strip() for x in text.split(",") if x.strip()}


def read_duration(path: Path) -> float:
    try:
        info = sf.info(str(path))
        if info.frames <= 0 or info.samplerate <= 0:
            return 0.0
        return float(info.frames) / float(info.samplerate)
    except Exception:
        return 0.0


def write_manifest(path: Path, rows: list[Row]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["path", "label", "source_label", "duration_sec", "speaker", "file_id"])
        for row in rows:
            writer.writerow(
                [
                    row.path,
                    row.label,
                    row.source_label,
                    f"{row.duration_sec:.6f}",
                    row.speaker,
                    row.file_id,
                ]
            )


def main() -> None:
    args = parse_args()
    raw_dir = Path(args.raw_dir).resolve()
    output_dir = Path(args.output_dir).resolve()
    if not raw_dir.is_dir():
        raise FileNotFoundError(f"raw-dir not found: {raw_dir}")

    train_speakers = parse_csv_set(args.train_speakers)
    val_speakers = parse_csv_set(args.val_speakers)
    test_speakers = parse_csv_set(args.test_speakers)
    overlap = (train_speakers & val_speakers) | (train_speakers & test_speakers) | (val_speakers & test_speakers)
    if overlap:
        raise ValueError(f"speaker split overlap detected: {sorted(overlap)}")

    label_map = PRESET_MAP[args.preset]
    rows_by_split: dict[str, list[Row]] = {"train": [], "val": [], "test": []}
    dropped = Counter()
    source_count = Counter()
    target_count = Counter()
    split_count = Counter()

    speaker_dirs = sorted([p for p in raw_dir.iterdir() if p.is_dir()], key=lambda p: p.name.lower())
    if not speaker_dirs:
        raise ValueError(f"no speaker dirs found under {raw_dir}")

    for speaker_dir in speaker_dirs:
        speaker = speaker_dir.name
        if speaker in train_speakers:
            split = "train"
        elif speaker in val_speakers:
            split = "val"
        elif speaker in test_speakers:
            split = "test"
        else:
            dropped["unassigned_speaker"] += 1
            continue

        for emotion_dir in sorted([p for p in speaker_dir.iterdir() if p.is_dir()], key=lambda p: p.name.lower()):
            source_label = emotion_dir.name.lower().strip()
            if source_label not in label_map:
                dropped[f"label_{source_label}"] += 1
                continue

            target = label_map[source_label]
            wavs = sorted(emotion_dir.glob("*.wav"))
            if not wavs:
                continue
            for wav_path in wavs:
                duration_sec = read_duration(wav_path)
                if duration_sec < args.min_duration_sec:
                    dropped["too_short"] += 1
                    continue
                rows_by_split[split].append(
                    Row(
                        path=str(wav_path.resolve()),
                        label=target.upper(),
                        source_label=source_label,
                        duration_sec=duration_sec,
                        speaker=speaker,
                        file_id=wav_path.stem,
                    )
                )
                source_count[source_label] += 1
                target_count[target] += 1
                split_count[split] += 1

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
        "split_speakers": {
            "train": sorted(train_speakers),
            "val": sorted(val_speakers),
            "test": sorted(test_speakers),
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

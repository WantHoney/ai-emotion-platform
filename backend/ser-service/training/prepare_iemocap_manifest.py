import argparse
import csv
import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

import soundfile as sf


EVAL_LINE_RE = re.compile(
    r"^\[(?P<start>\d+(?:\.\d+)?)\s*-\s*(?P<end>\d+(?:\.\d+)?)\]\s+"
    r"(?P<utt>[A-Za-z0-9_]+)\s+"
    r"(?P<emo>[a-zA-Z]+)\s+"
    r"\["
)


PRESET_MAP = {
    # Recommended production preset: balanced enough and widely comparable.
    "4class": {
        "ang": "ang",
        "hap": "hap",
        "exc": "hap",
        "sad": "sad",
        "neu": "neu",
    },
    # Optional richer preset with frustration.
    "5class_fru": {
        "ang": "ang",
        "hap": "hap",
        "exc": "hap",
        "sad": "sad",
        "neu": "neu",
        "fru": "fru",
    },
}


@dataclass(frozen=True)
class Row:
    path: str
    label: str
    source_label: str
    duration_sec: float
    session: str
    dialog_id: str
    utterance_id: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build train/val/test manifests from raw IEMOCAP annotations."
    )
    parser.add_argument("--raw-dir", required=True, help="IEMOCAP root (contains Session1..Session5).")
    parser.add_argument("--output-dir", required=True, help="Output manifest directory.")
    parser.add_argument(
        "--preset",
        default="4class",
        choices=sorted(PRESET_MAP.keys()),
        help="Label preset to use.",
    )
    parser.add_argument(
        "--train-sessions",
        default="Session1,Session2,Session3",
        help="Comma-separated sessions for training split.",
    )
    parser.add_argument(
        "--val-sessions",
        default="Session4",
        help="Comma-separated sessions for validation split.",
    )
    parser.add_argument(
        "--test-sessions",
        default="Session5",
        help="Comma-separated sessions for test split.",
    )
    parser.add_argument(
        "--min-duration-sec",
        type=float,
        default=0.2,
        help="Drop too-short utterances.",
    )
    return parser.parse_args()


def parse_session_list(text: str) -> set[str]:
    return {x.strip() for x in text.split(",") if x.strip()}


def resolve_session_base(session_dir: Path) -> Path:
    direct = session_dir
    nested = session_dir / session_dir.name
    for candidate in (direct, nested):
        if (candidate / "dialog" / "EmoEvaluation").is_dir() and (candidate / "sentences" / "wav").is_dir():
            return candidate
    raise FileNotFoundError(f"cannot resolve IEMOCAP session base under {session_dir}")


def read_duration(path: Path) -> float:
    try:
        info = sf.info(str(path))
        if info.frames <= 0 or info.samplerate <= 0:
            return 0.0
        return float(info.frames) / float(info.samplerate)
    except Exception:
        return 0.0


def dialog_id_from_utterance(utt_id: str) -> str:
    parts = utt_id.split("_")
    if len(parts) <= 1:
        return utt_id
    return "_".join(parts[:-1])


def build_utterance_wav_index(sentences_wav_dir: Path) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for wav_path in sentences_wav_dir.rglob("*.wav"):
        index[wav_path.stem] = wav_path
    return index


def split_from_session(session: str, train_sessions: set[str], val_sessions: set[str], test_sessions: set[str]) -> str:
    if session in train_sessions:
        return "train"
    if session in val_sessions:
        return "val"
    if session in test_sessions:
        return "test"
    raise ValueError(f"session '{session}' not assigned to any split")


def write_manifest(path: Path, rows: list[Row]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["path", "label", "source_label", "duration_sec", "session", "dialog_id", "utterance_id"])
        for row in rows:
            writer.writerow(
                [
                    row.path,
                    row.label,
                    row.source_label,
                    f"{row.duration_sec:.6f}",
                    row.session,
                    row.dialog_id,
                    row.utterance_id,
                ]
            )


def main() -> None:
    args = parse_args()
    raw_dir = Path(args.raw_dir).resolve()
    output_dir = Path(args.output_dir).resolve()
    if not raw_dir.is_dir():
        raise FileNotFoundError(f"raw-dir not found: {raw_dir}")

    label_map = PRESET_MAP[args.preset]
    train_sessions = parse_session_list(args.train_sessions)
    val_sessions = parse_session_list(args.val_sessions)
    test_sessions = parse_session_list(args.test_sessions)

    overlap = (train_sessions & val_sessions) | (train_sessions & test_sessions) | (val_sessions & test_sessions)
    if overlap:
        raise ValueError(f"session split overlap detected: {sorted(overlap)}")

    rows_by_split: dict[str, list[Row]] = {"train": [], "val": [], "test": []}
    dropped_counter = Counter()
    source_counter = Counter()
    target_counter = Counter()
    split_counter = Counter()

    session_dirs = sorted([p for p in raw_dir.iterdir() if p.is_dir() and p.name.startswith("Session")], key=lambda p: p.name)
    if not session_dirs:
        raise ValueError(f"no Session* directories found under {raw_dir}")

    for session_dir in session_dirs:
        session_name = session_dir.name
        split = split_from_session(session_name, train_sessions, val_sessions, test_sessions)
        session_base = resolve_session_base(session_dir)
        emo_eval_dir = session_base / "dialog" / "EmoEvaluation"
        wav_index = build_utterance_wav_index(session_base / "sentences" / "wav")

        for eval_file in sorted(emo_eval_dir.glob("*.txt")):
            text = eval_file.read_text(encoding="utf-8", errors="ignore")
            for line in text.splitlines():
                line = line.strip()
                if not line or line.startswith("#") or line.startswith("["):
                    # Keep candidate lines only if regex can parse them.
                    pass
                match = EVAL_LINE_RE.match(line)
                if not match:
                    continue

                utt_id = match.group("utt")
                emo = match.group("emo").lower().strip()
                if emo not in label_map:
                    dropped_counter[f"label_{emo}"] += 1
                    continue

                wav_path = wav_index.get(utt_id)
                if wav_path is None:
                    dropped_counter["missing_wav"] += 1
                    continue

                duration_sec = read_duration(wav_path)
                if duration_sec < args.min_duration_sec:
                    dropped_counter["too_short"] += 1
                    continue

                target_label = label_map[emo]
                source_counter[emo] += 1
                target_counter[target_label] += 1
                split_counter[split] += 1

                rows_by_split[split].append(
                    Row(
                        path=str(wav_path.resolve()),
                        label=target_label.upper(),
                        source_label=emo,
                        duration_sec=duration_sec,
                        session=session_name,
                        dialog_id=dialog_id_from_utterance(utt_id),
                        utterance_id=utt_id,
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
        "split_sessions": {
            "train": sorted(train_sessions),
            "val": sorted(val_sessions),
            "test": sorted(test_sessions),
        },
        "counts": {
            "total": int(sum(split_counter.values())),
            "split": dict(split_counter),
            "source_label": dict(source_counter),
            "target_label": dict(target_counter),
            "dropped": dict(dropped_counter),
        },
        "split_label_counts": split_label_counts,
    }

    with (output_dir / "summary.json").open("w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

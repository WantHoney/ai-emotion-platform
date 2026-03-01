import argparse
import csv
import hashlib
import json
import random
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

import soundfile as sf


PRESET_MAP = {
    "4class": {
        "angry": "ang",
        "happy": "hap",
        "neutral": "neu",
        "sad": "sad",
    }
}

SUPPORTED_EXT = {".wav", ".mp3", ".m4a", ".flac", ".ogg", ".webm"}

EMOTION_ALIASES = {
    "angry": {"angry", "anger", "ang", "mad"},
    "happy": {"happy", "happiness", "hap", "joy", "exc", "excited", "cheerful"},
    "neutral": {"neutral", "neu", "calm"},
    "sad": {"sad", "sadness"},
    "surprise": {"surprise", "surprised", "sur"},
    "fear": {"fear", "fearful", "fea"},
    "disgust": {"disgust", "dis"},
}

SPLIT_ALIASES = {
    "train": {"train", "training", "trn"},
    "val": {"val", "valid", "validation", "dev"},
    "test": {"test", "testing", "tst", "eval", "evaluation"},
}

SPLIT_ORDER = ("train", "val", "test")

SPEAKER_ID_RE = re.compile(r"^(?:speaker|spk|id|p)?[_-]?(?P<id>\d{1,4})$", re.IGNORECASE)
TOKEN_SPLIT_RE = re.compile(r"[_\-\s\.]+")
TEXT_LINE_SPLIT_RE = re.compile(r"[\t|]")


@dataclass(frozen=True)
class ParsedSample:
    path: Path
    rel_path: str
    source_label: str | None
    label_source: str
    speaker: str | None
    text: str
    text_source: str
    duration_sec: float
    top_level_split: str | None


@dataclass(frozen=True)
class Row:
    path: str
    label: str
    source_label: str
    duration_sec: float
    speaker: str
    file_id: str
    text: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build train/val/test manifests from raw ESD corpus.")
    parser.add_argument("--raw-dir", required=True, help="ESD root directory.")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--preset", default="4class", choices=sorted(PRESET_MAP.keys()))
    parser.add_argument("--train-ratio", type=float, default=0.8)
    parser.add_argument("--val-ratio", type=float, default=0.1)
    parser.add_argument("--test-ratio", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--min-duration-sec", type=float, default=0.2)
    parser.add_argument("--extensions", default=",".join(sorted(SUPPORTED_EXT)))
    parser.add_argument("--force-speaker-split", action="store_true")
    parser.add_argument("--allow-unknown-speaker", action="store_true")
    return parser.parse_args()


def parse_extensions(text: str) -> set[str]:
    result: set[str] = set()
    for ext in text.split(","):
        item = ext.strip()
        if not item:
            continue
        result.add(("." + item.lstrip(".")).lower())
    if not result:
        return set(SUPPORTED_EXT)
    return result


def normalize_token(token: str) -> str:
    return token.strip().lower()


def token_candidates(path: Path) -> list[str]:
    tokens: list[str] = []
    for part in path.parts:
        for token in TOKEN_SPLIT_RE.split(part):
            t = normalize_token(token)
            if t:
                tokens.append(t)
    return tokens


def detect_emotion(rel_path: Path) -> tuple[str | None, str]:
    folder_parts = rel_path.parts[:-1]
    file_stem = Path(rel_path.name).stem

    for part in folder_parts:
        for token in TOKEN_SPLIT_RE.split(part):
            key = normalize_token(token)
            if not key:
                continue
            for emotion, aliases in EMOTION_ALIASES.items():
                if key in aliases:
                    return emotion, "folder"

    for token in TOKEN_SPLIT_RE.split(file_stem):
        key = normalize_token(token)
        if not key:
            continue
        for emotion, aliases in EMOTION_ALIASES.items():
            if key in aliases:
                return emotion, "filename"

    return None, "none"


def normalize_speaker(token: str) -> str | None:
    t = token.strip()
    if not t:
        return None
    m = SPEAKER_ID_RE.match(t)
    if m:
        return m.group("id")
    if t.isdigit() and 1 <= len(t) <= 4:
        return t
    return None


def detect_speaker(rel_path: Path, stem: str) -> str | None:
    parts = [p for p in rel_path.parts if p]
    for part in parts:
        norm = normalize_speaker(part)
        if norm is not None:
            return norm

    for token in TOKEN_SPLIT_RE.split(stem):
        norm = normalize_speaker(token)
        if norm is not None:
            return norm
    return None


def detect_top_level_split(rel_path: Path) -> str | None:
    if len(rel_path.parts) == 0:
        return None
    top = normalize_token(rel_path.parts[0])
    for split, aliases in SPLIT_ALIASES.items():
        if top in aliases:
            return split
    return None


def read_duration(path: Path) -> float:
    try:
        info = sf.info(str(path))
        if info.frames <= 0 or info.samplerate <= 0:
            return 0.0
        return float(info.frames) / float(info.samplerate)
    except Exception:
        return 0.0


def load_text_metadata(raw_dir: Path) -> dict[str, str]:
    mapping: dict[str, str] = {}
    for txt_path in raw_dir.rglob("*.txt"):
        if "__MACOSX" in txt_path.parts:
            continue
        if txt_path.name.startswith("._"):
            continue
        try:
            lines = txt_path.read_text(encoding="utf-8", errors="ignore").splitlines()
        except Exception:
            continue
        for line in lines:
            content = line.strip()
            if not content:
                continue
            parts = [p.strip() for p in TEXT_LINE_SPLIT_RE.split(content) if p.strip()]
            if len(parts) >= 2:
                key = Path(parts[0]).stem
                text = parts[1]
                if key and text and key not in mapping:
                    mapping[key] = text
                continue
            fields = content.split(maxsplit=1)
            if len(fields) == 2:
                key = Path(fields[0]).stem
                text = fields[1].strip()
                if key and text and key not in mapping:
                    mapping[key] = text
    return mapping


def read_sidecar_text(audio_path: Path, text_metadata: dict[str, str]) -> tuple[str, str]:
    for suffix in (".txt", ".lab", ".trn"):
        sidecar = audio_path.with_suffix(suffix)
        if sidecar.is_file():
            try:
                text = sidecar.read_text(encoding="utf-8", errors="ignore").strip()
                if text:
                    line = text.splitlines()[0].strip()
                    return line, "transcript_file"
            except Exception:
                continue
    text = text_metadata.get(audio_path.stem, "").strip()
    if text:
        return text, "metadata"
    return "", "none"


def detect_layout(samples: list[ParsedSample], official_split: bool) -> str:
    if official_split:
        return "B(split/speaker/emotion/file)"
    if not samples:
        return "unknown"
    speaker_like = sum(1 for s in samples if s.speaker and s.speaker != "unknown")
    if speaker_like >= max(1, int(len(samples) * 0.5)):
        return "A(speaker/emotion/file)"
    return "C(mixed_or_filename_driven)"


def list_audio_files(raw_dir: Path, extensions: set[str]) -> list[Path]:
    rows: list[Path] = []
    for path in raw_dir.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix.lower() not in extensions:
            continue
        # Common macOS unzip artifacts; not real audio payload.
        if "__MACOSX" in path.parts:
            continue
        if path.name.startswith("._"):
            continue
        if any(part.startswith("._") for part in path.parts):
            continue
        rows.append(path)
    return sorted(rows)


def split_speakers(
    speakers: list[str],
    train_ratio: float,
    val_ratio: float,
    test_ratio: float,
    seed: int,
) -> dict[str, str]:
    total = train_ratio + val_ratio + test_ratio
    if total <= 0.0:
        raise ValueError("train/val/test ratios sum must be > 0")
    if not speakers:
        return {}

    train_ratio /= total
    val_ratio /= total
    test_ratio /= total

    unique = sorted(set(speakers))
    rng = random.Random(seed)
    rng.shuffle(unique)
    n = len(unique)

    if n == 1:
        return {unique[0]: "train"}
    if n == 2:
        return {unique[0]: "train", unique[1]: "test"}

    n_train = int(round(n * train_ratio))
    n_val = int(round(n * val_ratio))
    n_test = n - n_train - n_val

    n_train = max(1, n_train)
    n_val = max(1, n_val)
    n_test = max(1, n_test)

    while n_train + n_val + n_test > n:
        if n_train >= n_val and n_train >= n_test and n_train > 1:
            n_train -= 1
        elif n_val >= n_train and n_val >= n_test and n_val > 1:
            n_val -= 1
        elif n_test > 1:
            n_test -= 1
        else:
            break
    while n_train + n_val + n_test < n:
        n_train += 1

    mapping: dict[str, str] = {}
    for idx, speaker in enumerate(unique):
        if idx < n_train:
            mapping[speaker] = "train"
        elif idx < n_train + n_val:
            mapping[speaker] = "val"
        else:
            mapping[speaker] = "test"
    return mapping


def stable_sha1_key(path: Path, seed: int) -> str:
    return hashlib.sha1(f"{seed}:{path.as_posix()}".encode("utf-8")).hexdigest()


def write_manifest(path: Path, rows: list[Row]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["path", "label", "source_label", "duration_sec", "speaker", "file_id", "text"])
        for row in rows:
            writer.writerow(
                [
                    row.path,
                    row.label,
                    row.source_label,
                    f"{row.duration_sec:.6f}",
                    row.speaker,
                    row.file_id,
                    row.text,
                ]
            )


def main() -> None:
    args = parse_args()
    raw_dir = Path(args.raw_dir).resolve()
    output_dir = Path(args.output_dir).resolve()
    if not raw_dir.is_dir():
        raise FileNotFoundError(f"raw-dir not found: {raw_dir}")

    extensions = parse_extensions(args.extensions)
    label_map = PRESET_MAP[args.preset]
    text_metadata = load_text_metadata(raw_dir)
    wav_paths = list_audio_files(raw_dir, extensions)
    if not wav_paths:
        raise ValueError(f"no audio files found under {raw_dir}")

    parsed: list[ParsedSample] = []
    dropped = Counter()
    split_hint_counter = Counter()

    for audio_path in wav_paths:
        rel = audio_path.relative_to(raw_dir)
        top_split = detect_top_level_split(rel)
        if top_split:
            split_hint_counter[top_split] += 1
        source_label, label_source = detect_emotion(rel)
        speaker = detect_speaker(rel, audio_path.stem)
        duration_sec = read_duration(audio_path)
        if duration_sec < args.min_duration_sec:
            dropped["too_short"] += 1
            continue
        text, text_source = read_sidecar_text(audio_path, text_metadata)
        parsed.append(
            ParsedSample(
                path=audio_path.resolve(),
                rel_path=str(rel.as_posix()),
                source_label=source_label,
                label_source=label_source,
                speaker=speaker,
                text=text,
                text_source=text_source,
                duration_sec=duration_sec,
                top_level_split=top_split,
            )
        )

    split_strategy = "speaker_independent"
    official_split = False
    if not args.force_speaker_split:
        official_split = (
            len(parsed) > 0
            and all(item.top_level_split in {"train", "val", "test"} for item in parsed)
            and all(split_hint_counter.get(split, 0) > 0 for split in SPLIT_ORDER)
        )
        if official_split:
            split_strategy = "official_split"

    rows_by_split: dict[str, list[Row]] = {k: [] for k in SPLIT_ORDER}
    source_counter = Counter()
    target_counter = Counter()
    speaker_counter = Counter()
    label_source_counter = Counter()
    text_source_counter = Counter()
    speaker_split: dict[str, str] = {}
    speaker_overlap: dict[str, list[str]] = {}

    if official_split:
        speakers_per_split: dict[str, set[str]] = defaultdict(set)
        for item in sorted(parsed, key=lambda x: stable_sha1_key(x.path, args.seed)):
            source = (item.source_label or "").strip().lower()
            if not source:
                dropped["label_unknown"] += 1
                continue
            if source not in label_map:
                dropped[f"label_{source}"] += 1
                continue

            speaker = item.speaker or "unknown"
            if speaker == "unknown" and not args.allow_unknown_speaker:
                dropped["unknown_speaker"] += 1
                continue

            split = item.top_level_split
            if split not in rows_by_split:
                dropped["split_unknown"] += 1
                continue

            row = Row(
                path=str(item.path),
                label=label_map[source].upper(),
                source_label=source,
                duration_sec=item.duration_sec,
                speaker=speaker,
                file_id=item.path.stem,
                text=item.text,
            )
            rows_by_split[split].append(row)
            source_counter[source] += 1
            target_counter[row.label] += 1
            speaker_counter[speaker] += 1
            label_source_counter[item.label_source] += 1
            text_source_counter[item.text_source] += 1
            if speaker != "unknown":
                speakers_per_split[split].add(speaker)

        for speaker in sorted(set().union(*speakers_per_split.values()) if speakers_per_split else set()):
            in_splits = [split for split in SPLIT_ORDER if speaker in speakers_per_split[split]]
            if len(in_splits) > 1:
                speaker_overlap[speaker] = in_splits

        if speaker_overlap:
            detail = ", ".join(f"{k}:{'/'.join(v)}" for k, v in sorted(speaker_overlap.items()))
            raise ValueError(f"official split speaker leakage detected: {detail}")
    else:
        known_speakers = sorted({item.speaker for item in parsed if item.speaker})
        if not known_speakers and not args.allow_unknown_speaker:
            raise ValueError("no speaker ids detected; retry with --allow-unknown-speaker")
        speaker_split = split_speakers(
            known_speakers if known_speakers else ["unknown"],
            train_ratio=args.train_ratio,
            val_ratio=args.val_ratio,
            test_ratio=args.test_ratio,
            seed=args.seed,
        )

        for item in sorted(parsed, key=lambda x: stable_sha1_key(x.path, args.seed)):
            source = (item.source_label or "").strip().lower()
            if not source:
                dropped["label_unknown"] += 1
                continue
            if source not in label_map:
                dropped[f"label_{source}"] += 1
                continue

            speaker = item.speaker or "unknown"
            if speaker == "unknown" and not args.allow_unknown_speaker:
                dropped["unknown_speaker"] += 1
                continue

            split = speaker_split.get(speaker, "train")
            row = Row(
                path=str(item.path),
                label=label_map[source].upper(),
                source_label=source,
                duration_sec=item.duration_sec,
                speaker=speaker,
                file_id=item.path.stem,
                text=item.text,
            )
            rows_by_split[split].append(row)
            source_counter[source] += 1
            target_counter[row.label] += 1
            speaker_counter[speaker] += 1
            label_source_counter[item.label_source] += 1
            text_source_counter[item.text_source] += 1

    for split in SPLIT_ORDER:
        write_manifest(output_dir / f"{split}.csv", rows_by_split[split])

    split_label_counts: dict[str, dict[str, int]] = {}
    split_speaker_counts: dict[str, int] = {}
    split_counts: dict[str, int] = {}
    for split, rows in rows_by_split.items():
        split_counts[split] = len(rows)
        split_label_counts[split] = dict(sorted(Counter(r.label for r in rows).items()))
        split_speaker_counts[split] = len({r.speaker for r in rows})

    detected_layout = detect_layout(parsed, official_split=official_split)
    label_source = max(label_source_counter, key=label_source_counter.get) if label_source_counter else "none"
    text_source = max(text_source_counter, key=text_source_counter.get) if text_source_counter else "none"

    summary = {
        "preset": args.preset,
        "raw_dir": str(raw_dir),
        "output_dir": str(output_dir),
        "split_strategy": split_strategy,
        "official_split_detected": official_split,
        "detected_layout": detected_layout,
        "label_source": label_source,
        "text_source": text_source,
        "counts": {
            "total": int(sum(split_counts.values())),
            "split": split_counts,
            "source_label": dict(sorted(source_counter.items())),
            "target_label": dict(sorted(target_counter.items())),
            "speaker_total": len(speaker_counter),
            "speaker_split": split_speaker_counts,
            "label_source": dict(sorted(label_source_counter.items())),
            "text_source": dict(sorted(text_source_counter.items())),
            "dropped": dict(sorted(dropped.items())),
        },
        "split_label_counts": split_label_counts,
        "speaker_split_map": {k: speaker_split[k] for k in sorted(speaker_split)} if speaker_split else {},
        "notes": {
            "surprise_behavior": "dropped in 4class preset",
            "speaker_overlap_check": "enabled for official split",
        },
    }
    with (output_dir / "summary.json").open("w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

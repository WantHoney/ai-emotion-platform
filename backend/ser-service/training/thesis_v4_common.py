from __future__ import annotations

import csv
import hashlib
import json
import os
import re
import subprocess
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Any


SERVICE_ROOT = Path(__file__).resolve().parent.parent
TRAINING_ROOT = SERVICE_ROOT / "training"
THESIS_V4_ROOT = TRAINING_ROOT / "manifests" / "thesis_v4"
CANDIDATE_STAGE_ROOT = THESIS_V4_ROOT / "candidate_stage"
INTAKE_ROOT = THESIS_V4_ROOT / "intake"
DEFERRED_TRAINING_ROOT = THESIS_V4_ROOT / "deferred_training"
REVIEW_ROOT = CANDIDATE_STAGE_ROOT / "review"
LOG_ROOT = SERVICE_ROOT / "logs"
TEACHER_ROOT = CANDIDATE_STAGE_ROOT / "teacher_runs"
REAL_POOL_ROOT = CANDIDATE_STAGE_ROOT / "real_world"
CONTROLLED_POOL_ROOT = CANDIDATE_STAGE_ROOT / "controlled_zh"
LOCAL_TEXT_ROOT = DEFERRED_TRAINING_ROOT / "local_text"
PROMPTS_ROOT = INTAKE_ROOT / "recording_prompts"
RECORDING_DROPBOX_ROOT = INTAKE_ROOT / "recording_dropbox"
REVIEW_MANIFEST_PATH = REVIEW_ROOT / "teacher_review_set.csv"
RECORDING_PROMPTS_JSON_PATH = PROMPTS_ROOT / "recording_prompts_v1.json"
RECORDING_PROMPTS_CSV_PATH = PROMPTS_ROOT / "recording_prompts_v1.csv"
REAL_WORLD_POOL_FREEZE_PATH = REAL_POOL_ROOT / "real_world_pool.freeze.json"
REAL_TEST_FREEZE_PATH = REAL_POOL_ROOT / "real_test.freeze.json"
CONTROLLED_POOL_FREEZE_PATH = CONTROLLED_POOL_ROOT / "controlled_zh_pool.freeze.json"
TEACHER_DIFF_PATH = LOG_ROOT / "teacher_diff_e2b_vs_e4b.json"
TEACHER_REQUIRED_FIELDS = (
    "sample_id",
    "label3",
    "scores3",
    "label4",
    "scores4",
    "teacher_confidence",
    "conflict_hint",
    "teacher_model",
    "prompt_version",
    "generated_at",
)
SUPPORTED_AUDIO_EXTENSIONS = {".wav", ".mp3", ".m4a", ".flac", ".ogg", ".webm"}
CANONICAL_EMOTIONS = ("ANG", "HAP", "NEU", "SAD")
DEFAULT_UPLOAD_ROOTS = (
    Path(os.getenv("THESIS_V4_UPLOADS_DIR", Path.home() / "ai-emotion" / "uploads")),
    SERVICE_ROOT.parent / "uploads",
)
DB_DEFAULTS = {
    "host": os.getenv("THESIS_V4_DB_HOST", "127.0.0.1"),
    "port": int(os.getenv("THESIS_V4_DB_PORT", "3306")),
    "user": os.getenv("THESIS_V4_DB_USER", "root"),
    "password": os.getenv("THESIS_V4_DB_PASSWORD", "1029384756Wh"),
    "database": os.getenv("THESIS_V4_DB_NAME", "ai_emotion"),
    "charset": "utf8mb4",
}
ASR_BASE_URL = os.getenv("THESIS_V4_SER_BASE_URL", "http://127.0.0.1:8001")
OLLAMA_BASE_URL = os.getenv("THESIS_V4_OLLAMA_BASE_URL", "http://127.0.0.1:11434")
TEXT_BASE_MODEL = os.getenv(
    "THESIS_V4_TEXT_BASE_MODEL",
    str((SERVICE_ROOT / "text_models" / "zh_roberta_sentiment").resolve()),
)


@dataclass(frozen=True)
class SampleRecord:
    sample_id: str
    source: str
    source_kind: str
    path: str
    original_name: str
    speaker: str
    sha256: str | None
    size_bytes: int | None
    duration_ms: int | None
    transcript: str
    transcript_norm: str
    language: str | None
    human_label: str | None
    heuristic_label: str | None
    heuristic_score: float | None
    is_key_hard_case: bool
    metadata: dict[str, Any]


def ensure_dirs() -> None:
    for path in (
        THESIS_V4_ROOT,
        CANDIDATE_STAGE_ROOT,
        REVIEW_ROOT,
        TEACHER_ROOT,
        REAL_POOL_ROOT,
        CONTROLLED_POOL_ROOT,
        INTAKE_ROOT,
        PROMPTS_ROOT,
        RECORDING_DROPBOX_ROOT,
        LOCAL_TEXT_ROOT,
        LOG_ROOT,
    ):
        path.mkdir(parents=True, exist_ok=True)


def now_iso() -> str:
    return datetime.now().isoformat(timespec="seconds")


def read_json(path: Path, default: Any = None) -> Any:
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False))
            handle.write("\n")


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            payload = line.strip()
            if payload:
                rows.append(json.loads(payload))
    return rows


def write_csv(path: Path, rows: list[dict[str, Any]], fieldnames: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row.get(key) for key in fieldnames})


def count_csv_data_rows(path: Path) -> int | None:
    if not path.exists():
        return None
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        return sum(1 for _ in reader)


def load_terms(path: Path) -> tuple[str, ...]:
    if not path.exists():
        return ()
    terms: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        value = line.strip()
        if value and not value.startswith("#"):
            terms.append(value)
    return tuple(terms)


def normalize_whitespace(text: str) -> str:
    return re.sub(r"\s+", " ", (text or "").replace("\r", " ").replace("\n", " ")).strip()


def normalize_transcript(text: str) -> str:
    normalized = normalize_whitespace(text)
    normalized = re.sub(r"[，。、“”‘’！？!?,.；;：:\-—\(\)\[\]{}<>《》\"'`~]+", "", normalized)
    return normalized.strip().lower()


def contains_chinese(text: str) -> bool:
    return bool(re.search(r"[\u4e00-\u9fff]", text or ""))


def chinese_ratio(text: str) -> float:
    text = text or ""
    meaningful = [ch for ch in text if ch.isalnum() or ("\u4e00" <= ch <= "\u9fff")]
    if not meaningful:
        return 0.0
    zh_count = sum(1 for ch in meaningful if "\u4e00" <= ch <= "\u9fff")
    return zh_count / max(len(meaningful), 1)


def detect_language(text: str) -> str | None:
    if not text:
        return None
    ratio = chinese_ratio(text)
    if ratio >= 0.25:
        return "zh"
    if re.search(r"[A-Za-z]", text):
        return "en"
    return None


def canonical_emotion(label: str | None) -> str | None:
    if label is None:
        return None
    key = str(label).strip().upper()
    if key in {"ANG", "ANGRY"}:
        return "ANG"
    if key in {"HAP", "HAPPY", "EXC"}:
        return "HAP"
    if key in {"NEU", "NEUTRAL", "CALM"}:
        return "NEU"
    if key in {"SAD", "SADNESS"}:
        return "SAD"
    return key or None


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(value)
    except Exception:
        try:
            return int(float(value))
        except Exception:
            return None


def unique_preserve_order(values: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        out.append(value)
    return out


def load_unique_samples_from_manifests(manifest_paths: list[Path]) -> list["SampleRecord"]:
    deduped: dict[str, SampleRecord] = {}
    for manifest_path in manifest_paths:
        for sample in read_samples_csv(manifest_path):
            deduped[sample.sample_id] = sample
    return list(deduped.values())


def expected_sample_ids_from_manifests(manifest_paths: list[Path]) -> list[str]:
    samples = load_unique_samples_from_manifests(manifest_paths)
    return sorted(sample.sample_id for sample in samples)


def validate_teacher_artifact(
    jsonl_path: Path,
    expected_sample_ids: list[str],
) -> dict[str, Any]:
    csv_path = jsonl_path.with_suffix(".csv")
    rows = read_jsonl(jsonl_path)
    csv_rows = count_csv_data_rows(csv_path)
    seen: set[str] = set()
    duplicate_sample_ids: list[str] = []
    missing_required_fields: list[dict[str, Any]] = []
    row_by_id: dict[str, dict[str, Any]] = {}

    for row in rows:
        sample_id = str(row.get("sample_id") or "").strip()
        if not sample_id:
            missing_required_fields.append({"sample_id": None, "fields": ["sample_id"]})
            continue
        if sample_id in seen:
            duplicate_sample_ids.append(sample_id)
        seen.add(sample_id)

        missing_fields = [field for field in TEACHER_REQUIRED_FIELDS if row.get(field) in (None, "")]
        if missing_fields:
            missing_required_fields.append({"sample_id": sample_id, "fields": missing_fields})
        row_by_id[sample_id] = row

    expected_set = set(expected_sample_ids)
    actual_ids = sorted(row_by_id.keys())
    missing_sample_ids = sorted(expected_set - set(actual_ids))
    extra_sample_ids = sorted(set(actual_ids) - expected_set)
    row_count = len(rows)
    unique_row_count = len(row_by_id)
    csv_row_count_matches = csv_rows == row_count if csv_rows is not None else False
    is_complete = (
        row_count == len(expected_sample_ids)
        and unique_row_count == len(expected_sample_ids)
        and csv_row_count_matches
        and not missing_sample_ids
        and not extra_sample_ids
        and not duplicate_sample_ids
        and not missing_required_fields
    )
    return {
        "jsonlPath": str(jsonl_path.resolve()),
        "csvPath": str(csv_path.resolve()),
        "expectedCount": len(expected_sample_ids),
        "jsonlCount": row_count,
        "csvCount": csv_rows,
        "uniqueSampleCount": unique_row_count,
        "duplicateSampleIds": unique_preserve_order(duplicate_sample_ids),
        "missingSampleIds": missing_sample_ids,
        "extraSampleIds": extra_sample_ids,
        "missingRequiredFields": missing_required_fields,
        "requiredFields": list(TEACHER_REQUIRED_FIELDS),
        "csvRowCountMatches": csv_row_count_matches,
        "isComplete": is_complete,
        "rows": rows,
        "rowById": row_by_id,
    }


def find_bundled_ffmpeg() -> str | None:
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return None


def probe_duration_ms(path: Path) -> int | None:
    ffmpeg = find_bundled_ffmpeg()
    if not ffmpeg:
        return None
    command = [ffmpeg, "-i", str(path), "-f", "null", "-"]
    try:
        result = subprocess.run(command, capture_output=True, text=True, check=False, timeout=30)
    except Exception:
        return None
    text = (result.stderr or "") + "\n" + (result.stdout or "")
    match = re.search(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)", text)
    if not match:
        return None
    hours = int(match.group(1))
    minutes = int(match.group(2))
    seconds = float(match.group(3))
    return int(round((hours * 3600 + minutes * 60 + seconds) * 1000))


def encode_multipart_formdata(fields: dict[str, str], files: list[tuple[str, str, bytes]]) -> tuple[bytes, str]:
    boundary = "----CodexThesisV4Boundary"
    lines: list[bytes] = []
    for name, value in fields.items():
        lines.append(f"--{boundary}".encode("utf-8"))
        lines.append(f'Content-Disposition: form-data; name="{name}"'.encode("utf-8"))
        lines.append(b"")
        lines.append(str(value).encode("utf-8"))
    for field_name, filename, content in files:
        lines.append(f"--{boundary}".encode("utf-8"))
        lines.append(
            f'Content-Disposition: form-data; name="{field_name}"; filename="{filename}"'.encode("utf-8")
        )
        lines.append(b"Content-Type: application/octet-stream")
        lines.append(b"")
        lines.append(content)
    lines.append(f"--{boundary}--".encode("utf-8"))
    lines.append(b"")
    body = b"\r\n".join(lines)
    return body, boundary


def transcribe_via_ser(path: Path, timeout_seconds: int = 180) -> tuple[str, str | None, str | None]:
    file_bytes = path.read_bytes()
    body, boundary = encode_multipart_formdata({}, [("file", path.name, file_bytes)])
    request = urllib.request.Request(
        url=f"{ASR_BASE_URL.rstrip('/')}/asr/transcribe",
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            payload = json.loads(response.read().decode("utf-8"))
            transcript = normalize_whitespace(str(payload.get("text") or ""))
            language = payload.get("language")
            return transcript, language, None
    except urllib.error.HTTPError as exc:
        return "", None, f"HTTP {exc.code}"
    except Exception as exc:
        return "", None, str(exc)


def ollama_chat_json(
    model: str,
    system_prompt: str,
    user_prompt: str,
    temperature: float,
    timeout_seconds: int = 180,
) -> dict[str, Any]:
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "stream": False,
        "format": "json",
        "options": {"temperature": float(temperature)},
    }
    request = urllib.request.Request(
        url=f"{OLLAMA_BASE_URL.rstrip('/')}/api/chat",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        body = json.loads(response.read().decode("utf-8"))
    message = ((body or {}).get("message") or {}).get("content") or ""
    return json.loads(message)


def ollama_model_exists(model: str) -> bool:
    try:
        result = subprocess.run(["ollama", "list"], capture_output=True, text=True, check=False, timeout=30)
    except Exception:
        return False
    if result.returncode != 0:
        return False
    lines = [line.strip() for line in (result.stdout or "").splitlines() if line.strip()]
    for line in lines[1:]:
        if line.split()[0] == model:
            return True
    return False


def heuristic_semantic_scores(text: str, lexicon_dir: Path) -> dict[str, Any]:
    positive_terms = load_terms(lexicon_dir / "text_positive_zh.txt")
    anger_terms = load_terms(lexicon_dir / "text_anger_zh.txt")
    sad_terms = load_terms(lexicon_dir / "text_sad_zh.txt")
    negative_terms = load_terms(lexicon_dir / "text_negative_zh.txt")
    neutral_cues = load_terms(lexicon_dir / "text_neutral_cues_zh.txt")
    payload = normalize_whitespace(text)
    positive_hits = [term for term in positive_terms if term in payload and f"不{term}" not in payload]
    anger_hits = [term for term in anger_terms if term in payload]
    sad_hits = [term for term in sad_terms if term in payload]
    negative_hits = [term for term in negative_terms if term in payload]
    neutral_hits = [term for term in neutral_cues if term in payload]

    raw = {
        "ANG": float(len(anger_hits) * 2 + sum(1 for term in negative_hits if term in {"愤怒", "生气", "气死", "讨厌"})),
        "HAP": float(len(positive_hits) * 2),
        "NEU": 1.0 if not (positive_hits or anger_hits or sad_hits or negative_hits) else 0.0,
        "SAD": float(len(sad_hits) * 2 + len(negative_hits)),
    }

    if neutral_hits:
        raw["NEU"] += 1.5
    if positive_hits and not anger_hits and not sad_hits and not negative_hits:
        raw["HAP"] += 0.6
    if not any(raw.values()):
        raw["NEU"] = 1.0

    total = sum(raw.values())
    scores = {label: value / total for label, value in raw.items()}
    label = max(scores, key=scores.get)
    return {
        "label": label,
        "score": float(scores[label]),
        "scores": scores,
        "positiveHits": positive_hits,
        "angerHits": anger_hits,
        "sadHits": sad_hits,
        "negativeHits": negative_hits,
        "neutralHits": neutral_hits,
    }


def sample_to_row(sample: SampleRecord) -> dict[str, Any]:
    row = asdict(sample)
    row["metadata"] = json.dumps(sample.metadata, ensure_ascii=False)
    return row


def row_to_sample(row: dict[str, str]) -> SampleRecord:
    metadata = row.get("metadata") or "{}"
    try:
        parsed_metadata = json.loads(metadata)
    except Exception:
        parsed_metadata = {"rawMetadata": metadata}
    return SampleRecord(
        sample_id=row.get("sample_id", ""),
        source=row.get("source", ""),
        source_kind=row.get("source_kind", ""),
        path=row.get("path", ""),
        original_name=row.get("original_name", ""),
        speaker=row.get("speaker", ""),
        sha256=row.get("sha256") or None,
        size_bytes=safe_int(row.get("size_bytes")),
        duration_ms=safe_int(row.get("duration_ms")),
        transcript=row.get("transcript", ""),
        transcript_norm=row.get("transcript_norm", ""),
        language=row.get("language") or None,
        human_label=row.get("human_label") or None,
        heuristic_label=row.get("heuristic_label") or None,
        heuristic_score=None if not row.get("heuristic_score") else float(row["heuristic_score"]),
        is_key_hard_case=str(row.get("is_key_hard_case", "")).strip().lower() in {"1", "true", "yes"},
        metadata=parsed_metadata,
    )


def read_samples_csv(path: Path) -> list[SampleRecord]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        return [row_to_sample(row) for row in reader]


def write_samples_csv(path: Path, samples: list[SampleRecord]) -> None:
    fieldnames = [
        "sample_id",
        "source",
        "source_kind",
        "path",
        "original_name",
        "speaker",
        "sha256",
        "size_bytes",
        "duration_ms",
        "transcript",
        "transcript_norm",
        "language",
        "human_label",
        "heuristic_label",
        "heuristic_score",
        "is_key_hard_case",
        "metadata",
    ]
    write_csv(path, [sample_to_row(sample) for sample in samples], fieldnames)

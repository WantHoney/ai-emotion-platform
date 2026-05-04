from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import pymysql
from pymysql.cursors import DictCursor

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from thesis_v4_common import (  # noqa: E402
    ASR_BASE_URL,
    CONTROLLED_POOL_ROOT,
    DB_DEFAULTS,
    DEFAULT_UPLOAD_ROOTS,
    LOG_ROOT,
    REAL_POOL_ROOT,
    RECORDING_DROPBOX_ROOT,
    RECORDING_PROMPTS_CSV_PATH,
    RECORDING_PROMPTS_JSON_PATH,
    REVIEW_MANIFEST_PATH,
    SERVICE_ROOT,
    SampleRecord,
    canonical_emotion,
    contains_chinese,
    detect_language,
    ensure_dirs,
    heuristic_semantic_scores,
    normalize_transcript,
    normalize_whitespace,
    now_iso,
    probe_duration_ms,
    read_json,
    sha256_file,
    transcribe_via_ser,
    write_csv,
    write_json,
    write_samples_csv,
)


REVIEW_SEED_RULES = [
    {"kind": "real_contains", "match": "非常的开心", "humanLabel": "HAP", "hardCase": True, "notes": "正向困难样本"},
    {"kind": "real_contains", "match": "非常不開心", "humanLabel": "ANG", "hardCase": False, "notes": "明显负向且带愤怒词"},
    {"kind": "real_contains", "match": "我现在是什么情绪呢", "humanLabel": "NEU", "hardCase": True, "notes": "测试问句中性困难样本"},
    {"kind": "real_contains", "match": "就是下雨也去", "humanLabel": "NEU", "hardCase": False, "notes": "短句客观表达"},
    {"kind": "controlled_text", "match": "我曾经养过，我太高兴了。", "humanLabel": "HAP", "hardCase": False, "notes": "明显正向"},
    {"kind": "controlled_text", "match": "祝你春节快乐，全家幸福安康。", "humanLabel": "HAP", "hardCase": False, "notes": "明显正向祝福句"},
    {"kind": "controlled_text", "match": "那棒极了，其实心情挺不错。", "humanLabel": "HAP", "hardCase": False, "notes": "明显正向轻松表达"},
    {"kind": "controlled_text", "match": "资料全都不见了。气死我了。", "humanLabel": "ANG", "hardCase": False, "notes": "明显愤怒"},
    {"kind": "controlled_text", "match": "软妹子生气会说：讨厌，不理你啦。", "humanLabel": "ANG", "hardCase": False, "notes": "明显愤怒语义"},
    {"kind": "controlled_text", "match": "我很伤心，我准备去网吧。", "humanLabel": "SAD", "hardCase": False, "notes": "明显悲伤"},
    {"kind": "controlled_text", "match": "每次叫我都不给红包，不开心。", "humanLabel": "SAD", "hardCase": True, "notes": "否定正向词困难样本"},
    {"kind": "controlled_text", "match": "还不太糟糕，但是得躺在床上。", "humanLabel": "SAD", "hardCase": True, "notes": "轻度负向困难样本"},
    {"kind": "controlled_text", "match": "听说你要去香港看你叔叔。", "humanLabel": "NEU", "hardCase": False, "notes": "客观陈述"},
    {"kind": "controlled_text", "match": "我要学习一下相关知识。", "humanLabel": "NEU", "hardCase": False, "notes": "客观陈述"},
]

RECORDING_PROMPTS = [
    {"promptId": "hap_01", "emotion": "HAP", "text": "今天真的很开心，事情都进展得特别顺利。"},
    {"promptId": "hap_02", "emotion": "HAP", "text": "我刚刚收到一个好消息，心情一下就放松了。"},
    {"promptId": "hap_03", "emotion": "HAP", "text": "天气这么好，出去走一走真的很舒服。"},
    {"promptId": "hap_04", "emotion": "HAP", "text": "终于把这个任务做完了，我现在特别轻松。"},
    {"promptId": "hap_05", "emotion": "HAP", "text": "今天和朋友聊天聊得很开心，整个人都精神了。"},
    {"promptId": "neu_01", "emotion": "NEU", "text": "我现在在做一段情绪识别的测试录音。"},
    {"promptId": "neu_02", "emotion": "NEU", "text": "今天下午我要先开会，然后再整理材料。"},
    {"promptId": "neu_03", "emotion": "NEU", "text": "现在时间差不多了，我们可以开始下一项任务。"},
    {"promptId": "neu_04", "emotion": "NEU", "text": "我把文件放在桌面上了，等会儿再处理。"},
    {"promptId": "neu_05", "emotion": "NEU", "text": "这只是一次普通的语音测试，没有特别的情绪表达。"},
    {"promptId": "ang_01", "emotion": "ANG", "text": "这件事反复出问题，我现在真的很生气。"},
    {"promptId": "ang_02", "emotion": "ANG", "text": "资料怎么又不见了，真是气死我了。"},
    {"promptId": "ang_03", "emotion": "ANG", "text": "我已经说过很多次了，怎么还是没有改。"},
    {"promptId": "ang_04", "emotion": "ANG", "text": "这次的处理方式太不合理了，我很不满意。"},
    {"promptId": "ang_05", "emotion": "ANG", "text": "我现在非常恼火，真的一点都高兴不起来。"},
    {"promptId": "sad_01", "emotion": "SAD", "text": "我这几天一直很难过，心里有点压着。"},
    {"promptId": "sad_02", "emotion": "SAD", "text": "想到那件事我还是会觉得失落。"},
    {"promptId": "sad_03", "emotion": "SAD", "text": "最近状态不太好，总觉得提不起精神。"},
    {"promptId": "sad_04", "emotion": "SAD", "text": "我有点委屈，也不知道该怎么说。"},
    {"promptId": "sad_05", "emotion": "SAD", "text": "今天没有发生特别糟糕的事，但我还是有些低落。"},
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build thesis v4 dedup audit, pools, review set, and recording prompts.")
    parser.add_argument("--uploads-dir", action="append", default=[], help="Extra uploads directory. Can be passed multiple times.")
    parser.add_argument("--db-host", default=DB_DEFAULTS["host"])
    parser.add_argument("--db-port", type=int, default=DB_DEFAULTS["port"])
    parser.add_argument("--db-user", default=DB_DEFAULTS["user"])
    parser.add_argument("--db-password", default=DB_DEFAULTS["password"])
    parser.add_argument("--db-name", default=DB_DEFAULTS["database"])
    parser.add_argument("--controlled-candidates-limit", type=int, default=160)
    parser.add_argument("--review-size-min", type=int, default=12)
    parser.add_argument("--review-size-max", type=int, default=16)
    parser.add_argument("--real-test-target", type=int, default=0, help="0 means do not pre-freeze real_test yet.")
    parser.add_argument("--real-max-duration-ms", type=int, default=90000)
    parser.add_argument("--tiny-file-bytes", type=int, default=128)
    parser.add_argument("--skip-asr", action="store_true")
    return parser.parse_args()


def fetch_db_rows(args: argparse.Namespace) -> list[dict[str, Any]]:
    connection = pymysql.connect(
        host=args.db_host,
        port=args.db_port,
        user=args.db_user,
        password=args.db_password,
        database=args.db_name,
        charset="utf8mb4",
        cursorclass=DictCursor,
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT
                    af.id AS audio_id,
                    af.original_name,
                    af.stored_name,
                    af.storage_path,
                    af.content_type,
                    af.size_bytes,
                    af.sha256,
                    af.duration_ms,
                    af.status,
                    af.created_at,
                    atask.id AS task_id,
                    atask.status AS task_status,
                    ar.overall_emotion_code,
                    ar.overall_confidence,
                    ar.raw_json
                FROM audio_file af
                LEFT JOIN analysis_task atask ON atask.audio_file_id = af.id
                LEFT JOIN analysis_result ar ON ar.task_id = atask.id
                ORDER BY af.id ASC, atask.id ASC
                """
            )
            rows = cursor.fetchall()
    finally:
        connection.close()
    grouped: dict[int, dict[str, Any]] = {}
    for row in rows:
        audio_id = int(row["audio_id"])
        current = grouped.setdefault(audio_id, dict(row))
        task_id = row.get("task_id")
        if task_id is not None and (current.get("task_id") is None or int(task_id) > int(current["task_id"])):
            current.update(dict(row))
    return list(grouped.values())


def extract_transcript(raw_json: Any) -> str:
    payload = raw_json
    if isinstance(payload, str):
        try:
            payload = json.loads(payload)
        except Exception:
            payload = {}
    payload = payload or {}
    asr = payload.get("asr") or {}
    transcript = payload.get("transcript") or asr.get("text") or ""
    return normalize_whitespace(str(transcript))


def build_real_sources(args: argparse.Namespace, db_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    uploads_roots = [Path(item).resolve() for item in args.uploads_dir]
    for root in DEFAULT_UPLOAD_ROOTS:
        root = Path(root).resolve()
        if root not in uploads_roots:
            uploads_roots.append(root)
    intake_root = RECORDING_DROPBOX_ROOT.resolve()

    sources: list[dict[str, Any]] = []
    seen_paths: set[str] = set()
    for row in db_rows:
        storage_path = (row.get("storage_path") or "").strip()
        if storage_path:
            path = Path(storage_path)
            key = str(path.resolve()) if path.exists() else str(path)
            seen_paths.add(key)
            sources.append(
                {
                    "source": "db_audio_file",
                    "path": path,
                    "originalName": row.get("original_name") or path.name,
                    "dbRow": row,
                }
            )

    for root in uploads_roots:
        if not root.exists():
            continue
        for path in sorted(root.iterdir()):
            if not path.is_file():
                continue
            key = str(path.resolve())
            if key in seen_paths:
                continue
            seen_paths.add(key)
            sources.append(
                {
                    "source": "uploads_scan",
                    "path": path,
                    "originalName": path.name,
                    "dbRow": None,
                }
            )
    if intake_root.exists():
        for path in sorted(intake_root.rglob("*")):
            if not path.is_file():
                continue
            key = str(path.resolve())
            if key in seen_paths:
                continue
            seen_paths.add(key)
            sources.append(
                {
                    "source": "intake_recording",
                    "path": path,
                    "originalName": path.name,
                    "dbRow": None,
                }
            )
    return sources


def detect_intake_emotion(path: Path) -> str | None:
    try:
        relative_parts = path.resolve().relative_to(RECORDING_DROPBOX_ROOT.resolve()).parts
    except Exception:
        return None
    if not relative_parts:
        return None
    folder = canonical_emotion(relative_parts[0])
    if folder in {"ANG", "HAP", "NEU", "SAD"}:
        return folder
    return None


def classify_real_source(item: dict[str, Any], args: argparse.Namespace) -> tuple[SampleRecord | None, dict[str, Any]]:
    path: Path = item["path"]
    db_row = item.get("dbRow")
    audit = {"path": str(path), "source": item["source"], "exists": path.exists(), "reasons": []}
    if not path.exists():
        audit["reasons"].append("missing_file")
        return None, audit
    if path.suffix.lower() not in {".wav", ".mp3", ".m4a", ".flac", ".ogg", ".webm"}:
        audit["reasons"].append("unsupported_extension")
        return None, audit
    size_bytes = int(path.stat().st_size)
    audit["sizeBytes"] = size_bytes
    if size_bytes <= args.tiny_file_bytes:
        audit["reasons"].append("tiny_or_corrupt_file")
        return None, audit

    sha256 = (db_row or {}).get("sha256") or sha256_file(path)
    transcript = extract_transcript((db_row or {}).get("raw_json"))
    asr_error = None
    asr_language = None
    if not transcript and not args.skip_asr:
        transcript, asr_language, asr_error = transcribe_via_ser(path)
    transcript = normalize_whitespace(transcript)
    duration_ms = (db_row or {}).get("duration_ms")
    if duration_ms is None:
        duration_ms = probe_duration_ms(path)
    duration_ms = int(duration_ms) if duration_ms not in (None, "") else None
    language = detect_language(transcript)
    transcript_norm = normalize_transcript(transcript)

    audit["sha256"] = sha256
    audit["durationMs"] = duration_ms
    audit["transcript"] = transcript
    audit["language"] = language
    if asr_language and not language:
        language = asr_language
    if asr_error:
        audit["asrError"] = asr_error
    if asr_language:
        audit["asrLanguage"] = asr_language

    if not transcript:
        audit["reasons"].append("empty_transcript")
        return None, audit
    if size_bytes >= 5_000_000:
        audit["reasons"].append("likely_long_music_or_non_target")
    if duration_ms is not None and duration_ms > args.real_max_duration_ms:
        audit["reasons"].append("too_long_for_real_speech_pool")
    if duration_ms is None and size_bytes >= 1_000_000 and len(transcript) >= 80:
        audit["reasons"].append("likely_long_music_or_non_target")
    if language != "zh":
        audit["reasons"].append("non_chinese_transcript")
    if len(transcript_norm) <= 1:
        audit["reasons"].append("too_short_transcript")
    if re.search(r"(青花瓷|周杰伦)", transcript):
        audit["reasons"].append("likely_music_or_lyrics")
    if audit["reasons"]:
        return None, audit

    heuristic = heuristic_semantic_scores(transcript, SERVICE_ROOT / "lexicon")
    intake_emotion = detect_intake_emotion(path)
    source_kind = "intake_recording" if intake_emotion else item["source"]
    sample = SampleRecord(
        sample_id=f"real_{sha256[:12]}",
        source="real_world",
        source_kind=source_kind,
        path=str(path.resolve()),
        original_name=item["originalName"],
        speaker="",
        sha256=sha256,
        size_bytes=size_bytes,
        duration_ms=duration_ms,
        transcript=transcript,
        transcript_norm=transcript_norm,
        language=language,
        human_label=intake_emotion,
        heuristic_label=heuristic["label"],
        heuristic_score=heuristic["score"],
        is_key_hard_case=False,
        metadata={
            "audioId": (db_row or {}).get("audio_id"),
            "taskId": (db_row or {}).get("task_id"),
            "taskStatus": (db_row or {}).get("task_status"),
            "overallEmotionCode": canonical_emotion((db_row or {}).get("overall_emotion_code")),
            "overallConfidence": (db_row or {}).get("overall_confidence"),
            "heuristicHits": {
                "positive": heuristic["positiveHits"],
                "anger": heuristic["angerHits"],
                "sad": heuristic["sadHits"],
                "negative": heuristic["negativeHits"],
                "neutral": heuristic["neutralHits"],
            },
            "intendedEmotion": intake_emotion,
            "ingestPath": str(path.resolve()) if intake_emotion else None,
        },
    )
    audit["sampleId"] = sample.sample_id
    audit["heuristicLabel"] = sample.heuristic_label
    if intake_emotion:
        audit["intakeEmotion"] = intake_emotion
    return sample, audit


def build_controlled_candidates(args: argparse.Namespace) -> list[SampleRecord]:
    rows: list[dict[str, str]] = []
    manifest_root = SERVICE_ROOT / "training" / "manifests" / "zh_4class_v2"
    manifest_names = ("train.csv", "val.csv", "test.csv")
    for manifest_name in manifest_names:
        manifest_path = manifest_root / manifest_name
        if not manifest_path.exists():
            continue
        with manifest_path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            for row in reader:
                prepared = {key: (value or "").strip() for key, value in row.items()}
                prepared["source_split"] = manifest_name.removesuffix(".csv")
                rows.append(prepared)

    rows_by_label_speaker: dict[str, dict[str, list[dict[str, str]]]] = defaultdict(lambda: defaultdict(list))
    seed_match_rows: dict[str, dict[str, str]] = {}
    for row in rows:
        text = normalize_whitespace(row.get("text", ""))
        if not text or not contains_chinese(text):
            continue
        source_label = canonical_emotion(row.get("label") or row.get("human_label"))
        if source_label not in {"ANG", "HAP", "NEU", "SAD"}:
            continue
        transcript_norm = normalize_transcript(text)
        prepared = dict(row)
        prepared["text"] = text
        prepared["transcript_norm"] = transcript_norm
        speaker = row.get("speaker", "")
        rows_by_label_speaker[source_label][speaker].append(prepared)
        for rule in REVIEW_SEED_RULES:
            if rule["kind"] == "controlled_text" and text == rule["match"]:
                seed_match_rows[text] = prepared

    per_label_target = max(1, args.controlled_candidates_limit // 4)
    selected_rows: list[dict[str, str]] = []
    selected_ids: set[str] = set()

    def append_candidate(representative: dict[str, str], candidate_count_for_same_text: int) -> None:
        sample_id = f"controlled_{representative.get('speaker', '')}_{representative.get('file_id', 'unknown')}"
        if sample_id in selected_ids:
            return
        representative = dict(representative)
        representative["candidate_count_for_same_text"] = str(candidate_count_for_same_text)
        selected_rows.append(representative)
        selected_ids.add(sample_id)

    for label in ["ANG", "HAP", "NEU", "SAD"]:
        speaker_map = rows_by_label_speaker.get(label, {})
        speaker_order = sorted(speaker_map.keys())
        pointers = {speaker: 0 for speaker in speaker_order}
        used_transcripts: set[str] = set()
        chosen = 0
        while chosen < per_label_target and speaker_order:
            progressed = False
            for speaker in speaker_order:
                bucket = speaker_map[speaker]
                while pointers[speaker] < len(bucket):
                    candidate = bucket[pointers[speaker]]
                    pointers[speaker] += 1
                    transcript_norm = candidate["transcript_norm"]
                    if transcript_norm in used_transcripts:
                        continue
                    append_candidate(candidate, 1)
                    used_transcripts.add(transcript_norm)
                    chosen += 1
                    progressed = True
                    break
                if chosen >= per_label_target:
                    break
            if not progressed:
                break

    for rule in REVIEW_SEED_RULES:
        if rule["kind"] != "controlled_text":
            continue
        representative = seed_match_rows.get(rule["match"])
        if representative:
            append_candidate(representative, 1)

    candidates: list[SampleRecord] = []
    for representative in selected_rows:
        transcript = representative["text"]
        transcript_norm = representative["transcript_norm"]
        speaker = representative.get("speaker", "")
        heuristic = heuristic_semantic_scores(transcript, SERVICE_ROOT / "lexicon")
        candidates.append(
            SampleRecord(
                sample_id=f"controlled_{speaker}_{representative.get('file_id', 'unknown')}",
                source="controlled_zh",
                source_kind="zh_4class_v2",
                path=representative.get("path", ""),
                original_name=Path(representative.get("path", "")).name,
                speaker=speaker,
                sha256=None,
                size_bytes=None,
                duration_ms=None,
                transcript=transcript,
                transcript_norm=transcript_norm,
                language="zh",
                human_label=None,
                heuristic_label=heuristic["label"],
                heuristic_score=heuristic["score"],
                is_key_hard_case=False,
                metadata={
                    "sourceLabel": canonical_emotion(representative.get("label") or representative.get("human_label")),
                    "sourceDataset": representative.get("source_dataset", ""),
                    "sourceSplit": representative.get("source_split", ""),
                    "fileId": representative.get("file_id", ""),
                    "candidateCountForSameText": int(representative.get("candidate_count_for_same_text", "1")),
                    "heuristicHits": {
                        "positive": heuristic["positiveHits"],
                        "anger": heuristic["angerHits"],
                        "sad": heuristic["sadHits"],
                        "negative": heuristic["negativeHits"],
                        "neutral": heuristic["neutralHits"],
                    },
                },
            )
        )

    candidates.sort(key=lambda sample: (sample.metadata.get("sourceLabel") or "", sample.speaker, sample.sample_id))
    return candidates


def build_review_set(real_samples: list[SampleRecord], controlled_candidates: list[SampleRecord]) -> list[SampleRecord]:
    selected: list[SampleRecord] = []
    controlled_map = {sample.transcript: sample for sample in controlled_candidates}
    for rule in REVIEW_SEED_RULES:
        sample = None
        if rule["kind"] == "real_contains":
            for candidate in real_samples:
                if rule["match"] in candidate.transcript:
                    sample = candidate
                    break
        elif rule["kind"] == "controlled_text":
            sample = controlled_map.get(rule["match"])
        if sample is None:
            continue
        metadata = dict(sample.metadata)
        metadata["reviewNotes"] = rule["notes"]
        selected.append(
            SampleRecord(
                **{
                    **sample.__dict__,
                    "human_label": rule["humanLabel"],
                    "is_key_hard_case": bool(rule["hardCase"]),
                    "metadata": metadata,
                }
            )
        )
    return selected


def build_real_test_manifest(real_samples: list[SampleRecord], target: int) -> tuple[list[SampleRecord], dict[str, Any]]:
    freeze_path = REAL_POOL_ROOT / "real_test.freeze.json"
    existing = read_json(freeze_path, default=None)
    if isinstance(existing, dict) and existing.get("frozen") and existing.get("samples"):
        frozen_ids = set(existing["samples"])
        real_test = [sample for sample in real_samples if sample.sample_id in frozen_ids]
        real_dev = [sample for sample in real_samples if sample.sample_id not in frozen_ids]
        return real_test, {
            "schemaVersion": "thesis_v4.real_test_freeze.v1",
            "generatedAt": now_iso(),
            "frozen": True,
            "status": "FROZEN",
            "samples": [sample.sample_id for sample in real_test],
            "devSamples": [sample.sample_id for sample in real_dev],
            "notes": ["Using existing frozen real_test manifest."],
        }

    if target <= 0 or len(real_samples) < max(target, 8):
        return [], {
            "schemaVersion": "thesis_v4.real_test_freeze.v1",
            "generatedAt": now_iso(),
            "frozen": False,
            "status": "PENDING_CONFIRMATION",
            "samples": [],
            "devSamples": [sample.sample_id for sample in real_samples],
            "notes": [
                "real_test not frozen yet.",
                "Current real-world pool is insufficient for a stable frozen test split.",
            ],
        }

    pool = sorted(real_samples, key=lambda sample: (sample.heuristic_label or "ZZZ", sample.sample_id))
    chosen = pool[:target]
    chosen_ids = {sample.sample_id for sample in chosen}
    return chosen, {
        "schemaVersion": "thesis_v4.real_test_freeze.v1",
        "generatedAt": now_iso(),
        "frozen": False,
        "status": "PENDING_CONFIRMATION",
        "samples": [sample.sample_id for sample in chosen],
        "devSamples": [sample.sample_id for sample in real_samples if sample.sample_id not in chosen_ids],
        "notes": ["Candidate real_test generated. Freeze after human confirmation."],
    }


def build_recording_prompt_rows() -> list[dict[str, Any]]:
    return [
        {
            "prompt_id": item["promptId"],
            "emotion": item["emotion"],
            "text": item["text"],
            "recording_notes": "自然语气，单条 1-2 次录制，避免夸张表演和背景音乐。",
        }
        for item in RECORDING_PROMPTS
    ]


def main() -> None:
    args = parse_args()
    ensure_dirs()

    db_rows = fetch_db_rows(args)
    real_sources = build_real_sources(args, db_rows)
    real_samples: list[SampleRecord] = []
    audit_rows: list[dict[str, Any]] = []
    hash_groups: defaultdict[str, list[str]] = defaultdict(list)
    transcript_groups: defaultdict[str, list[str]] = defaultdict(list)
    for item in real_sources:
        sample, audit = classify_real_source(item, args)
        audit_rows.append(audit)
        if sample is None:
            continue
        hash_groups[sample.sha256 or sample.sample_id].append(sample.sample_id)
        transcript_groups[sample.transcript_norm].append(sample.sample_id)
        real_samples.append(sample)

    exact_dedup: list[SampleRecord] = []
    seen_sha: set[str] = set()
    seen_transcript: set[str] = set()
    dropped_duplicates: list[dict[str, Any]] = []
    for sample in sorted(real_samples, key=lambda row: row.sample_id):
        duplicate_reason = None
        if sample.sha256 and sample.sha256 in seen_sha:
            duplicate_reason = "duplicate_sha256"
        elif sample.transcript_norm in seen_transcript:
            duplicate_reason = "duplicate_transcript"
        if duplicate_reason:
            dropped_duplicates.append(
                {
                    "sampleId": sample.sample_id,
                    "path": sample.path,
                    "reason": duplicate_reason,
                    "transcript": sample.transcript,
                }
            )
            continue
        if sample.sha256:
            seen_sha.add(sample.sha256)
        seen_transcript.add(sample.transcript_norm)
        exact_dedup.append(sample)

    controlled_candidates = build_controlled_candidates(args)
    review_set = build_review_set(exact_dedup, controlled_candidates)
    real_test, freeze_manifest = build_real_test_manifest(exact_dedup, args.real_test_target)
    real_test_ids = {sample.sample_id for sample in real_test}
    real_dev = [sample for sample in exact_dedup if sample.sample_id not in real_test_ids]

    write_samples_csv(REAL_POOL_ROOT / "real_world_pool.csv", exact_dedup)
    write_samples_csv(REAL_POOL_ROOT / "real_dev.csv", real_dev)
    write_samples_csv(REAL_POOL_ROOT / "real_test.csv", real_test)
    write_json(REAL_POOL_ROOT / "real_test.freeze.json", freeze_manifest)
    write_samples_csv(CONTROLLED_POOL_ROOT / "controlled_zh_candidates.csv", controlled_candidates)
    write_samples_csv(REVIEW_MANIFEST_PATH, review_set)
    write_json(RECORDING_PROMPTS_JSON_PATH, build_recording_prompt_rows())
    write_csv(
        RECORDING_PROMPTS_CSV_PATH,
        build_recording_prompt_rows(),
        ["prompt_id", "emotion", "text", "recording_notes"],
    )

    dedup_audit = {
        "schemaVersion": "thesis_v4.dedup_audit.v1",
        "generatedAt": now_iso(),
        "sourceRoots": {
            "uploads": [str(Path(item).resolve()) for item in args.uploads_dir] + [str(Path(root).resolve()) for root in DEFAULT_UPLOAD_ROOTS],
            "intake": str(RECORDING_DROPBOX_ROOT.resolve()),
            "serBaseUrl": ASR_BASE_URL,
            "database": {"host": args.db_host, "port": args.db_port, "name": args.db_name},
        },
        "summary": {
            "realSourcesScanned": len(real_sources),
            "realCandidatesBeforeDedup": len(real_samples),
            "realWorldPool": len(exact_dedup),
            "realDev": len(real_dev),
            "realTest": len(real_test),
            "controlledCandidates": len(controlled_candidates),
            "reviewSet": len(review_set),
            "knownUploadFact": {
                "historicalUploadFiles": 41,
                "historicalUploadUniqueBytes": 14,
                "duplicateNeutralQuestionGroup": 19,
                "duplicateEnglishShortGroup": 7,
                "duplicateLongMp3Group": 4,
            },
        },
        "exclusionSummary": dict(Counter(reason for row in audit_rows for reason in row.get("reasons", []))),
        "duplicateSummary": {
            "sha256Groups": {key: value for key, value in hash_groups.items() if len(value) > 1},
            "transcriptGroups": {key: value for key, value in transcript_groups.items() if len(value) > 1},
            "droppedDuplicates": dropped_duplicates,
        },
        "auditRows": audit_rows,
        "recordingPromptFile": str(RECORDING_PROMPTS_CSV_PATH.resolve()),
        "notes": [
            "real_test follows a freeze manifest. It remains pending until explicitly frozen.",
            "controlled_zh candidates are deduplicated by normalized transcript before later teacher selection.",
            "Folder-based human labels apply only to new intake recordings under recording_dropbox.",
            "Formal train/val/test generation must wait for teacher approval and explicit freeze artifacts.",
        ],
    }
    write_json(LOG_ROOT / "dedup_audit.json", dedup_audit)
    print(json.dumps(dedup_audit, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

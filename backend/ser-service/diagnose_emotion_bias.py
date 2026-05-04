import argparse
import json
import math
import re
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

import numpy as np
import soundfile as sf
import torch

import app
from training.audio_utils import load_audio_mono_resample


PROJECT_LABELS = ("ANGRY", "HAPPY", "NEUTRAL", "SAD")
TASK_REPORT_PAGE_SIZE = 200
DEFAULT_REDUCED_AUDIO_WEIGHT = 0.35
RULE_VERSION = "sad_positive_conflict_v1"
LOW_CONSISTENCY_CODE = "LOW_CONSISTENCY"
LOW_CONSISTENCY_STATUS = "LOW_CONSISTENCY"
READY_STATUS = "READY"
UNAVAILABLE_STATUS = "UNAVAILABLE"
UNKNOWN_CODE = "UNKNOWN"
BASE_EMOTION_MISSING_REASON = "base_emotion_missing"
SAD_POSITIVE_CONFLICT_REASON = "voice_sad_high_conflicts_with_positive_text"
VOICE_CONFIDENCE_MIN = 0.98
FUSED_TEXT_NEG_MAX = 0.45
TRANSCRIPT_EXCERPT_MAX_CHARS = 120
TRANSCRIPT_LEFT_CONTEXT_CHARS = 24
TRANSCRIPT_RIGHT_CONTEXT_CHARS = 72
POSITIVE_CONFLICT_TERMS = ("开心", "高兴", "快乐", "真好", "很好", "太阳非常好", "休息很好", "休息的很好")
NEGATIVE_CONFLICT_TERMS = ("不开心", "难过", "伤心", "痛苦", "愤怒", "生气", "焦虑", "绝望", "抑郁", "压力")


def round6(value: float | None) -> float | None:
    if value is None:
        return None
    return round(float(value), 6)


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


def normalize_project_label(label: str) -> str:
    return app.normalize_label(label)


def entropy(values: list[float]) -> float:
    eps = 1e-12
    return float(-sum(float(v) * math.log(max(float(v), eps)) for v in values))


def score_from_map(scores: dict[str, Any] | None, key: str, fallback: float) -> float:
    if not scores:
        return fallback
    value = scores.get(key)
    if value is None:
        value = scores.get(key.upper())
    if value is None:
        value = scores.get(key.lower())
    return fallback if value is None else float(value)


def argmax_label(scores: dict[str, float]) -> tuple[str, float]:
    best_label = max(scores, key=scores.get)
    return best_label, float(scores[best_label])


def normalize_text(text: str | None) -> str:
    return " ".join((text or "").replace("\r", " ").replace("\n", " ").split())


def json_request(method: str, url: str, payload: dict[str, Any] | None = None, headers: dict[str, str] | None = None) -> Any:
    data = None
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(url=url, method=method.upper(), headers=req_headers, data=data)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"request failed: {method} {url} status={exc.code} body={body}") from exc


def admin_login(base_url: str, username: str, password: str) -> str:
    response = json_request(
        "POST",
        f"{base_url.rstrip('/')}/api/auth/admin/login",
        {"username": username, "password": password},
    )
    token = response.get("accessToken")
    if not token:
        raise RuntimeError("admin login succeeded without accessToken")
    return token


def fetch_task_bundle(base_url: str, task_id: int, token: str, upload_dir: Path) -> dict[str, Any]:
    auth_headers = {"Authorization": f"Bearer {token}"}
    task_result = json_request(
        "GET",
        f"{base_url.rstrip('/')}/api/analysis/task/{task_id}/result",
        headers=auth_headers,
    )
    reports = json_request(
        "GET",
        f"{base_url.rstrip('/')}/api/reports?page=1&pageSize={TASK_REPORT_PAGE_SIZE}",
        headers=auth_headers,
    )
    report_match = next((item for item in reports.get("items", []) if int(item.get("taskId", -1)) == int(task_id)), None)
    if report_match is None:
        raise RuntimeError(f"report not found for taskId={task_id}")
    stored_name = (((report_match.get("audio") or {}).get("storedName")) or "").strip()
    raw_audio_path = upload_dir / stored_name
    if not stored_name or not raw_audio_path.exists():
        raise RuntimeError(f"audio file not found for taskId={task_id}: {raw_audio_path}")
    raw_json = (((task_result.get("analysis_result") or {}).get("raw_json")) or "").strip()
    persisted = json.loads(raw_json) if raw_json else {}
    return {
        "taskResult": task_result,
        "report": report_match,
        "audioPath": str(raw_audio_path),
        "persisted": persisted,
    }


def ffmpeg_probe(input_path: Path) -> dict[str, Any]:
    ffmpeg = app.resolve_ffmpeg_binary()
    if not ffmpeg:
        raise RuntimeError("ffmpeg binary unavailable")
    proc = subprocess.run(
        [ffmpeg, "-hide_banner", "-i", str(input_path), "-f", "null", "-"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    stderr = proc.stderr or ""
    duration_match = re.search(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)", stderr)
    if duration_match is None:
        duration_match = re.search(r"time=(\d+):(\d+):(\d+(?:\.\d+)?)", stderr)
    stream_match = re.search(r"Audio:.*?(\d+)\s*Hz,\s*([^,]+)", stderr)
    duration_ms = None
    if duration_match:
        hours = int(duration_match.group(1))
        minutes = int(duration_match.group(2))
        seconds = float(duration_match.group(3))
        duration_ms = int(round((hours * 3600 + minutes * 60 + seconds) * 1000))
    sample_rate = int(stream_match.group(1)) if stream_match else None
    channel_desc = stream_match.group(2).strip() if stream_match else None
    if channel_desc == "mono":
        channels = 1
    elif channel_desc == "stereo":
        channels = 2
    else:
        number_match = re.search(r"(\d+)", channel_desc or "")
        channels = int(number_match.group(1)) if number_match else None
    return {
        "path": str(input_path),
        "durationMs": duration_ms,
        "sampleRate": sample_rate,
        "channels": channels,
        "channelDescription": channel_desc,
        "ffmpegStderrExcerpt": stderr.splitlines()[-8:],
    }


def array_stats(audio: np.ndarray, sample_rate: int) -> dict[str, Any]:
    audio = np.asarray(audio, dtype=np.float32)
    if audio.ndim == 2:
        channels = int(audio.shape[1])
        mono = np.mean(audio, axis=1)
    else:
        channels = 1
        mono = audio
    mono = mono.astype(np.float32, copy=False)
    peak = float(np.max(np.abs(mono))) if mono.size else 0.0
    rms = float(np.sqrt(np.mean(np.square(mono)))) if mono.size else 0.0
    silent_ratio = float(np.mean(np.abs(mono) < 1e-3)) if mono.size else 1.0
    return {
        "sampleRate": int(sample_rate),
        "channels": channels,
        "samples": int(mono.shape[0]),
        "durationMs": int(round(mono.shape[0] * 1000.0 / float(sample_rate))) if sample_rate > 0 else None,
        "min": round6(float(np.min(mono))) if mono.size else None,
        "max": round6(float(np.max(mono))) if mono.size else None,
        "mean": round6(float(np.mean(mono))) if mono.size else None,
        "std": round6(float(np.std(mono))) if mono.size else None,
        "rms": round6(rms),
        "peakAbs": round6(peak),
        "silentRatioAbsLt1e3": round6(silent_ratio),
    }


def normalize_audio(raw_audio_path: Path) -> tuple[Path, np.ndarray, int]:
    temp_dir = Path(tempfile.mkdtemp(prefix="emotion-diagnose-"))
    normalized_path = temp_dir / "normalized.wav"
    app.run_ffmpeg_convert(raw_audio_path, normalized_path)
    audio, sample_rate = sf.read(str(normalized_path), dtype="float32")
    if audio.ndim > 1:
        audio = np.mean(audio, axis=1)
    return normalized_path, np.asarray(audio, dtype=np.float32), int(sample_rate)


def transcribe_audio(normalized_wav: Path) -> dict[str, Any]:
    model = app.get_asr_model()
    segments_iter, info = model.transcribe(str(normalized_wav), vad_filter=True)
    segments = [
        {
            "startMs": int(math.floor(seg.start * 1000)),
            "endMs": int(math.floor(seg.end * 1000)),
            "text": seg.text.strip(),
        }
        for seg in segments_iter
    ]
    language = (getattr(info, "language", "") or "").lower()
    if language.startswith("zh"):
        language = "zh"
    elif language.startswith("en"):
        language = "en"
    else:
        language = None
    return {
        "text": " ".join(item["text"] for item in segments).strip(),
        "language": language,
        "segments": segments,
    }


def build_text_features_java_style(transcript: str, text_sentiment: dict[str, Any] | None) -> dict[str, float]:
    negative = 0.0
    neutral = 1.0
    positive = 0.0
    negative_score = None
    text4_ang = negative * 0.5
    text4_sad = negative * 0.5
    text4_neu = neutral
    text4_hap = positive
    text4_ready = 0.0

    if text_sentiment:
        if text_sentiment.get("negativeScore") is not None:
            negative_score = clamp01(text_sentiment["negativeScore"])
            negative = negative_score
        scores = text_sentiment.get("scores") or {}
        if scores:
            negative = clamp01(score_from_map(scores, "negative", negative))
            neutral = clamp01(score_from_map(scores, "neutral", neutral))
            positive = clamp01(score_from_map(scores, "positive", positive))
            total = negative + neutral + positive
            if total > 0.0:
                negative /= total
                neutral /= total
                positive /= total
        else:
            neutral = max(0.0, 1.0 - negative)
            positive = 0.0

        emotion4_scores = text_sentiment.get("emotion4Scores") or {}
        if emotion4_scores:
            text4_ang = clamp01(score_from_map(emotion4_scores, "ANG", text4_ang))
            text4_hap = clamp01(score_from_map(emotion4_scores, "HAP", text4_hap))
            text4_neu = clamp01(score_from_map(emotion4_scores, "NEU", text4_neu))
            text4_sad = clamp01(score_from_map(emotion4_scores, "SAD", text4_sad))
            total4 = text4_ang + text4_hap + text4_neu + text4_sad
            if total4 > 0.0:
                text4_ang /= total4
                text4_hap /= total4
                text4_neu /= total4
                text4_sad /= total4
            text4_ready = 1.0 if bool(text_sentiment.get("emotion4Ready")) else 0.0
        else:
            text4_ang = negative * 0.5
            text4_sad = negative * 0.5
            text4_neu = neutral
            text4_hap = positive

    if negative_score is None:
        negative_score = negative
    text_length_norm = clamp01((len(transcript) if transcript else 0) / 256.0)
    text4_confidence = max(text4_ang, text4_hap, text4_neu, text4_sad)
    text4_entropy = -sum(
        max(min(v, 1.0), 1e-12) * math.log(max(min(v, 1.0), 1e-12))
        for v in (text4_ang, text4_hap, text4_neu, text4_sad)
    )

    return {
        "text_negative": round6(negative),
        "text_neutral": round6(neutral),
        "text_positive": round6(positive),
        "text_negative_score": round6(negative_score),
        "text_length_norm": round6(text_length_norm),
        "text4_prob_ang": round6(text4_ang),
        "text4_prob_hap": round6(text4_hap),
        "text4_prob_neu": round6(text4_neu),
        "text4_prob_sad": round6(text4_sad),
        "text4_confidence": round6(text4_confidence),
        "text4_entropy": round6(text4_entropy),
        "text4_ready": round6(text4_ready),
    }


def build_text_only_distribution(text_features: dict[str, float]) -> dict[str, float]:
    return {
        "ANGRY": float(text_features.get("text4_prob_ang", 0.0) or 0.0),
        "HAPPY": float(text_features.get("text4_prob_hap", 0.0) or 0.0),
        "NEUTRAL": float(text_features.get("text4_prob_neu", 0.0) or 0.0),
        "SAD": float(text_features.get("text4_prob_sad", 0.0) or 0.0),
    }


def get_hf_segment_prediction(runtime: Any, segment_wav_path: Path) -> dict[str, Any]:
    audio = load_audio_mono_resample(segment_wav_path, target_sr=runtime.sample_rate, max_duration_sec=None)
    encoded = runtime.feature_extractor(
        [audio],
        sampling_rate=runtime.sample_rate,
        return_tensors="pt",
        padding=True,
        return_attention_mask=True,
    )
    inputs = {"input_values": encoded["input_values"].to(runtime.device)}
    attention_mask = encoded.get("attention_mask")
    if attention_mask is not None:
        inputs["attention_mask"] = attention_mask.to(runtime.device)
    with torch.no_grad():
        outputs = runtime.model(**inputs)
        logits = outputs.logits[0].detach().cpu()
        probs = torch.softmax(outputs.logits, dim=-1)[0].detach().cpu()
    logits_map = {runtime.id2label[idx]: round6(float(logits[idx].item())) for idx in range(int(logits.shape[0]))}
    probs_map = {runtime.id2label[idx]: round6(float(probs[idx].item())) for idx in range(int(probs.shape[0]))}
    pred_id = int(torch.argmax(probs).item())
    raw_label = runtime.id2label[pred_id]
    project_label = normalize_project_label(raw_label)
    return {
        "rawLabel": raw_label,
        "projectLabel": project_label,
        "predId": pred_id,
        "confidence": round6(float(probs[pred_id].item())),
        "logits": logits_map,
        "softmax": probs_map,
    }


def analyze_voice_segments(
    normalized_audio: np.ndarray,
    sample_rate: int,
    segment_ms: int,
    overlap_ms: int,
    language_hint: str | None,
) -> dict[str, Any]:
    runtime, model_meta = app.get_model_for_language(language_hint)
    if app.SER_ENGINE != "hf_wav2vec2":
        raise RuntimeError(f"voice debug script currently supports hf_wav2vec2 only, got {app.SER_ENGINE}")

    temp_dir = Path(tempfile.mkdtemp(prefix="emotion-segments-"))
    seg_samples = int(sample_rate * (segment_ms / 1000.0))
    hop_samples = max(int(sample_rate * ((segment_ms - overlap_ms) / 1000.0)), 1)

    segments_verbose = []
    segments_simple = []
    start = 0
    index = 0
    while start < len(normalized_audio):
        end = min(start + seg_samples, len(normalized_audio))
        if end <= start:
            break
        chunk = normalized_audio[start:end]
        chunk_path = temp_dir / f"seg_{index}.wav"
        sf.write(str(chunk_path), chunk, sample_rate, subtype="PCM_16")
        prediction = get_hf_segment_prediction(runtime, chunk_path)
        start_ms = int(math.floor(start / sample_rate * 1000))
        end_ms = int(math.floor(end / sample_rate * 1000))
        segment_entry = {
            "index": index,
            "path": str(chunk_path),
            "startMs": start_ms,
            "endMs": end_ms,
            "samples": int(end - start),
            "rawLabel": prediction["rawLabel"],
            "emotionCode": prediction["projectLabel"],
            "confidence": prediction["confidence"],
            "logits": prediction["logits"],
            "softmax": prediction["softmax"],
        }
        segments_verbose.append(segment_entry)
        segments_simple.append(
            {
                "startMs": start_ms,
                "endMs": end_ms,
                "emotionCode": prediction["projectLabel"],
                "confidence": prediction["confidence"],
            }
        )
        if end == len(normalized_audio):
            break
        start += hop_samples
        index += 1

    overall = app.aggregate_overall(segments_simple)
    audio_summary = app.build_audio_summary(segments_simple)
    label_map = {int(k): v for k, v in runtime.id2label.items()}
    return {
        "modelMeta": model_meta,
        "labelMap": label_map,
        "segmentsVerbose": segments_verbose,
        "segmentsSimple": segments_simple,
        "overall": overall,
        "audioSummary": audio_summary,
    }


def blend_audio_summary(audio_summary: dict[str, Any], voice_weight: float) -> dict[str, Any]:
    weight = clamp01(voice_weight)
    probs = np.asarray(
        [
            float(audio_summary.get("audio_prob_ang", 0.0) or 0.0),
            float(audio_summary.get("audio_prob_hap", 0.0) or 0.0),
            float(audio_summary.get("audio_prob_neu", 0.0) or 0.0),
            float(audio_summary.get("audio_prob_sad", 0.0) or 0.0),
        ],
        dtype=np.float64,
    )
    uniform = np.asarray([0.25, 0.25, 0.25, 0.25], dtype=np.float64)
    blended = probs * weight + uniform * (1.0 - weight)
    blended = blended / blended.sum()
    dominant_idx = int(np.argmax(blended))
    dominant_labels = ("ANGRY", "HAPPY", "NEUTRAL", "SAD")
    return {
        "audio_prob_ang": round6(float(blended[0])),
        "audio_prob_hap": round6(float(blended[1])),
        "audio_prob_neu": round6(float(blended[2])),
        "audio_prob_sad": round6(float(blended[3])),
        "audio_confidence": round6(float(audio_summary.get("audio_confidence", 0.0) or 0.0) * weight),
        "audio_entropy": round6(entropy(blended.tolist())),
        "dominantEmotion": dominant_labels[dominant_idx],
        "voiceWeightApplied": round6(weight),
    }


def build_ablation_result(
    voice_analysis: dict[str, Any],
    text_features: dict[str, float],
    route_language: str | None,
    reduced_audio_weight: float,
) -> dict[str, Any]:
    audio_summary = voice_analysis["audioSummary"]
    voice_only_label = str(voice_analysis["overall"].get("emotionCode", "NEUTRAL"))
    voice_only_conf = float(voice_analysis["overall"].get("confidence", 0.0) or 0.0)

    text_only_distribution = build_text_only_distribution(text_features)
    text_only_label, text_only_conf = argmax_label(text_only_distribution)

    current_fusion = app.predict_fusion_result(audio_summary, text_features, route_language)
    reduced_audio_summary = blend_audio_summary(audio_summary, reduced_audio_weight)
    reduced_fusion = app.predict_fusion_result(reduced_audio_summary, text_features, route_language)

    return {
        "voiceOnly": {
            "label": voice_only_label,
            "confidence": round6(voice_only_conf),
            "distribution": {
                "ANGRY": round6(float(audio_summary.get("audio_prob_ang", 0.0) or 0.0)),
                "HAPPY": round6(float(audio_summary.get("audio_prob_hap", 0.0) or 0.0)),
                "NEUTRAL": round6(float(audio_summary.get("audio_prob_neu", 0.0) or 0.0)),
                "SAD": round6(float(audio_summary.get("audio_prob_sad", 0.0) or 0.0)),
            },
        },
        "textOnlyCurrent": {
            "label": text_only_label,
            "confidence": round6(text_only_conf),
            "distribution": {key: round6(value) for key, value in text_only_distribution.items()},
        },
        "currentFusion": current_fusion,
        "reducedAudioFusion": {
            "audioSummary": reduced_audio_summary,
            "fusion": reduced_fusion,
        },
    }


def detect_conflict_terms(asr_text: str) -> dict[str, list[str]]:
    text = (asr_text or "").strip()
    positive_hits = []
    negative_hits = []

    for term in NEGATIVE_CONFLICT_TERMS:
        if term in text and term not in negative_hits:
            negative_hits.append(term)

    for term in POSITIVE_CONFLICT_TERMS:
        if term not in text:
            continue
        if term == "开心" and "不开心" in text:
            continue
        if term == "很好" and "不好" in text:
            continue
        if term not in positive_hits:
            positive_hits.append(term)

    return {
        "positiveHits": positive_hits,
        "negativeHits": negative_hits,
    }


def build_conflict_guard_result(asr_text: str, voice_only: dict[str, Any], final_prediction: dict[str, Any]) -> dict[str, Any]:
    hits = detect_conflict_terms(asr_text)
    voice_label = str(voice_only.get("label") or "")
    voice_conf = float(voice_only.get("confidence", 0.0) or 0.0)
    triggered = voice_label == "SAD" and voice_conf >= 0.98 and bool(hits["positiveHits"]) and not hits["negativeHits"]
    if triggered:
        return {
            "triggered": True,
            "label": "LOW_CONSISTENCY",
            "reason": "voice_sad_high_conflicts_with_positive_text",
            "voiceConfidence": round6(voice_conf),
            "positiveHits": hits["positiveHits"],
            "negativeHits": hits["negativeHits"],
        }
    return {
        "triggered": False,
        "label": final_prediction["label"],
        "confidence": final_prediction["confidence"],
        "positiveHits": hits["positiveHits"],
        "negativeHits": hits["negativeHits"],
    }


def split_advice_text(advice_text: str | None) -> list[str]:
    if not advice_text:
        return []
    tokens = re.split(r"[\r\n;；]+", advice_text)
    items = []
    for token in tokens:
        value = token.strip()
        if value and value not in items:
            items.append(value)
    return items


def build_narrative_prompt_payload(
    task_id: int | None,
    ser_response: dict[str, Any],
    transcript: str | None,
    risk_assessment: dict[str, Any] | None,
    language: str = "zh-CN",
    max_segments: int = 3,
    max_transcript_chars: int = 600,
) -> dict[str, Any]:
    fusion = ser_response.get("fusion") or {}
    overall = ser_response.get("overall") or {}
    resolved_overall_emotion = fusion.get("label") or overall.get("emotionCode")
    baseline_advice = split_advice_text((risk_assessment or {}).get("advice_text"))
    segments = ser_response.get("segments") or []
    top_segments = sorted(
        [
            {
                "startMs": seg.get("startMs"),
                "endMs": seg.get("endMs"),
                "emotion": seg.get("emotionCode"),
                "confidence": seg.get("confidence"),
            }
            for seg in segments
        ],
        key=lambda item: float(item.get("confidence") or 0.0),
        reverse=True,
    )[: max(1, max_segments)]
    transcript_excerpt = None
    if transcript:
        normalized = transcript.replace("\r", " ").replace("\n", " ").strip()
        transcript_excerpt = normalized[:max_transcript_chars] + ("..." if len(normalized) > max_transcript_chars else "")
    return {
        "taskId": task_id,
        "language": language,
        "overallEmotion": resolved_overall_emotion,
        "risk": {
            "score": None if risk_assessment is None else risk_assessment.get("risk_score"),
            "level": None if risk_assessment is None else risk_assessment.get("risk_level"),
            "pSad": None if risk_assessment is None else risk_assessment.get("p_sad"),
            "pAngry": None if risk_assessment is None else risk_assessment.get("p_angry"),
            "varConf": None if risk_assessment is None else risk_assessment.get("var_conf"),
            "textNeg": None if risk_assessment is None else risk_assessment.get("text_neg"),
        },
        "serFusion": {
            "label": fusion.get("label"),
            "confidence": fusion.get("confidence"),
            "ready": fusion.get("ready"),
            "overallEmotion": overall.get("emotionCode"),
            "overallConfidence": overall.get("confidence"),
        },
        "topSegments": top_segments,
        "transcriptExcerpt": transcript_excerpt,
        "baselineAdvice": baseline_advice,
    }


def select_known_label_samples(dataset_root: Path, per_label: int) -> list[dict[str, Any]]:
    selected: list[dict[str, Any]] = []
    for folder_name, expected in (("happy", "HAPPY"), ("sad", "SAD"), ("neutral", "NEUTRAL")):
        files = sorted(dataset_root.glob(f"**/{folder_name}/*.wav"))
        for wav_path in files[:per_label]:
            selected.append(
                {
                    "path": str(wav_path),
                    "expectedLabel": expected,
                    "datasetSource": dataset_root.name,
                }
            )
    return selected


def analyze_audio_path(
    audio_path: Path,
    segment_ms: int,
    overlap_ms: int,
    reduced_audio_weight: float,
) -> dict[str, Any]:
    raw_stats = ffmpeg_probe(audio_path)
    normalized_path, normalized_audio, sample_rate = normalize_audio(audio_path)
    preprocessed_stats = array_stats(normalized_audio, sample_rate)
    asr_result = transcribe_audio(normalized_path)
    language_hint = asr_result.get("language")
    text_sentiment = app.analyze_text_sentiment(asr_result.get("text", ""), language_hint)
    text_features = build_text_features_java_style(asr_result.get("text", ""), text_sentiment)
    voice_analysis = analyze_voice_segments(
        normalized_audio=normalized_audio,
        sample_rate=sample_rate,
        segment_ms=segment_ms,
        overlap_ms=overlap_ms,
        language_hint=language_hint,
    )
    route_language = (voice_analysis.get("modelMeta") or {}).get("routeLanguage")
    fusion_result = app.predict_fusion_result(voice_analysis["audioSummary"], text_features, route_language)
    ablation = build_ablation_result(voice_analysis, text_features, route_language, reduced_audio_weight)
    final_label = (
        (fusion_result.get("label") if fusion_result.get("ready") else None)
        or voice_analysis["overall"].get("emotionCode")
        or "NEUTRAL"
    )
    final_confidence = (
        float(fusion_result.get("confidence", 0.0) or 0.0)
        if fusion_result.get("ready")
        else float(voice_analysis["overall"].get("confidence", 0.0) or 0.0)
    )
    narrative_input = build_narrative_prompt_payload(
        task_id=None,
        ser_response={
            "overall": voice_analysis["overall"],
            "segments": voice_analysis["segmentsSimple"],
            "fusion": fusion_result,
        },
        transcript=asr_result.get("text"),
        risk_assessment=None,
    )
    return {
        "audioPath": str(audio_path),
        "rawAudioStats": raw_stats,
        "preprocessedAudioPath": str(normalized_path),
        "preprocessedAudioStats": preprocessed_stats,
        "asr": asr_result,
        "voiceBranch": voice_analysis,
        "textSentiment": text_sentiment,
        "textFeatures": text_features,
        "fusion": fusion_result,
        "ablation": ablation,
        "finalPrediction": {
            "label": final_label,
            "confidence": round6(final_confidence),
        },
        "conflictGuardCandidate": build_conflict_guard_result(
            asr_text=asr_result.get("text", ""),
            voice_only=ablation["voiceOnly"],
            final_prediction={"label": final_label, "confidence": round6(final_confidence)},
        ),
        "gemmaStructuredInput": narrative_input,
    }


def summarize_batch_item(item: dict[str, Any], reduced_audio_weight: float) -> dict[str, Any]:
    text_branch_label = item["ablation"]["textOnlyCurrent"]["label"]
    text_branch_conf = item["ablation"]["textOnlyCurrent"]["confidence"]
    reduced_fusion = item["ablation"]["reducedAudioFusion"]["fusion"]
    reduced_label = (reduced_fusion.get("label") if reduced_fusion.get("ready") else None) or item["ablation"]["voiceOnly"]["label"]
    reduced_conf = (
        float(reduced_fusion.get("confidence", 0.0) or 0.0)
        if reduced_fusion.get("ready")
        else float(item["ablation"]["voiceOnly"]["confidence"] or 0.0)
    )
    return {
        "audioPath": item["audioPath"],
        "expectedLabel": item["expectedLabel"],
        "asrText": item["asr"]["text"],
        "voicePrediction": {
            "label": item["ablation"]["voiceOnly"]["label"],
            "confidence": item["ablation"]["voiceOnly"]["confidence"],
        },
        "textPrediction": {
            "label": text_branch_label,
            "confidence": text_branch_conf,
            "modelLabelRaw": item["textSentiment"].get("label"),
        },
        "fusionPrediction": {
            "label": item["finalPrediction"]["label"],
            "confidence": item["finalPrediction"]["confidence"],
        },
        "finalLabel": item["finalPrediction"]["label"],
        "confidence": item["finalPrediction"]["confidence"],
        "isMisclassified": item["finalPrediction"]["label"] != item["expectedLabel"],
        "afterReducedAudioWeight": {
            "voiceWeight": round6(reduced_audio_weight),
            "label": reduced_label,
            "confidence": round6(reduced_conf),
            "isMisclassified": reduced_label != item["expectedLabel"],
        },
        "afterConflictGuard": {
            "triggered": item["conflictGuardCandidate"]["triggered"],
            "label": item["conflictGuardCandidate"]["label"],
            "positiveHits": item["conflictGuardCandidate"]["positiveHits"],
            "negativeHits": item["conflictGuardCandidate"]["negativeHits"],
            "isMisclassified": item["conflictGuardCandidate"]["label"] != item["expectedLabel"],
        },
    }


def batch_metrics(rows: list[dict[str, Any]], field: str) -> dict[str, Any]:
    total = len(rows)
    errors = sum(1 for row in rows if row.get(field))
    by_expected: dict[str, dict[str, int]] = {}
    for row in rows:
        expected = row["expectedLabel"]
        bucket = by_expected.setdefault(expected, {"total": 0, "errors": 0})
        bucket["total"] += 1
        if row.get(field):
            bucket["errors"] += 1
    return {
        "total": total,
        "errors": errors,
        "accuracy": round6((total - errors) / total) if total else None,
        "byExpectedLabel": {
            label: {
                "total": bucket["total"],
                "errors": bucket["errors"],
                "accuracy": round6((bucket["total"] - bucket["errors"]) / bucket["total"]) if bucket["total"] else None,
            }
            for label, bucket in by_expected.items()
        },
    }


def find_term_matches(text: str, terms: tuple[str, ...], blocked_matches: list[dict[str, Any]] | None = None) -> list[dict[str, Any]]:
    normalized = normalize_text(text)
    lowered = normalized.lower()
    blocked_matches = blocked_matches or []
    matches = []
    for term in terms:
        needle = (term or "").strip()
        if not needle:
            continue
        start = lowered.find(needle.lower())
        if start < 0:
            continue
        end = start + len(needle)
        if any(start < blocked["end"] and end > blocked["start"] for blocked in blocked_matches):
            continue
        matches.append({"term": needle, "start": start, "end": end})
    matches.sort(key=lambda item: item["start"])
    return matches


def build_transcript_excerpt_v2(text: str) -> str:
    normalized = normalize_text(text)
    return normalized[:TRANSCRIPT_EXCERPT_MAX_CHARS]


def build_transcript_excerpt_for_hits(text: str, positive_matches: list[dict[str, Any]], negative_matches: list[dict[str, Any]]) -> str | None:
    normalized = normalize_text(text)
    if not normalized:
        return None
    anchors = sorted([*positive_matches, *negative_matches], key=lambda item: item["start"])
    if not anchors:
        return build_transcript_excerpt_v2(normalized)
    first = anchors[0]
    start = max(0, first["start"] - TRANSCRIPT_LEFT_CONTEXT_CHARS)
    end = min(len(normalized), first["end"] + TRANSCRIPT_RIGHT_CONTEXT_CHARS)
    if end - start > TRANSCRIPT_EXCERPT_MAX_CHARS:
        end = min(len(normalized), start + TRANSCRIPT_EXCERPT_MAX_CHARS)
        if first["end"] > end:
            end = first["end"]
            start = max(0, end - TRANSCRIPT_EXCERPT_MAX_CHARS)
    return normalized[start:end].strip()


def resolve_base_result_v2(voice_analysis: dict[str, Any], fusion_result: dict[str, Any]) -> dict[str, Any]:
    if fusion_result.get("ready") and fusion_result.get("label"):
        return {
            "emotionCode": str(fusion_result.get("label")),
            "confidence": round6(float(fusion_result.get("confidence", 0.0) or 0.0)),
        }
    voice_overall = voice_analysis.get("overall") or {}
    confidence = voice_overall.get("confidence")
    return {
        "emotionCode": voice_overall.get("emotionCode"),
        "confidence": None if confidence is None else round6(float(confidence)),
    }


def build_decision_without_guard_v2(base_result: dict[str, Any]) -> dict[str, Any]:
    base_code = base_result.get("emotionCode")
    base_conf = base_result.get("confidence")
    if base_code:
        return {
            "code": base_code,
            "status": READY_STATUS,
            "reason": None,
            "baseEmotionCode": base_code,
            "baseConfidence": base_conf,
        }
    return {
        "code": UNKNOWN_CODE,
        "status": UNAVAILABLE_STATUS,
        "reason": BASE_EMOTION_MISSING_REASON,
        "baseEmotionCode": None,
        "baseConfidence": None,
    }


def build_guard_audit_v2(
    asr_text: str,
    voice_analysis: dict[str, Any],
    text_sentiment: dict[str, Any] | None,
    fused_text_neg: float,
    base_result: dict[str, Any],
) -> dict[str, Any]:
    negative_matches = find_term_matches(asr_text, NEGATIVE_CONFLICT_TERMS)
    positive_matches = find_term_matches(asr_text, POSITIVE_CONFLICT_TERMS, blocked_matches=negative_matches)
    voice_overall = voice_analysis.get("overall") or {}
    voice_conf = float(voice_overall.get("confidence", 0.0) or 0.0)
    triggered = (
        base_result.get("emotionCode") == "SAD"
        and voice_conf >= VOICE_CONFIDENCE_MIN
        and float(fused_text_neg) < FUSED_TEXT_NEG_MAX
        and bool(positive_matches)
        and not negative_matches
    )
    return {
        "triggered": bool(triggered),
        "reason": SAD_POSITIVE_CONFLICT_REASON if triggered else None,
        "positiveHits": [item["term"] for item in positive_matches],
        "negativeHits": [item["term"] for item in negative_matches],
        "mappedMass": round6(float((text_sentiment or {}).get("mappedMass", 0.0) or 0.0)),
        "emotion4Ready": bool((text_sentiment or {}).get("emotion4Ready", False)),
        "transcriptExcerpt": build_transcript_excerpt_for_hits(asr_text, positive_matches, negative_matches),
    }


def build_decision_with_guard_v2(base_result: dict[str, Any], guard_audit: dict[str, Any]) -> dict[str, Any]:
    base_decision = build_decision_without_guard_v2(base_result)
    if guard_audit.get("triggered"):
        return {
            "code": LOW_CONSISTENCY_CODE,
            "status": LOW_CONSISTENCY_STATUS,
            "reason": SAD_POSITIVE_CONFLICT_REASON,
            "baseEmotionCode": base_decision["baseEmotionCode"],
            "baseConfidence": base_decision["baseConfidence"],
        }
    return base_decision


def build_narrative_prompt_payload(
    task_id: int | None,
    ser_response: dict[str, Any],
    transcript: str | None,
    risk_assessment: dict[str, Any] | None,
    decision: dict[str, Any] | None = None,
    language: str = "zh-CN",
    max_segments: int = 3,
    max_transcript_chars: int = 600,
) -> dict[str, Any]:
    fusion = ser_response.get("fusion") or {}
    overall = ser_response.get("overall") or {}
    resolved_overall_emotion = (decision or {}).get("code") or fusion.get("label") or overall.get("emotionCode")
    baseline_advice = split_advice_text((risk_assessment or {}).get("advice_text"))
    segments = ser_response.get("segments") or []
    top_segments = sorted(
        [
            {
                "startMs": seg.get("startMs"),
                "endMs": seg.get("endMs"),
                "emotion": seg.get("emotionCode"),
                "confidence": seg.get("confidence"),
            }
            for seg in segments
        ],
        key=lambda item: float(item.get("confidence") or 0.0),
        reverse=True,
    )[: max(1, max_segments)]
    transcript_excerpt = None
    if transcript:
        normalized = normalize_text(transcript)
        transcript_excerpt = normalized[:max_transcript_chars] + ("..." if len(normalized) > max_transcript_chars else "")
    return {
        "taskId": task_id,
        "language": language,
        "overallEmotion": resolved_overall_emotion,
        "decision": {
            "code": None if decision is None else decision.get("code"),
            "status": None if decision is None else decision.get("status"),
            "reason": None if decision is None else decision.get("reason"),
            "baseEmotionCode": None if decision is None else decision.get("baseEmotionCode"),
            "baseConfidence": None if decision is None else decision.get("baseConfidence"),
        },
        "risk": {
            "score": None if risk_assessment is None else risk_assessment.get("risk_score"),
            "level": None if risk_assessment is None else risk_assessment.get("risk_level"),
            "pSad": None if risk_assessment is None else risk_assessment.get("p_sad"),
            "pAngry": None if risk_assessment is None else risk_assessment.get("p_angry"),
            "varConf": None if risk_assessment is None else risk_assessment.get("var_conf"),
            "textNeg": None if risk_assessment is None else risk_assessment.get("text_neg"),
        },
        "serFusion": {
            "label": fusion.get("label"),
            "confidence": fusion.get("confidence"),
            "ready": fusion.get("ready"),
            "overallEmotion": overall.get("emotionCode"),
            "overallConfidence": overall.get("confidence"),
        },
        "topSegments": top_segments,
        "transcriptExcerpt": transcript_excerpt,
        "baselineAdvice": baseline_advice,
    }


def analyze_audio_path(
    audio_path: Path,
    segment_ms: int,
    overlap_ms: int,
    reduced_audio_weight: float,
) -> dict[str, Any]:
    raw_stats = ffmpeg_probe(audio_path)
    normalized_path, normalized_audio, sample_rate = normalize_audio(audio_path)
    preprocessed_stats = array_stats(normalized_audio, sample_rate)
    asr_result = transcribe_audio(normalized_path)
    language_hint = asr_result.get("language")
    text_sentiment = app.analyze_text_sentiment(asr_result.get("text", ""), language_hint)
    text_features = build_text_features_java_style(asr_result.get("text", ""), text_sentiment)
    voice_analysis = analyze_voice_segments(
        normalized_audio=normalized_audio,
        sample_rate=sample_rate,
        segment_ms=segment_ms,
        overlap_ms=overlap_ms,
        language_hint=language_hint,
    )
    route_language = (voice_analysis.get("modelMeta") or {}).get("routeLanguage")
    fusion_result = app.predict_fusion_result(voice_analysis["audioSummary"], text_features, route_language)
    ablation = build_ablation_result(voice_analysis, text_features, route_language, reduced_audio_weight)
    base_result = resolve_base_result_v2(voice_analysis, fusion_result)
    before_decision = build_decision_without_guard_v2(base_result)
    guard_audit = build_guard_audit_v2(
        asr_text=asr_result.get("text", ""),
        voice_analysis=voice_analysis,
        text_sentiment=text_sentiment,
        fused_text_neg=text_features.get("text_negative_score", 0.0) or 0.0,
        base_result=base_result,
    )
    after_decision = build_decision_with_guard_v2(base_result, guard_audit)
    narrative_input = build_narrative_prompt_payload(
        task_id=None,
        ser_response={
            "overall": voice_analysis["overall"],
            "segments": voice_analysis["segmentsSimple"],
            "fusion": fusion_result,
        },
        transcript=asr_result.get("text"),
        risk_assessment=None,
        decision=after_decision,
    )
    return {
        "audioPath": str(audio_path),
        "rawAudioStats": raw_stats,
        "preprocessedAudioPath": str(normalized_path),
        "preprocessedAudioStats": preprocessed_stats,
        "asr": asr_result,
        "voiceBranch": voice_analysis,
        "textSentiment": text_sentiment,
        "textFeatures": text_features,
        "fusion": fusion_result,
        "ablation": ablation,
        "baseResult": base_result,
        "beforeDecision": before_decision,
        "guardAudit": guard_audit,
        "afterDecision": after_decision,
        "gemmaStructuredInput": narrative_input,
    }


def build_state_snapshot(item: dict[str, Any], decision: dict[str, Any]) -> dict[str, Any]:
    return {
        "voiceLabel": item["ablation"]["voiceOnly"]["label"],
        "voiceConfidence": item["ablation"]["voiceOnly"]["confidence"],
        "textLabel": item["textSentiment"].get("label"),
        "textNegative": round6(float(item["textSentiment"].get("negativeScore", 0.0) or 0.0)),
        "fusionLabel": item["fusion"].get("label"),
        "fusionConfidence": round6(float(item["fusion"].get("confidence", 0.0) or 0.0)) if item["fusion"].get("confidence") is not None else None,
        "baseEmotionCode": decision.get("baseEmotionCode"),
        "baseConfidence": decision.get("baseConfidence"),
        "decisionCode": decision.get("code"),
        "decisionStatus": decision.get("status"),
    }


def build_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Diagnose sadness bias in the local SER pipeline.")
    parser.add_argument("--backend-base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--admin-username", default="operator")
    parser.add_argument("--admin-password", default="operator123")
    parser.add_argument("--task-id", type=int, default=37, help="Existing misclassified task id to inspect deeply.")
    parser.add_argument("--upload-dir", default=str(Path.home() / "ai-emotion" / "uploads"))
    parser.add_argument(
        "--dataset-root",
        default=str(Path(__file__).resolve().parents[1] / "data" / "datasets" / "CASIA_raw"),
    )
    parser.add_argument("--samples-per-label", type=int, default=5)
    parser.add_argument("--segment-ms", type=int, default=8000)
    parser.add_argument("--overlap-ms", type=int, default=0)
    parser.add_argument("--reduced-audio-weight", type=float, default=DEFAULT_REDUCED_AUDIO_WEIGHT)
    parser.add_argument(
        "--output-json",
        default=str(Path(__file__).resolve().parent / "logs" / "emotion_bias_fix_compare.json"),
    )
    return parser


def main() -> None:
    args = build_argument_parser().parse_args()
    output_path = Path(args.output_json).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    token = admin_login(args.backend_base_url, args.admin_username, args.admin_password)
    task_bundle = fetch_task_bundle(
        base_url=args.backend_base_url,
        task_id=args.task_id,
        token=token,
        upload_dir=Path(args.upload_dir),
    )
    task_audio_path = Path(task_bundle["audioPath"]).resolve()
    task_debug = analyze_audio_path(
        audio_path=task_audio_path,
        segment_ms=args.segment_ms,
        overlap_ms=args.overlap_ms,
        reduced_audio_weight=args.reduced_audio_weight,
    )

    persisted = task_bundle["persisted"]
    persisted_ser = persisted.get("ser") or {}
    persisted_risk = persisted.get("riskAssessment") or {}
    persisted_text_sentiment = persisted.get("textSentiment") or {}
    persisted_transcript = persisted.get("transcript") or ((persisted.get("asr") or {}).get("text"))
    persisted_base_result = {
        "emotionCode": ((persisted_ser.get("fusion") or {}).get("label")) or ((persisted_ser.get("overall") or {}).get("emotionCode")),
        "confidence": round6(
            float(((persisted_ser.get("fusion") or {}).get("confidence", 0.0) or 0.0))
        ) if (persisted_ser.get("fusion") or {}).get("ready") and (persisted_ser.get("fusion") or {}).get("confidence") is not None else (
            round6(float(((persisted_ser.get("overall") or {}).get("confidence", 0.0) or 0.0)))
            if (persisted_ser.get("overall") or {}).get("confidence") is not None
            else None
        ),
    }
    persisted_before_decision = build_decision_without_guard_v2(persisted_base_result)
    persisted_prompt_payload = build_narrative_prompt_payload(
        task_id=args.task_id,
        ser_response=persisted_ser,
        transcript=persisted_transcript,
        risk_assessment=persisted_risk,
        decision=persisted_before_decision,
    )

    selected_samples = select_known_label_samples(Path(args.dataset_root).resolve(), args.samples_per_label)
    batch_rows = []
    for sample in selected_samples:
        analyzed = analyze_audio_path(
            audio_path=Path(sample["path"]),
            segment_ms=args.segment_ms,
            overlap_ms=args.overlap_ms,
            reduced_audio_weight=args.reduced_audio_weight,
        )
        analyzed["expectedLabel"] = sample["expectedLabel"]
        analyzed["datasetSource"] = sample["datasetSource"]
        batch_rows.append(analyzed)

    batch_summary_rows = []
    for index, row in enumerate(batch_rows, start=1):
        before_state = build_state_snapshot(row, row["beforeDecision"])
        after_state = build_state_snapshot(row, row["afterDecision"])
        batch_summary_rows.append(
            {
                "sampleId": f"sample-{index:02d}",
                "group": row["expectedLabel"].lower(),
                "expectedLabel": row["expectedLabel"],
                "asrText": row["asr"]["text"],
                "before": before_state,
                "after": after_state,
                "guardAudit": {
                    "triggered": row["guardAudit"]["triggered"],
                    "reason": row["guardAudit"]["reason"],
                    "positiveHits": row["guardAudit"]["positiveHits"],
                    "negativeHits": row["guardAudit"]["negativeHits"],
                    "mappedMass": row["guardAudit"]["mappedMass"],
                    "emotion4Ready": row["guardAudit"]["emotion4Ready"],
                    "transcriptExcerpt": row["guardAudit"]["transcriptExcerpt"],
                },
                "misclassifiedBefore": before_state["decisionCode"] != row["expectedLabel"],
                "misclassifiedAfter": after_state["decisionCode"] != row["expectedLabel"],
            }
        )
    before_metrics = batch_metrics(
        [{"expectedLabel": row["expectedLabel"], "isMisclassified": row["misclassifiedBefore"]} for row in batch_summary_rows],
        "isMisclassified",
    )
    after_metrics = batch_metrics(
        [{"expectedLabel": row["expectedLabel"], "isMisclassified": row["misclassifiedAfter"]} for row in batch_summary_rows],
        "isMisclassified",
    )

    report = {
        "schemaVersion": "emotion_bias_fix_compare.v1",
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "ruleVersion": RULE_VERSION,
        "misclassifiedSample": {
            "taskId": args.task_id,
            "audioPath": str(task_audio_path),
            "expectedInterpretation": "positive_or_happy_content_should_not_be_forced_to_high_confidence_sad",
            "before": {
                "voiceLabel": ((persisted_ser.get("overall") or {}).get("emotionCode")),
                "voiceConfidence": round6(float(((persisted_ser.get("overall") or {}).get("confidence", 0.0) or 0.0))) if (persisted_ser.get("overall") or {}).get("confidence") is not None else None,
                "textLabel": persisted_text_sentiment.get("label"),
                "textNegative": round6(float(persisted_text_sentiment.get("negativeScore", 0.0) or 0.0)) if persisted_text_sentiment else None,
                "fusionLabel": ((persisted_ser.get("fusion") or {}).get("label")),
                "fusionConfidence": round6(float(((persisted_ser.get("fusion") or {}).get("confidence", 0.0) or 0.0))) if (persisted_ser.get("fusion") or {}).get("confidence") is not None else None,
                "baseEmotionCode": persisted_before_decision.get("baseEmotionCode"),
                "baseConfidence": persisted_before_decision.get("baseConfidence"),
                "decisionCode": persisted_before_decision.get("code"),
                "decisionStatus": persisted_before_decision.get("status"),
            },
            "after": build_state_snapshot(task_debug, task_debug["afterDecision"]),
        },
        "batchSummary": {
            "datasetRoot": str(Path(args.dataset_root).resolve()),
            "samplesPerLabel": args.samples_per_label,
            "totalSamples": len(batch_summary_rows),
            "beforeAccuracy": before_metrics.get("accuracy"),
            "afterAccuracy": after_metrics.get("accuracy"),
            "beforeErrors": before_metrics.get("errors"),
            "afterErrors": after_metrics.get("errors"),
            "byExpectedLabel": {
                label: {
                    "beforeAccuracy": before_metrics["byExpectedLabel"][label]["accuracy"],
                    "afterAccuracy": after_metrics["byExpectedLabel"][label]["accuracy"],
                    "beforeErrors": before_metrics["byExpectedLabel"][label]["errors"],
                    "afterErrors": after_metrics["byExpectedLabel"][label]["errors"],
                }
                for label in sorted(before_metrics["byExpectedLabel"].keys())
            },
        },
        "samples": batch_summary_rows,
    }

    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(
        {
            "outputJson": str(output_path),
            "taskId": args.task_id,
            "taskAudioPath": str(task_audio_path),
            "batchRows": len(batch_summary_rows),
            "beforeAccuracy": before_metrics.get("accuracy"),
            "afterGuardAccuracy": after_metrics.get("accuracy"),
            "taskDecision": task_debug["afterDecision"],
            "taskGuardAudit": task_debug["guardAudit"],
            "persistedPromptPayload": persisted_prompt_payload,
        },
        ensure_ascii=False,
        indent=2,
    ))


if __name__ == "__main__":
    main()

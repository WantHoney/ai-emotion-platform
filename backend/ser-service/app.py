import math
import os
import shutil
import subprocess
import tempfile
import time
from collections import Counter
import logging
import pathlib
from pathlib import Path
from typing import Any

import numpy as np
import soundfile as sf
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from faster_whisper import WhisperModel
from pydantic import BaseModel

app = FastAPI(title="SER Service", version="1.0.0")

TARGET_SAMPLE_RATE = 16000
DEFAULT_SEGMENT_MS = 8000
DEFAULT_OVERLAP_MS = 0
MODEL_NAME = os.getenv("SER_MODEL_NAME", "speechbrain/emotion-recognition-wav2vec2-IEMOCAP")
SER_ENGINE = os.getenv("SER_ENGINE", "speechbrain").strip().lower()
SER_HF_MODEL_DIR = os.getenv("SER_HF_MODEL_DIR", "./wav2vec2_finetuned")
SER_HF_ROUTING = os.getenv("SER_HF_ROUTING", "single").strip().lower()
SER_HF_MODEL_DIR_EN = os.getenv("SER_HF_MODEL_DIR_EN", "").strip()
SER_HF_MODEL_DIR_ZH = os.getenv("SER_HF_MODEL_DIR_ZH", "").strip()
SER_HF_DEFAULT_LANGUAGE = os.getenv("SER_HF_DEFAULT_LANGUAGE", "en").strip().lower()
SER_HF_DEVICE = os.getenv("SER_HF_DEVICE", "auto")
TEXT_ENGINE = os.getenv("TEXT_ENGINE", "hf").strip().lower()
TEXT_HF_ROUTING = os.getenv("TEXT_HF_ROUTING", "language").strip().lower()
TEXT_HF_MODEL = os.getenv("TEXT_HF_MODEL", "").strip()
TEXT_HF_MODEL_EN = os.getenv("TEXT_HF_MODEL_EN", "./text_models/en_roberta_sentiment").strip()
TEXT_HF_MODEL_ZH = os.getenv("TEXT_HF_MODEL_ZH", "./text_models/zh_roberta_sentiment").strip()
TEXT_HF_DEFAULT_LANGUAGE = os.getenv("TEXT_HF_DEFAULT_LANGUAGE", "zh").strip().lower()
TEXT_HF_DEVICE = os.getenv("TEXT_HF_DEVICE", SER_HF_DEVICE).strip().lower()
FUSION_ENABLED = os.getenv("FUSION_ENABLED", "true").strip().lower() not in {"0", "false", "no", "off"}
FUSION_MODEL_DIR = os.getenv("FUSION_MODEL_DIR", "./training/fusion/models/fusion_best").strip()
FUSION_DEVICE = os.getenv("FUSION_DEVICE", SER_HF_DEVICE).strip().lower()
TEXT_LEXICON_NEGATIVE_TERMS_ZH = tuple(
    t.strip() for t in os.getenv("TEXT_LEXICON_NEGATIVE_TERMS_ZH", "难受,抑郁,想哭,崩溃,压力,焦虑,失眠,绝望,没有意义,不想活,害怕,恐惧,孤独,无助").split(",") if t.strip()
)
TEXT_LEXICON_NEGATIVE_TERMS_EN = tuple(
    t.strip() for t in os.getenv("TEXT_LEXICON_NEGATIVE_TERMS_EN", "sad,down,depressed,anxious,panic,stressed,insomnia,hopeless,lonely,helpless").split(",") if t.strip()
)
TEXT_LEXICON_HIGH_RISK_TERMS_ZH = tuple(
    t.strip() for t in os.getenv("TEXT_LEXICON_HIGH_RISK_TERMS_ZH", "不想活,轻生,自杀").split(",") if t.strip()
)
TEXT_LEXICON_HIGH_RISK_TERMS_EN = tuple(
    t.strip() for t in os.getenv("TEXT_LEXICON_HIGH_RISK_TERMS_EN", "suicide,kill myself,end my life").split(",") if t.strip()
)
WHISPER_MODEL = os.getenv("WHISPER_MODEL", "small")
WHISPER_DEVICE = os.getenv("WHISPER_DEVICE", "cpu")
WHISPER_COMPUTE_TYPE = os.getenv("WHISPER_COMPUTE_TYPE", "int8" if WHISPER_DEVICE == "cpu" else "float16")
DEFAULT_WHISPER_CPU_THREADS = 16 if (os.cpu_count() or 0) >= 16 else 12
WHISPER_CPU_THREADS = int(os.getenv("WHISPER_CPU_THREADS", str(DEFAULT_WHISPER_CPU_THREADS)))
MAX_ASR_DURATION_MS = int(os.getenv("MAX_ASR_DURATION_MS", "600000"))
SUPPORTED_AUDIO_EXTENSIONS = {".wav", ".mp3", ".m4a", ".flac", ".ogg", ".webm"}
SUPPORTED_SER_ENGINES = {"speechbrain", "hf_wav2vec2"}
SUPPORTED_SER_HF_ROUTING = {"single", "language"}
SUPPORTED_TEXT_ENGINES = {"hf", "lexicon"}
SUPPORTED_TEXT_ROUTING = {"single", "language"}
if SER_ENGINE not in SUPPORTED_SER_ENGINES:
    logger = logging.getLogger("ser-service")
    logger.warning("unsupported SER_ENGINE=%s, fallback to speechbrain", SER_ENGINE)
    SER_ENGINE = "speechbrain"
if SER_HF_ROUTING not in SUPPORTED_SER_HF_ROUTING:
    logger = logging.getLogger("ser-service")
    logger.warning("unsupported SER_HF_ROUTING=%s, fallback to single", SER_HF_ROUTING)
    SER_HF_ROUTING = "single"
if TEXT_ENGINE not in SUPPORTED_TEXT_ENGINES:
    logger = logging.getLogger("ser-service")
    logger.warning("unsupported TEXT_ENGINE=%s, fallback to lexicon", TEXT_ENGINE)
    TEXT_ENGINE = "lexicon"
if TEXT_HF_ROUTING not in SUPPORTED_TEXT_ROUTING:
    logger = logging.getLogger("ser-service")
    logger.warning("unsupported TEXT_HF_ROUTING=%s, fallback to single", TEXT_HF_ROUTING)
    TEXT_HF_ROUTING = "single"

logger = logging.getLogger("ser-service")
if not logger.handlers:
    logging.basicConfig(level=logging.INFO)

# Model labels -> project labels
# speechbrain iemocap labels: anger, happiness, sadness, neutral
EMOTION_MAP = {
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

_model = None
_hf_model_cache: dict[str, object] = {}
_asr_model = None
_text_model_cache: dict[str, object] = {}
_fusion_runtime = None
_speechbrain_import_error = None
_bundled_ffmpeg_path = None
_asr_warmup_error = None
_ser_warmup_error = None
_text_warmup_error = None
_fusion_warmup_error = None


class TextSentimentRequest(BaseModel):
    text: str
    language: str | None = None

# speechbrain 1.0 expects torchaudio.list_audio_backends on torchaudio>=2
# Newer torchaudio may remove this symbol; provide a minimal compatibility shim.
try:
    import torch as _torch
    import torchaudio as _ta

    if not hasattr(_ta, "list_audio_backends"):
        def _list_audio_backends_compat():
            return ["soundfile"]

        _ta.list_audio_backends = _list_audio_backends_compat

    if not hasattr(_ta, "io"):
        class _TorchaudioIoCompat:
            class StreamReader:  # pragma: no cover - compatibility shim
                pass

        _ta.io = _TorchaudioIoCompat()

    _torchaudio_load = getattr(_ta, "load", None)
    if callable(_torchaudio_load):
        def _load_compat(uri, *args, **kwargs):
            try:
                return _torchaudio_load(uri, *args, **kwargs)
            except ImportError as exc:
                # torchaudio>=2.9 may require optional torchcodec at runtime.
                # For our normalized local wav files, fallback to soundfile keeps
                # speechbrain classify_file path stable without extra dependency.
                if "TorchCodec" not in str(exc):
                    raise

                channels_first = bool(kwargs.get("channels_first", True))
                frame_offset = int(kwargs.get("frame_offset", 0) or 0)
                num_frames = int(kwargs.get("num_frames", -1) or -1)

                audio_np, sample_rate = sf.read(str(uri), dtype="float32", always_2d=True)
                if frame_offset > 0:
                    audio_np = audio_np[frame_offset:]
                if num_frames >= 0:
                    audio_np = audio_np[:num_frames]

                if channels_first:
                    audio_np = np.ascontiguousarray(audio_np.T)
                else:
                    audio_np = np.ascontiguousarray(audio_np)

                return _torch.from_numpy(audio_np), sample_rate

        _ta.load = _load_compat
except Exception:
    pass

try:
    import imageio_ffmpeg

    _bundled_ffmpeg_path = imageio_ffmpeg.get_ffmpeg_exe()
except Exception:
    _bundled_ffmpeg_path = None

# SpeechBrain internally tries to create symlinks when fetching model files.
# On Windows without Developer Mode/Admin privilege this raises WinError 1314.
# Fallback to file copy to keep local development runnable.
_path_symlink_to = pathlib.Path.symlink_to


def _safe_symlink_to(self, target, target_is_directory=False):
    try:
        return _path_symlink_to(self, target, target_is_directory=target_is_directory)
    except OSError as exc:
        if os.name == "nt" and getattr(exc, "winerror", None) == 1314:
            source = pathlib.Path(target)
            if source.is_dir():
                shutil.copytree(source, self, dirs_exist_ok=True)
            else:
                shutil.copy2(source, self)
            return None
        raise


pathlib.Path.symlink_to = _safe_symlink_to

try:
    from speechbrain.inference.interfaces import foreign_class
except Exception as exc:  # pragma: no cover - environment dependent
    foreign_class = None
    _speechbrain_import_error = str(exc)


def get_model():
    model, _ = get_model_for_language(None)
    return model


def normalize_language_hint(language_hint: str | None) -> str | None:
    if not language_hint:
        return None
    value = language_hint.strip().lower()
    if value.startswith("zh"):
        return "zh"
    if value.startswith("en"):
        return "en"
    return None


def resolve_hf_model_dir(language_hint: str | None) -> tuple[str, str]:
    normalized_hint = normalize_language_hint(language_hint)
    if SER_HF_ROUTING != "language":
        return SER_HF_MODEL_DIR, "single"

    if normalized_hint == "zh" and SER_HF_MODEL_DIR_ZH:
        return SER_HF_MODEL_DIR_ZH, "zh"
    if normalized_hint == "en" and SER_HF_MODEL_DIR_EN:
        return SER_HF_MODEL_DIR_EN, "en"

    if SER_HF_DEFAULT_LANGUAGE == "zh" and SER_HF_MODEL_DIR_ZH:
        return SER_HF_MODEL_DIR_ZH, "zh_default"
    if SER_HF_DEFAULT_LANGUAGE == "en" and SER_HF_MODEL_DIR_EN:
        return SER_HF_MODEL_DIR_EN, "en_default"

    if SER_HF_MODEL_DIR_EN:
        return SER_HF_MODEL_DIR_EN, "en_fallback"
    if SER_HF_MODEL_DIR_ZH:
        return SER_HF_MODEL_DIR_ZH, "zh_fallback"
    return SER_HF_MODEL_DIR, "single_fallback"


def get_model_for_language(language_hint: str | None) -> tuple[object, dict]:
    global _model, _hf_model_cache
    normalized_hint = normalize_language_hint(language_hint)

    if SER_ENGINE == "speechbrain":
        if _model is None:
            if foreign_class is None:
                raise RuntimeError(
                    "speechbrain import failed. "
                    "Please reinstall ser-service dependencies. "
                    f"reason={_speechbrain_import_error}"
                )
            _model = foreign_class(
                source=MODEL_NAME,
                pymodule_file="custom_interface.py",
                classname="CustomEncoderWav2vec2Classifier",
            )
        return _model, {
            "model": MODEL_NAME,
            "routeLanguage": None,
            "routingStrategy": "speechbrain_single",
        }

    selected_dir, route_tag = resolve_hf_model_dir(normalized_hint)
    selected_path = str(Path(selected_dir).resolve())
    if selected_path not in _hf_model_cache:
        try:
            from hf_wav2vec2_runtime import HFWav2Vec2Runtime

            _hf_model_cache[selected_path] = HFWav2Vec2Runtime(
                model_dir=selected_path,
                sample_rate=TARGET_SAMPLE_RATE,
                device=SER_HF_DEVICE,
            )
        except Exception as exc:
            raise RuntimeError(
                "hf_wav2vec2 runtime init failed. "
                f"model_dir={selected_path}, reason={exc}"
            ) from exc
    return _hf_model_cache[selected_path], {
        "model": selected_path,
        "routeLanguage": normalized_hint,
        "routingStrategy": route_tag,
    }


def normalize_text_language(language_hint: str | None) -> str | None:
    return normalize_language_hint(language_hint)


def resolve_text_model_ref(language_hint: str | None) -> tuple[str, str]:
    normalized_hint = normalize_text_language(language_hint)
    if TEXT_HF_ROUTING != "language":
        if TEXT_HF_MODEL:
            return TEXT_HF_MODEL, "single"
        if TEXT_HF_DEFAULT_LANGUAGE == "zh":
            return TEXT_HF_MODEL_ZH, "single_default_zh"
        return TEXT_HF_MODEL_EN, "single_default_en"

    if normalized_hint == "zh" and TEXT_HF_MODEL_ZH:
        return TEXT_HF_MODEL_ZH, "zh"
    if normalized_hint == "en" and TEXT_HF_MODEL_EN:
        return TEXT_HF_MODEL_EN, "en"

    if TEXT_HF_DEFAULT_LANGUAGE == "zh" and TEXT_HF_MODEL_ZH:
        return TEXT_HF_MODEL_ZH, "zh_default"
    if TEXT_HF_DEFAULT_LANGUAGE == "en" and TEXT_HF_MODEL_EN:
        return TEXT_HF_MODEL_EN, "en_default"

    if TEXT_HF_MODEL:
        return TEXT_HF_MODEL, "single_fallback"
    if TEXT_HF_MODEL_ZH:
        return TEXT_HF_MODEL_ZH, "zh_fallback"
    return TEXT_HF_MODEL_EN, "en_fallback"


def get_text_runtime_for_language(language_hint: str | None) -> tuple[object, dict[str, Any]]:
    global _text_model_cache
    model_ref, route_tag = resolve_text_model_ref(language_hint)
    normalized_hint = normalize_text_language(language_hint)
    resolved = Path(model_ref)
    cache_key = str(resolved.resolve()) if resolved.exists() else model_ref

    if cache_key not in _text_model_cache:
        from text_sentiment_runtime import TextSentimentRuntime

        _text_model_cache[cache_key] = TextSentimentRuntime(
            model_ref=model_ref,
            device_name=TEXT_HF_DEVICE,
        )

    return _text_model_cache[cache_key], {
        "engine": TEXT_ENGINE,
        "model": cache_key,
        "routeLanguage": normalized_hint,
        "routingStrategy": route_tag,
    }


def _lexicon_terms_by_language(language_hint: str | None) -> tuple[tuple[str, ...], tuple[str, ...]]:
    lang = normalize_text_language(language_hint)
    if lang == "en":
        return TEXT_LEXICON_NEGATIVE_TERMS_EN, TEXT_LEXICON_HIGH_RISK_TERMS_EN
    return TEXT_LEXICON_NEGATIVE_TERMS_ZH, TEXT_LEXICON_HIGH_RISK_TERMS_ZH


def score_text_by_lexicon(text: str, language_hint: str | None) -> dict[str, Any]:
    payload = (text or "").strip()
    if not payload:
        return {
            "label": "neutral",
            "negativeScore": 0.0,
            "scores": {"negative": 0.0, "neutral": 1.0, "positive": 0.0},
            "hits": [],
            "highRiskHit": False,
        }

    terms, high_risk_terms = _lexicon_terms_by_language(language_hint)
    hit_count = 0
    hits: list[str] = []
    content = payload.lower() if normalize_text_language(language_hint) == "en" else payload
    for term in terms:
        normalized_term = term.lower() if normalize_text_language(language_hint) == "en" else term
        count = content.count(normalized_term)
        if count > 0:
            hit_count += count
            hits.append(f"{term}x{count}")

    high_risk_hit = any(
        ((term.lower() in content) if normalize_text_language(language_hint) == "en" else (term in payload))
        for term in high_risk_terms
    )
    negative_score = min(1.0, hit_count / 8.0)
    if high_risk_hit:
        negative_score = max(negative_score, 0.8)

    label = "negative" if negative_score >= 0.55 else "neutral"
    neutral_score = max(0.0, 1.0 - negative_score)
    positive_score = 0.0
    return {
        "label": label,
        "negativeScore": float(negative_score),
        "scores": {
            "negative": float(negative_score),
            "neutral": float(neutral_score),
            "positive": float(positive_score),
        },
        "hits": hits,
        "highRiskHit": high_risk_hit,
    }


def analyze_text_sentiment(text: str, language_hint: str | None) -> dict[str, Any]:
    normalized_hint = normalize_text_language(language_hint)
    if TEXT_ENGINE == "lexicon":
        result = score_text_by_lexicon(text, normalized_hint)
        return {
            **result,
            "meta": {
                "engine": "lexicon",
                "model": "lexicon-v1",
                "routeLanguage": normalized_hint,
                "routingStrategy": "lexicon",
            },
        }

    runtime, meta = get_text_runtime_for_language(normalized_hint)
    model_result = runtime.predict_text(text)
    return {
        **model_result,
        "meta": meta,
    }


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


def shannon_entropy(values: list[float]) -> float:
    eps = 1e-12
    return float(-sum(v * math.log(max(v, eps)) for v in values))


def get_fusion_runtime():
    global _fusion_runtime
    if not FUSION_ENABLED:
        return None
    if _fusion_runtime is None:
        from fusion_runtime import FusionRuntime

        _fusion_runtime = FusionRuntime(
            model_dir=FUSION_MODEL_DIR,
            device_name=FUSION_DEVICE,
        )
    return _fusion_runtime


def _to_optional_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except Exception:
        return None


def build_text_feature_inputs(
    text_negative: float | None,
    text_neutral: float | None,
    text_positive: float | None,
    text_negative_score: float | None,
    text_length_norm: float | None,
) -> dict[str, float]:
    neg = _to_optional_float(text_negative)
    neu = _to_optional_float(text_neutral)
    pos = _to_optional_float(text_positive)
    neg_score = _to_optional_float(text_negative_score)
    length_norm = _to_optional_float(text_length_norm)

    if neg is None:
        neg = neg_score if neg_score is not None else 0.0
    neg = clamp01(neg)

    if neu is None and pos is None:
        neu = max(0.0, 1.0 - neg)
        pos = 0.0
    elif neu is None:
        pos = clamp01(pos)
        neu = max(0.0, 1.0 - neg - pos)
    elif pos is None:
        neu = clamp01(neu)
        pos = max(0.0, 1.0 - neg - neu)
    else:
        neu = clamp01(neu)
        pos = clamp01(pos)

    total = neg + neu + pos
    if total <= 0.0:
        neg, neu, pos = 0.0, 1.0, 0.0
    else:
        neg, neu, pos = neg / total, neu / total, pos / total

    if neg_score is None:
        neg_score = neg
    neg_score = clamp01(neg_score)

    if length_norm is None:
        length_norm = 0.0
    length_norm = clamp01(length_norm)

    return {
        "text_negative": float(neg),
        "text_neutral": float(neu),
        "text_positive": float(pos),
        "text_negative_score": float(neg_score),
        "text_length_norm": float(length_norm),
    }


def build_audio_summary(segments: list[dict[str, Any]]) -> dict[str, float | str]:
    key_map = {
        "ANGRY": "audio_prob_ang",
        "HAPPY": "audio_prob_hap",
        "NEUTRAL": "audio_prob_neu",
        "SAD": "audio_prob_sad",
    }
    audio_probs = {
        "audio_prob_ang": 0.0,
        "audio_prob_hap": 0.0,
        "audio_prob_neu": 0.0,
        "audio_prob_sad": 0.0,
    }
    max_conf = 0.0
    total_weight = 0.0

    for segment in segments:
        emotion = str(segment.get("emotionCode") or "").upper()
        confidence = clamp01(_to_optional_float(segment.get("confidence")) or 0.0)
        max_conf = max(max_conf, confidence)
        mapped_key = key_map.get(emotion)
        if not mapped_key:
            continue
        weight = confidence if confidence > 0.0 else 1e-6
        total_weight += weight
        audio_probs[mapped_key] += weight

    if total_weight <= 0.0:
        audio_probs["audio_prob_neu"] = 1.0
    else:
        for key in list(audio_probs):
            audio_probs[key] = float(audio_probs[key] / total_weight)

    entropy = shannon_entropy(
        [
            audio_probs["audio_prob_ang"],
            audio_probs["audio_prob_hap"],
            audio_probs["audio_prob_neu"],
            audio_probs["audio_prob_sad"],
        ]
    )
    dominant_key = max(audio_probs, key=audio_probs.get)
    dominant_label_map = {
        "audio_prob_ang": "ANGRY",
        "audio_prob_hap": "HAPPY",
        "audio_prob_neu": "NEUTRAL",
        "audio_prob_sad": "SAD",
    }
    return {
        **audio_probs,
        "audio_confidence": float(max_conf),
        "audio_entropy": float(entropy),
        "dominantEmotion": dominant_label_map[dominant_key],
    }


def build_fusion_features(audio_summary: dict[str, Any], text_features: dict[str, Any]) -> dict[str, float]:
    return {
        "audio_prob_ang": float(audio_summary.get("audio_prob_ang", 0.0) or 0.0),
        "audio_prob_hap": float(audio_summary.get("audio_prob_hap", 0.0) or 0.0),
        "audio_prob_neu": float(audio_summary.get("audio_prob_neu", 0.0) or 0.0),
        "audio_prob_sad": float(audio_summary.get("audio_prob_sad", 0.0) or 0.0),
        "audio_confidence": float(audio_summary.get("audio_confidence", 0.0) or 0.0),
        "audio_entropy": float(audio_summary.get("audio_entropy", 0.0) or 0.0),
        "text_negative": float(text_features.get("text_negative", 0.0) or 0.0),
        "text_neutral": float(text_features.get("text_neutral", 1.0) or 0.0),
        "text_positive": float(text_features.get("text_positive", 0.0) or 0.0),
        "text_negative_score": float(text_features.get("text_negative_score", 0.0) or 0.0),
        "text_length_norm": float(text_features.get("text_length_norm", 0.0) or 0.0),
    }


def predict_fusion_result(audio_summary: dict[str, Any], text_features: dict[str, Any]) -> dict[str, Any]:
    if not FUSION_ENABLED:
        return {"enabled": False, "ready": False}
    try:
        runtime = get_fusion_runtime()
        if runtime is None:
            return {"enabled": False, "ready": False}
        features = build_fusion_features(audio_summary, text_features)
        predicted = runtime.predict(features)
        scores_project: dict[str, float] = {}
        for label, score in predicted.get("scores", {}).items():
            scores_project[normalize_label(label)] = float(score)
        return {
            "enabled": True,
            "ready": True,
            "labelRaw": predicted.get("label"),
            "label": normalize_label(str(predicted.get("label") or "")),
            "confidence": float(predicted.get("confidence", 0.0)),
            "temperature": float(predicted.get("temperature", 1.0)),
            "scoresRaw": predicted.get("scores", {}),
            "scores": scores_project,
            "features": features,
        }
    except Exception as exc:
        logger.warning("fusion predict failed: %s", exc)
        return {
            "enabled": True,
            "ready": False,
            "error": str(exc),
        }


def get_asr_model():
    global _asr_model
    if _asr_model is None:
        _asr_model = WhisperModel(
            WHISPER_MODEL,
            device=WHISPER_DEVICE,
            compute_type=WHISPER_COMPUTE_TYPE,
            cpu_threads=WHISPER_CPU_THREADS,
        )
    return _asr_model


def resolve_ffmpeg_binary() -> str | None:
    ffmpeg_path = shutil.which("ffmpeg")
    if ffmpeg_path:
        return ffmpeg_path
    return _bundled_ffmpeg_path


def run_ffmpeg_convert(input_path: Path, output_path: Path):
    ffmpeg_path = resolve_ffmpeg_binary()
    if not ffmpeg_path:
        raise HTTPException(status_code=500, detail="ffmpeg not found in ser-service")

    cmd = [
        ffmpeg_path,
        "-y",
        "-i",
        str(input_path),
        "-ac",
        "1",
        "-ar",
        str(TARGET_SAMPLE_RATE),
        "-f",
        "wav",
        str(output_path),
    ]
    # Keep ffmpeg output as bytes to avoid locale decode crashes on Windows.
    proc = subprocess.run(cmd, capture_output=True)
    if proc.returncode != 0:
        stderr_text = (proc.stderr or b"").decode("utf-8", errors="replace").strip()
        raise HTTPException(status_code=400, detail=f"ffmpeg convert failed: {stderr_text}")


def normalize_label(raw_label: str) -> str:
    if raw_label is None:
        return "NEUTRAL"
    key = raw_label.strip().lower()
    return EMOTION_MAP.get(key, "NEUTRAL")


def analyze_segment(model, segment_wav_path: Path) -> tuple[str, float]:
    if SER_ENGINE == "speechbrain":
        out_prob, score, index, text_lab = model.classify_file(str(segment_wav_path))
        label_text = text_lab[0] if isinstance(text_lab, list) else str(text_lab)
        mapped_label = normalize_label(label_text)
        confidence = float(score[0]) if hasattr(score, "__len__") else float(score)
        confidence = max(0.0, min(1.0, confidence))
        return mapped_label, confidence

    label_text, confidence = model.predict_file(segment_wav_path)
    mapped_label = normalize_label(label_text)
    return mapped_label, max(0.0, min(1.0, float(confidence)))


def validate_audio_extension(filename: str | None):
    if not filename:
        return
    ext = Path(filename).suffix.lower()
    if ext and ext not in SUPPORTED_AUDIO_EXTENSIONS:
        raise HTTPException(
            status_code=400,
            detail=f"unsupported file extension '{ext}', supported: {', '.join(sorted(SUPPORTED_AUDIO_EXTENSIONS))}",
        )


def aggregate_overall(segments: list[dict]) -> dict:
    if not segments:
        return {"emotionCode": "NEUTRAL", "confidence": 0.0}

    votes = Counter(seg["emotionCode"] for seg in segments)
    best_count = max(votes.values())
    top_labels = [label for label, cnt in votes.items() if cnt == best_count]

    if len(top_labels) == 1:
        best_label = top_labels[0]
    else:
        # tie-break by highest confidence among tied labels
        best_label = max(
            top_labels,
            key=lambda label: max(seg["confidence"] for seg in segments if seg["emotionCode"] == label),
        )

    label_confidences = [seg["confidence"] for seg in segments if seg["emotionCode"] == best_label]
    confidence = float(sum(label_confidences) / len(label_confidences)) if label_confidences else 0.0
    return {"emotionCode": best_label, "confidence": round(confidence, 6)}


@app.get("/health")
def health():
    if SER_ENGINE == "speechbrain":
        ser_model_name = MODEL_NAME
        ser_model_ready = foreign_class is not None
        ser_model_import_error = _speechbrain_import_error
    else:
        ser_model_name = SER_HF_MODEL_DIR
        ser_model_ready = Path(SER_HF_MODEL_DIR).exists()
        ser_model_import_error = None

    return {
        "status": "ok",
        "serEngine": SER_ENGINE,
        "serModel": ser_model_name,
        "serModelReady": ser_model_ready,
        "serModelImportError": ser_model_import_error,
        "serModelWarmupError": _ser_warmup_error,
        "serHfDevice": SER_HF_DEVICE if SER_ENGINE == "hf_wav2vec2" else None,
        "serHfRouting": SER_HF_ROUTING if SER_ENGINE == "hf_wav2vec2" else None,
        "serHfModelDir": SER_HF_MODEL_DIR if SER_ENGINE == "hf_wav2vec2" else None,
        "serHfModelDirEn": SER_HF_MODEL_DIR_EN if SER_ENGINE == "hf_wav2vec2" else None,
        "serHfModelDirZh": SER_HF_MODEL_DIR_ZH if SER_ENGINE == "hf_wav2vec2" else None,
        "serHfDefaultLanguage": SER_HF_DEFAULT_LANGUAGE if SER_ENGINE == "hf_wav2vec2" else None,
        "serHfModelCacheSize": len(_hf_model_cache) if SER_ENGINE == "hf_wav2vec2" else 0,
        "ffmpegReady": resolve_ffmpeg_binary() is not None,
        "ffmpegPath": resolve_ffmpeg_binary(),
        "asrModel": WHISPER_MODEL,
        "asrModelReady": _asr_model is not None,
        "asrModelWarmupError": _asr_warmup_error,
        "asrDevice": WHISPER_DEVICE,
        "asrComputeType": WHISPER_COMPUTE_TYPE,
        "asrCpuThreads": WHISPER_CPU_THREADS,
        "textEngine": TEXT_ENGINE,
        "textHfRouting": TEXT_HF_ROUTING if TEXT_ENGINE == "hf" else None,
        "textHfModel": TEXT_HF_MODEL if TEXT_ENGINE == "hf" else None,
        "textHfModelEn": TEXT_HF_MODEL_EN if TEXT_ENGINE == "hf" else None,
        "textHfModelZh": TEXT_HF_MODEL_ZH if TEXT_ENGINE == "hf" else None,
        "textHfDefaultLanguage": TEXT_HF_DEFAULT_LANGUAGE if TEXT_ENGINE == "hf" else None,
        "textHfDevice": TEXT_HF_DEVICE if TEXT_ENGINE == "hf" else None,
        "textModelCacheSize": len(_text_model_cache),
        "textModelWarmupError": _text_warmup_error,
        "fusionEnabled": FUSION_ENABLED,
        "fusionModelDir": str(Path(FUSION_MODEL_DIR).resolve()) if FUSION_ENABLED else None,
        "fusionDevice": FUSION_DEVICE if FUSION_ENABLED else None,
        "fusionReady": (_fusion_runtime is not None) if FUSION_ENABLED else False,
        "fusionWarmupError": _fusion_warmup_error,
    }


@app.get("/warmup")
def warmup():
    global _asr_warmup_error, _ser_warmup_error, _text_warmup_error, _fusion_warmup_error
    _ser_warmup_error = None
    _text_warmup_error = None
    _fusion_warmup_error = None
    try:
        if SER_ENGINE == "hf_wav2vec2" and SER_HF_ROUTING == "language":
            warmup_hints = [None, "zh", "en"]
            loaded = set()
            for hint in warmup_hints:
                _, meta = get_model_for_language(hint)
                loaded.add(meta["model"])
            logger.info("hf_wav2vec2 warmup loaded models=%s", sorted(loaded))
        else:
            get_model()
    except Exception as exc:
        _ser_warmup_error = str(exc)
        raise HTTPException(status_code=503, detail=f"ser warmup failed: {exc}")

    text_ready = False
    try:
        if TEXT_ENGINE == "hf" and TEXT_HF_ROUTING == "language":
            loaded = set()
            for hint in [None, "zh", "en"]:
                _, meta = get_text_runtime_for_language(hint)
                loaded.add(meta["model"])
            logger.info("text hf warmup loaded models=%s", sorted(loaded))
        elif TEXT_ENGINE == "hf":
            get_text_runtime_for_language(None)
        text_ready = True
    except Exception as exc:
        _text_warmup_error = str(exc)
        logger.warning("text model warmup failed: %s", _text_warmup_error)

    asr_ready = False
    _asr_warmup_error = None
    try:
        get_asr_model()
        asr_ready = True
    except Exception as exc:
        _asr_warmup_error = str(exc)
        logger.warning("asr model warmup failed: %s", _asr_warmup_error)

    fusion_ready = not FUSION_ENABLED
    if FUSION_ENABLED:
        try:
            get_fusion_runtime()
            fusion_ready = True
        except Exception as exc:
            _fusion_warmup_error = str(exc)
            logger.warning("fusion model warmup failed: %s", _fusion_warmup_error)

    return {
        "status": "ok" if asr_ready and text_ready and fusion_ready else "degraded",
        "serEngine": SER_ENGINE,
        "serModel": MODEL_NAME if SER_ENGINE == "speechbrain" else SER_HF_MODEL_DIR,
        "serModelReady": True,
        "serModelWarmupError": _ser_warmup_error,
        "asrModel": WHISPER_MODEL,
        "asrModelReady": asr_ready,
        "asrModelWarmupError": _asr_warmup_error,
        "textEngine": TEXT_ENGINE,
        "textModelReady": text_ready,
        "textModelWarmupError": _text_warmup_error,
        "fusionEnabled": FUSION_ENABLED,
        "fusionModelReady": fusion_ready,
        "fusionModelWarmupError": _fusion_warmup_error,
    }


@app.post("/ser/analyze")
async def analyze(
    file: UploadFile = File(...),
    segment_ms: int = Form(DEFAULT_SEGMENT_MS),
    overlap_ms: int = Form(DEFAULT_OVERLAP_MS),
    language_hint: str | None = Form(default=None),
    text_negative: float | None = Form(default=None),
    text_neutral: float | None = Form(default=None),
    text_positive: float | None = Form(default=None),
    text_negative_score: float | None = Form(default=None),
    text_length_norm: float | None = Form(default=None),
):
    if segment_ms <= 0:
        raise HTTPException(status_code=400, detail="segment_ms must be > 0")
    if overlap_ms < 0 or overlap_ms >= segment_ms:
        raise HTTPException(status_code=400, detail="overlap_ms must be >= 0 and < segment_ms")

    try:
        model, model_meta = get_model_for_language(language_hint)
    except Exception as exc:
        raise HTTPException(status_code=503, detail=str(exc))

    with tempfile.TemporaryDirectory(prefix="ser-") as td:
        tmp_dir = Path(td)
        raw_path = tmp_dir / (file.filename or "input.audio")
        wav_path = tmp_dir / "normalized.wav"

        content = await file.read()
        raw_path.write_bytes(content)

        run_ffmpeg_convert(raw_path, wav_path)

        audio, sr = sf.read(str(wav_path), dtype="float32")
        if audio.ndim > 1:
            audio = np.mean(audio, axis=1)

        duration_ms = int(math.floor((len(audio) / sr) * 1000))
        if len(audio) == 0:
            raise HTTPException(status_code=400, detail="empty audio after conversion")

        seg_samples = int(sr * (segment_ms / 1000.0))
        hop_samples = int(sr * ((segment_ms - overlap_ms) / 1000.0))
        hop_samples = max(hop_samples, 1)

        segments = []
        start = 0
        index = 0
        while start < len(audio):
            end = min(start + seg_samples, len(audio))
            if end <= start:
                break

            chunk = audio[start:end]
            chunk_path = tmp_dir / f"seg_{index}.wav"
            sf.write(str(chunk_path), chunk, sr, subtype="PCM_16")

            emotion_code, confidence = analyze_segment(model, chunk_path)

            start_ms = int(math.floor(start / sr * 1000))
            end_ms = int(math.floor(end / sr * 1000))
            segments.append(
                {
                    "startMs": start_ms,
                    "endMs": end_ms,
                    "emotionCode": emotion_code,
                    "confidence": round(confidence, 6),
                }
            )

            if end == len(audio):
                break
            start += hop_samples
            index += 1

        overall = aggregate_overall(segments)
        audio_summary = build_audio_summary(segments)
        text_features = build_text_feature_inputs(
            text_negative=text_negative,
            text_neutral=text_neutral,
            text_positive=text_positive,
            text_negative_score=text_negative_score,
            text_length_norm=text_length_norm,
        )
        fusion_result = predict_fusion_result(audio_summary, text_features)
        return {
            "overall": overall,
            "segments": segments,
            "audioSummary": audio_summary,
            "textFeatures": text_features,
            "fusion": fusion_result,
            "meta": {
                "model": model_meta["model"],
                "engine": SER_ENGINE,
                "routeLanguage": model_meta["routeLanguage"],
                "routingStrategy": model_meta["routingStrategy"],
                "languageHint": normalize_language_hint(language_hint),
                "sampleRate": TARGET_SAMPLE_RATE,
                "durationMs": duration_ms,
                "fusionEnabled": FUSION_ENABLED,
            },
        }


@app.post("/asr/transcribe")
async def transcribe(file: UploadFile = File(...)):
    validate_audio_extension(file.filename)

    start_time = time.perf_counter()
    model = get_asr_model()

    with tempfile.TemporaryDirectory(prefix="asr-") as td:
        tmp_dir = Path(td)
        raw_path = tmp_dir / (file.filename or "input.audio")
        wav_path = tmp_dir / "normalized.wav"

        content = await file.read()
        raw_path.write_bytes(content)

        run_ffmpeg_convert(raw_path, wav_path)

        audio, sr = sf.read(str(wav_path), dtype="float32")
        if audio.ndim > 1:
            audio = np.mean(audio, axis=1)

        duration_ms = int(math.floor((len(audio) / sr) * 1000))
        if duration_ms <= 0:
            raise HTTPException(status_code=400, detail="empty audio after conversion")
        if duration_ms > MAX_ASR_DURATION_MS:
            raise HTTPException(
                status_code=400,
                detail=f"audio too long: {duration_ms}ms, max allowed is {MAX_ASR_DURATION_MS}ms",
            )

        segments_iter, info = model.transcribe(str(wav_path), vad_filter=True)
        segments = [
            {
                "startMs": int(math.floor(seg.start * 1000)),
                "endMs": int(math.floor(seg.end * 1000)),
                "text": seg.text.strip(),
            }
            for seg in segments_iter
        ]

    elapsed_ms = int(math.floor((time.perf_counter() - start_time) * 1000))
    language = (info.language or "").lower()
    if language.startswith("zh"):
        language = "zh"
    elif language.startswith("en"):
        language = "en"

    logger.info(
        "asr_transcribe finished model=%s compute_type=%s cpu_threads=%s durationMs=%s elapsedMs=%s segments=%s language=%s",
        WHISPER_MODEL,
        WHISPER_COMPUTE_TYPE,
        WHISPER_CPU_THREADS,
        duration_ms,
        elapsed_ms,
        len(segments),
        language,
    )

    return {
        "text": " ".join(seg["text"] for seg in segments).strip(),
        "language": language,
        "segments": segments,
        "meta": {
            "model": WHISPER_MODEL,
            "durationMs": duration_ms,
        },
    }


@app.post("/text/sentiment")
async def text_sentiment(payload: TextSentimentRequest):
    text = (payload.text or "").strip()
    language_hint = normalize_text_language(payload.language)
    if not text:
        return {
            "label": "neutral",
            "negativeScore": 0.0,
            "scores": {"negative": 0.0, "neutral": 1.0, "positive": 0.0},
            "meta": {
                "engine": TEXT_ENGINE,
                "model": "empty",
                "routeLanguage": language_hint,
                "routingStrategy": "empty",
            },
        }

    try:
        result = analyze_text_sentiment(text, language_hint)
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"text sentiment failed: {exc}")

    return result

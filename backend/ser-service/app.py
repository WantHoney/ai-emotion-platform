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

import numpy as np
import soundfile as sf
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from faster_whisper import WhisperModel

app = FastAPI(title="SER Service", version="1.0.0")

TARGET_SAMPLE_RATE = 16000
DEFAULT_SEGMENT_MS = 8000
DEFAULT_OVERLAP_MS = 0
MODEL_NAME = os.getenv("SER_MODEL_NAME", "speechbrain/emotion-recognition-wav2vec2-IEMOCAP")
WHISPER_MODEL = os.getenv("WHISPER_MODEL", "small")
WHISPER_DEVICE = os.getenv("WHISPER_DEVICE", "cpu")
WHISPER_COMPUTE_TYPE = os.getenv("WHISPER_COMPUTE_TYPE", "int8" if WHISPER_DEVICE == "cpu" else "float16")
DEFAULT_WHISPER_CPU_THREADS = 16 if (os.cpu_count() or 0) >= 16 else 12
WHISPER_CPU_THREADS = int(os.getenv("WHISPER_CPU_THREADS", str(DEFAULT_WHISPER_CPU_THREADS)))
MAX_ASR_DURATION_MS = int(os.getenv("MAX_ASR_DURATION_MS", "600000"))
SUPPORTED_AUDIO_EXTENSIONS = {".wav", ".mp3", ".m4a", ".flac", ".ogg", ".webm"}

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
_asr_model = None
_speechbrain_import_error = None
_bundled_ffmpeg_path = None

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
    global _model
    if foreign_class is None:
        raise RuntimeError(
            "speechbrain import failed. "
            "Please reinstall ser-service dependencies. "
            f"reason={_speechbrain_import_error}"
        )
    if _model is None:
        _model = foreign_class(
            source=MODEL_NAME,
            pymodule_file="custom_interface.py",
            classname="CustomEncoderWav2vec2Classifier",
        )
    return _model


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
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise HTTPException(status_code=400, detail=f"ffmpeg convert failed: {proc.stderr.strip()}")


def normalize_label(raw_label: str) -> str:
    if raw_label is None:
        return "NEUTRAL"
    key = raw_label.strip().lower()
    return EMOTION_MAP.get(key, "NEUTRAL")


def analyze_segment(model, segment_wav_path: Path) -> tuple[str, float]:
    out_prob, score, index, text_lab = model.classify_file(str(segment_wav_path))

    label_text = text_lab[0] if isinstance(text_lab, list) else str(text_lab)
    mapped_label = normalize_label(label_text)

    confidence = float(score[0]) if hasattr(score, "__len__") else float(score)
    confidence = max(0.0, min(1.0, confidence))
    return mapped_label, confidence


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
    return {
        "status": "ok",
        "serModel": MODEL_NAME,
        "serModelReady": foreign_class is not None,
        "serModelImportError": _speechbrain_import_error,
        "ffmpegReady": resolve_ffmpeg_binary() is not None,
        "ffmpegPath": resolve_ffmpeg_binary(),
        "asrModel": WHISPER_MODEL,
        "asrDevice": WHISPER_DEVICE,
        "asrComputeType": WHISPER_COMPUTE_TYPE,
        "asrCpuThreads": WHISPER_CPU_THREADS,
    }


@app.get("/warmup")
def warmup():
    try:
        get_model()
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"ser warmup failed: {exc}")

    return {
        "status": "ok",
        "serModel": MODEL_NAME,
        "serModelReady": True,
    }


@app.post("/ser/analyze")
async def analyze(
    file: UploadFile = File(...),
    segment_ms: int = Form(DEFAULT_SEGMENT_MS),
    overlap_ms: int = Form(DEFAULT_OVERLAP_MS),
):
    if segment_ms <= 0:
        raise HTTPException(status_code=400, detail="segment_ms must be > 0")
    if overlap_ms < 0 or overlap_ms >= segment_ms:
        raise HTTPException(status_code=400, detail="overlap_ms must be >= 0 and < segment_ms")

    try:
        model = get_model()
    except RuntimeError as exc:
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
        return {
            "overall": overall,
            "segments": segments,
            "meta": {
                "model": MODEL_NAME,
                "sampleRate": TARGET_SAMPLE_RATE,
                "durationMs": duration_ms,
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

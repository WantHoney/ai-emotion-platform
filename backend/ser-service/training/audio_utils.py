import math
from pathlib import Path

import numpy as np
import soundfile as sf


def to_mono(audio: np.ndarray) -> np.ndarray:
    if audio.ndim == 1:
        return audio
    if audio.ndim == 2:
        return np.mean(audio, axis=1)
    raise ValueError(f"unsupported audio ndim: {audio.ndim}")


def linear_resample(audio: np.ndarray, src_sr: int, dst_sr: int) -> np.ndarray:
    if src_sr == dst_sr:
        return audio.astype(np.float32, copy=False)
    if src_sr <= 0 or dst_sr <= 0:
        raise ValueError(f"invalid sample rates src_sr={src_sr}, dst_sr={dst_sr}")
    if audio.size == 0:
        return audio.astype(np.float32, copy=False)

    duration_sec = audio.shape[0] / float(src_sr)
    dst_len = max(1, int(math.floor(duration_sec * dst_sr)))

    src_times = np.arange(audio.shape[0], dtype=np.float64) / float(src_sr)
    dst_times = np.arange(dst_len, dtype=np.float64) / float(dst_sr)
    dst_audio = np.interp(dst_times, src_times, audio.astype(np.float64, copy=False))
    return dst_audio.astype(np.float32, copy=False)


def load_audio_mono_resample(
    file_path: str | Path,
    target_sr: int = 16000,
    max_duration_sec: float | None = None,
) -> np.ndarray:
    file_path = Path(file_path)
    audio, src_sr = sf.read(str(file_path), dtype="float32", always_2d=False)
    audio = to_mono(audio)
    if src_sr != target_sr:
        audio = linear_resample(audio, src_sr=src_sr, dst_sr=target_sr)

    if max_duration_sec is not None and max_duration_sec > 0:
        max_samples = int(max_duration_sec * target_sr)
        if audio.shape[0] > max_samples:
            audio = audio[:max_samples]

    if audio.size == 0:
        raise ValueError(f"empty audio loaded from {file_path}")
    return audio.astype(np.float32, copy=False)

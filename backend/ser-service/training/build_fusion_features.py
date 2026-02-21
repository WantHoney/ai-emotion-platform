import argparse
import csv
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch
from faster_whisper import WhisperModel
from transformers import AutoFeatureExtractor, Wav2Vec2ForSequenceClassification

from audio_utils import load_audio_mono_resample

SCRIPT_DIR = Path(__file__).resolve().parent
SERVICE_ROOT = SCRIPT_DIR.parent
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))


CANONICAL_EMOTIONS = ("ANG", "HAP", "NEU", "SAD")


def normalize_language(language: str | None) -> str | None:
    if language is None:
        return None
    key = language.strip().lower()
    if not key:
        return None
    if key.startswith("zh"):
        return "zh"
    if key.startswith("en"):
        return "en"
    return None


def normalize_emotion(label: str | None) -> str:
    key = (label or "").strip().lower()
    if key in {"ang", "angry", "anger"}:
        return "ANG"
    if key in {"hap", "happy", "happiness", "exc", "excited"}:
        return "HAP"
    if key in {"neu", "neutral", "calm"}:
        return "NEU"
    if key in {"sad", "sadness"}:
        return "SAD"
    raise ValueError(f"unsupported emotion label: {label}")


def infer_language_from_path(path: str, fallback: str = "en") -> str:
    key = path.replace("\\", "/").lower()
    if "casia" in key or "/zh/" in key or "/cn/" in key:
        return "zh"
    if "iemocap" in key or "ravdess" in key or "/en/" in key:
        return "en"
    return fallback


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build multimodal fusion features from manifests.")
    parser.add_argument("--train-manifest", required=True)
    parser.add_argument("--val-manifest", required=True)
    parser.add_argument("--test-manifest", default="")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--audio-model-en", required=True)
    parser.add_argument("--audio-model-zh", required=True)
    parser.add_argument("--text-model-en", default="./text_models/en_roberta_sentiment")
    parser.add_argument("--text-model-zh", default="./text_models/zh_roberta_sentiment")
    parser.add_argument("--sample-rate", type=int, default=16000)
    parser.add_argument("--audio-device", default="auto", choices=["auto", "cpu", "cuda"])
    parser.add_argument("--text-device", default="auto", choices=["auto", "cpu", "cuda"])
    parser.add_argument("--default-language", default="en", choices=["en", "zh"])
    parser.add_argument("--skip-asr", action="store_true")
    parser.add_argument("--whisper-model", default="small")
    parser.add_argument("--whisper-device", default="cpu", choices=["cpu", "cuda"])
    parser.add_argument("--whisper-compute-type", default="")
    parser.add_argument("--whisper-cpu-threads", type=int, default=8)
    parser.add_argument("--asr-cache-json", default="")
    parser.add_argument("--max-samples", type=int, default=0)
    return parser.parse_args()


@dataclass(frozen=True)
class ManifestRow:
    path: str
    label: str
    language: str | None
    transcript: str


def read_manifest(path: str | Path) -> list[ManifestRow]:
    rows: list[ManifestRow] = []
    with Path(path).open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            wav_path = (row.get("path") or "").strip()
            if not wav_path:
                continue
            label = normalize_emotion(row.get("label"))
            language = normalize_language(row.get("language"))
            transcript = (row.get("transcript") or "").strip()
            rows.append(
                ManifestRow(
                    path=wav_path,
                    label=label,
                    language=language,
                    transcript=transcript,
                )
            )
    if not rows:
        raise ValueError(f"manifest is empty: {path}")
    return rows


class AcousticRuntime:
    def __init__(self, model_dir: str, sample_rate: int, device: str) -> None:
        model_path = str(Path(model_dir).resolve())
        if device == "auto":
            torch_device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        elif device == "cuda":
            if not torch.cuda.is_available():
                raise RuntimeError("audio-device=cuda but CUDA unavailable")
            torch_device = torch.device("cuda")
        else:
            torch_device = torch.device("cpu")

        self.sample_rate = sample_rate
        self.device = torch_device
        self.feature_extractor = AutoFeatureExtractor.from_pretrained(model_path)
        self.model = Wav2Vec2ForSequenceClassification.from_pretrained(model_path).to(self.device).eval()
        config = self.model.config
        if isinstance(config.id2label, dict) and config.id2label:
            self.id2label = {int(k): str(v) for k, v in config.id2label.items()}
        elif isinstance(config.label2id, dict) and config.label2id:
            self.id2label = {int(v): str(k) for k, v in config.label2id.items()}
        else:
            raise RuntimeError(f"model has no label mapping: {model_path}")

    def predict_probs(self, wav_path: str) -> dict[str, float]:
        audio = load_audio_mono_resample(wav_path, target_sr=self.sample_rate, max_duration_sec=None)
        encoded = self.feature_extractor(
            [audio],
            sampling_rate=self.sample_rate,
            return_tensors="pt",
            padding=True,
            return_attention_mask=True,
        )
        inputs = {"input_values": encoded["input_values"].to(self.device)}
        attention_mask = encoded.get("attention_mask")
        if attention_mask is not None:
            inputs["attention_mask"] = attention_mask.to(self.device)

        with torch.no_grad():
            logits = self.model(**inputs).logits
            probs = torch.softmax(logits, dim=-1).detach().cpu().numpy()[0]

        mapped = {emotion: 0.0 for emotion in CANONICAL_EMOTIONS}
        for idx, prob in enumerate(probs):
            label = normalize_emotion(self.id2label.get(idx, str(idx)))
            mapped[label] = float(prob)
        return mapped


class TextRuntime:
    def __init__(self, model_en: str, model_zh: str, device: str) -> None:
        from text_sentiment_runtime import TextSentimentRuntime

        self.runtime_en = TextSentimentRuntime(model_ref=model_en, device_name=device)
        self.runtime_zh = TextSentimentRuntime(model_ref=model_zh, device_name=device)

    def score(self, text: str, language: str) -> dict:
        runtime = self.runtime_zh if language == "zh" else self.runtime_en
        return runtime.predict_text(text)


class AsrRuntime:
    def __init__(self, model_name: str, device: str, compute_type: str, cpu_threads: int) -> None:
        if not compute_type:
            compute_type = "int8" if device == "cpu" else "float16"
        self.model = WhisperModel(
            model_name,
            device=device,
            compute_type=compute_type,
            cpu_threads=cpu_threads,
        )

    def _transcribe_once(self, wav_path: str, vad_filter: bool, language: str | None) -> tuple[str, str | None]:
        kwargs: dict[str, object] = {"vad_filter": vad_filter}
        if language:
            kwargs["language"] = language
        segments_iter, info = self.model.transcribe(wav_path, **kwargs)
        text = " ".join((seg.text or "").strip() for seg in segments_iter).strip()
        return text, normalize_language(getattr(info, "language", None))

    def transcribe(self, wav_path: str, language_hint: str | None = None) -> tuple[str, str | None]:
        hint = normalize_language(language_hint)
        try:
            text, detected_language = self._transcribe_once(wav_path, vad_filter=True, language=hint)
            return text, detected_language or hint
        except ValueError as exc:
            if "empty sequence" not in str(exc).lower():
                raise
            print(f"[warn] asr empty-sequence with VAD for {wav_path}; retry without VAD")

        text, detected_language = self._transcribe_once(wav_path, vad_filter=False, language=hint)
        return text, detected_language or hint


def shannon_entropy(values: list[float]) -> float:
    eps = 1e-12
    return float(-sum(p * math.log(max(p, eps)) for p in values))


def build_writer(path: Path) -> tuple[csv.DictWriter, object]:
    fieldnames = [
        "path",
        "label",
        "label_id",
        "language",
        "transcript",
        "audio_prob_ang",
        "audio_prob_hap",
        "audio_prob_neu",
        "audio_prob_sad",
        "audio_confidence",
        "audio_entropy",
        "text_negative",
        "text_neutral",
        "text_positive",
        "text_negative_score",
        "text_top_label_raw",
        "text_top_conf_raw",
        "text_length",
        "text_length_norm",
    ]
    f = path.open("w", newline="", encoding="utf-8")
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    return writer, f


def load_asr_cache(path: str) -> dict[str, dict[str, str]]:
    if not path:
        return {}
    p = Path(path)
    if not p.exists():
        return {}
    with p.open("r", encoding="utf-8") as f:
        raw = json.load(f)
    out: dict[str, dict[str, str]] = {}
    for key, value in raw.items():
        if isinstance(value, dict):
            out[str(key)] = {
                "text": str(value.get("text", "")),
                "language": str(value.get("language", "")),
            }
    return out


def save_asr_cache(path: str, cache: dict[str, dict[str, str]]) -> None:
    if not path:
        return
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    with p.open("w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False, indent=2)


def process_split(
    split_name: str,
    rows: list[ManifestRow],
    writer: csv.DictWriter,
    acoustic_en: AcousticRuntime,
    acoustic_zh: AcousticRuntime,
    text_runtime: TextRuntime,
    asr_runtime: AsrRuntime | None,
    asr_cache: dict[str, dict[str, str]],
    default_language: str,
    max_samples: int,
) -> dict:
    label2id = {label: idx for idx, label in enumerate(CANONICAL_EMOTIONS)}
    total = 0
    label_count = {label: 0 for label in CANONICAL_EMOTIONS}
    lang_count = {"en": 0, "zh": 0}
    asr_miss = 0
    asr_failed = 0

    for idx, row in enumerate(rows):
        if max_samples > 0 and idx >= max_samples:
            break

        language = row.language or infer_language_from_path(row.path, fallback=default_language)
        language = "zh" if language == "zh" else "en"
        acoustic = acoustic_zh if language == "zh" else acoustic_en
        audio_probs = acoustic.predict_probs(row.path)

        transcript = row.transcript
        transcript_language = row.language
        if not transcript:
            cached = asr_cache.get(row.path)
            if cached is not None:
                transcript = cached.get("text", "")
                transcript_language = normalize_language(cached.get("language"))
                if not transcript:
                    asr_miss += 1
            elif asr_runtime is not None:
                try:
                    transcript, transcript_language = asr_runtime.transcribe(row.path, language_hint=language)
                except Exception as exc:
                    asr_failed += 1
                    transcript = ""
                    transcript_language = language
                    print(f"[warn] asr failed for {row.path}: {exc}")
                asr_cache[row.path] = {
                    "text": transcript,
                    "language": transcript_language or "",
                }
                if not transcript:
                    asr_miss += 1
            else:
                asr_miss += 1

        text_lang = transcript_language or language
        text_lang = "zh" if text_lang == "zh" else "en"
        text_result = text_runtime.score(transcript, text_lang)
        text_scores = text_result.get("scores", {})
        text_negative = float(text_scores.get("negative", 0.0))
        text_neutral = float(text_scores.get("neutral", 0.0))
        text_positive = float(text_scores.get("positive", 0.0))
        text_negative_score = float(text_result.get("negativeScore", text_negative))
        text_length = len(transcript)
        text_length_norm = min(1.0, text_length / 256.0)

        audio_prob_values = [
            float(audio_probs["ANG"]),
            float(audio_probs["HAP"]),
            float(audio_probs["NEU"]),
            float(audio_probs["SAD"]),
        ]
        writer.writerow(
            {
                "path": row.path,
                "label": row.label,
                "label_id": label2id[row.label],
                "language": language,
                "transcript": transcript,
                "audio_prob_ang": audio_prob_values[0],
                "audio_prob_hap": audio_prob_values[1],
                "audio_prob_neu": audio_prob_values[2],
                "audio_prob_sad": audio_prob_values[3],
                "audio_confidence": max(audio_prob_values),
                "audio_entropy": shannon_entropy(audio_prob_values),
                "text_negative": text_negative,
                "text_neutral": text_neutral,
                "text_positive": text_positive,
                "text_negative_score": text_negative_score,
                "text_top_label_raw": str(text_result.get("topLabelRaw", "")),
                "text_top_conf_raw": float(text_result.get("topConfidenceRaw", 0.0)),
                "text_length": text_length,
                "text_length_norm": text_length_norm,
            }
        )
        total += 1
        label_count[row.label] += 1
        lang_count[language] += 1

        if total % 100 == 0:
            print(f"[{split_name}] processed={total}")

    return {
        "split": split_name,
        "count": total,
        "label_count": label_count,
        "language_count": lang_count,
        "asr_missing_transcript": asr_miss,
        "asr_failed": asr_failed,
    }


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    train_rows = read_manifest(args.train_manifest)
    val_rows = read_manifest(args.val_manifest)
    test_rows = read_manifest(args.test_manifest) if args.test_manifest else []

    acoustic_en = AcousticRuntime(args.audio_model_en, sample_rate=args.sample_rate, device=args.audio_device)
    acoustic_zh = AcousticRuntime(args.audio_model_zh, sample_rate=args.sample_rate, device=args.audio_device)
    text_runtime = TextRuntime(args.text_model_en, args.text_model_zh, device=args.text_device)

    asr_runtime = None
    if not args.skip_asr:
        asr_runtime = AsrRuntime(
            model_name=args.whisper_model,
            device=args.whisper_device,
            compute_type=args.whisper_compute_type,
            cpu_threads=args.whisper_cpu_threads,
        )

    asr_cache = load_asr_cache(args.asr_cache_json)
    print(f"[cache] loaded asr entries={len(asr_cache)}")

    train_writer, train_file = build_writer(output_dir / "train_features.csv")
    val_writer, val_file = build_writer(output_dir / "val_features.csv")
    test_writer = None
    test_file = None
    if test_rows:
        test_writer, test_file = build_writer(output_dir / "test_features.csv")

    try:
        summary = {
            "train": process_split(
                "train",
                train_rows,
                train_writer,
                acoustic_en,
                acoustic_zh,
                text_runtime,
                asr_runtime,
                asr_cache,
                default_language=args.default_language,
                max_samples=args.max_samples,
            ),
            "val": process_split(
                "val",
                val_rows,
                val_writer,
                acoustic_en,
                acoustic_zh,
                text_runtime,
                asr_runtime,
                asr_cache,
                default_language=args.default_language,
                max_samples=args.max_samples,
            ),
        }
        if test_rows and test_writer is not None:
            summary["test"] = process_split(
                "test",
                test_rows,
                test_writer,
                acoustic_en,
                acoustic_zh,
                text_runtime,
                asr_runtime,
                asr_cache,
                default_language=args.default_language,
                max_samples=args.max_samples,
            )
    finally:
        train_file.close()
        val_file.close()
        if test_file is not None:
            test_file.close()

    save_asr_cache(args.asr_cache_json, asr_cache)
    summary["config"] = {
        "audio_model_en": str(Path(args.audio_model_en).resolve()),
        "audio_model_zh": str(Path(args.audio_model_zh).resolve()),
        "text_model_en": str(Path(args.text_model_en).resolve()),
        "text_model_zh": str(Path(args.text_model_zh).resolve()),
        "audio_device": args.audio_device,
        "text_device": args.text_device,
        "asr_enabled": not args.skip_asr,
        "asr_cache_entries": len(asr_cache),
    }
    with (output_dir / "summary.json").open("w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(json.dumps({"status": "ok", "output_dir": str(output_dir), "summary": summary}, ensure_ascii=False))


if __name__ == "__main__":
    main()

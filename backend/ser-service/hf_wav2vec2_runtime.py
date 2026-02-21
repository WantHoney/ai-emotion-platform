from pathlib import Path

import numpy as np
import torch
from transformers import AutoFeatureExtractor, Wav2Vec2ForSequenceClassification

from training.audio_utils import load_audio_mono_resample


class HFWav2Vec2Runtime:
    def __init__(self, model_dir: str, sample_rate: int = 16000, device: str = "auto"):
        self.model_dir = str(Path(model_dir).resolve())
        self.sample_rate = sample_rate

        if device == "auto":
            self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        elif device == "cuda":
            if not torch.cuda.is_available():
                raise RuntimeError("SER_HF_DEVICE=cuda but CUDA is unavailable")
            self.device = torch.device("cuda")
        else:
            self.device = torch.device("cpu")

        self.feature_extractor = AutoFeatureExtractor.from_pretrained(self.model_dir)
        self.model = Wav2Vec2ForSequenceClassification.from_pretrained(self.model_dir).to(self.device).eval()
        config = self.model.config

        self.id2label: dict[int, str] = {}
        if isinstance(config.id2label, dict):
            for k, v in config.id2label.items():
                self.id2label[int(k)] = str(v)
        elif isinstance(config.label2id, dict):
            for label, idx in config.label2id.items():
                self.id2label[int(idx)] = str(label)
        else:
            raise RuntimeError("wav2vec2 model config has no label mapping")

    def predict_file(self, wav_path: str | Path) -> tuple[str, float]:
        audio = load_audio_mono_resample(wav_path, target_sr=self.sample_rate, max_duration_sec=None)
        encoded = self.feature_extractor(
            [audio],
            sampling_rate=self.sample_rate,
            return_tensors="pt",
            padding=True,
            return_attention_mask=True,
        )
        inputs = {
            "input_values": encoded["input_values"].to(self.device),
        }
        attention_mask = encoded.get("attention_mask")
        if attention_mask is not None:
            inputs["attention_mask"] = attention_mask.to(self.device)
        with torch.no_grad():
            outputs = self.model(**inputs)
            probs = torch.softmax(outputs.logits, dim=-1).detach().cpu().numpy()[0]
        pred_id = int(np.argmax(probs))
        label = self.id2label.get(pred_id, str(pred_id))
        confidence = float(probs[pred_id])
        return label, confidence

from dataclasses import dataclass
from pathlib import Path

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer


NEGATIVE_HINTS = ("neg", "negative", "sad", "ang", "anger", "fear", "disgust")
NEUTRAL_HINTS = ("neu", "neutral")
POSITIVE_HINTS = ("pos", "positive", "happy", "joy", "love")


def _categorize_label(label: str) -> str | None:
    key = (label or "").strip().lower()
    if not key:
        return None
    if any(h in key for h in NEGATIVE_HINTS):
        return "negative"
    if any(h in key for h in NEUTRAL_HINTS):
        return "neutral"
    if any(h in key for h in POSITIVE_HINTS):
        return "positive"
    return None


@dataclass
class TextSentimentRuntime:
    model_ref: str
    device_name: str = "auto"
    max_length: int = 256

    def __post_init__(self) -> None:
        resolved = Path(self.model_ref)
        self.model_ref = str(resolved.resolve()) if resolved.exists() else self.model_ref

        if self.device_name == "auto":
            self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        elif self.device_name == "cuda":
            if not torch.cuda.is_available():
                raise RuntimeError("TEXT_HF_DEVICE=cuda but CUDA is unavailable")
            self.device = torch.device("cuda")
        else:
            self.device = torch.device("cpu")

        self.tokenizer = AutoTokenizer.from_pretrained(self.model_ref)
        self.model = AutoModelForSequenceClassification.from_pretrained(self.model_ref).to(self.device).eval()
        self.id2label = {}
        config = self.model.config
        if isinstance(config.id2label, dict) and config.id2label:
            self.id2label = {int(k): str(v) for k, v in config.id2label.items()}
        elif isinstance(config.label2id, dict) and config.label2id:
            self.id2label = {int(v): str(k) for k, v in config.label2id.items()}
        else:
            self.id2label = {idx: f"LABEL_{idx}" for idx in range(int(config.num_labels))}

    def _fallback_category_scores(self, probs: torch.Tensor) -> dict[str, float]:
        n = int(probs.shape[0])
        if n == 2:
            return {
                "negative": float(probs[0].item()),
                "neutral": 0.0,
                "positive": float(probs[1].item()),
            }
        if n == 3:
            return {
                "negative": float(probs[0].item()),
                "neutral": float(probs[1].item()),
                "positive": float(probs[2].item()),
            }
        return {"negative": 0.0, "neutral": 1.0, "positive": 0.0}

    def predict_text(self, text: str) -> dict:
        payload = (text or "").strip()
        if not payload:
            return {
                "label": "neutral",
                "negativeScore": 0.0,
                "scores": {"negative": 0.0, "neutral": 1.0, "positive": 0.0},
                "topLabelRaw": "EMPTY",
                "topConfidenceRaw": 1.0,
                "rawScores": {"EMPTY": 1.0},
            }

        encoded = self.tokenizer(
            payload,
            return_tensors="pt",
            truncation=True,
            max_length=self.max_length,
        )
        encoded = {k: v.to(self.device) for k, v in encoded.items()}
        with torch.no_grad():
            logits = self.model(**encoded).logits[0]
            probs = torch.softmax(logits, dim=-1).detach().cpu()

        raw_scores: dict[str, float] = {}
        category_scores = {"negative": 0.0, "neutral": 0.0, "positive": 0.0}

        for idx in range(int(probs.shape[0])):
            label = self.id2label.get(idx, f"LABEL_{idx}")
            score = float(probs[idx].item())
            raw_scores[label] = score
            category = _categorize_label(label)
            if category is not None:
                category_scores[category] += score

        if sum(category_scores.values()) <= 0.0:
            category_scores = self._fallback_category_scores(probs)
        else:
            unknown_mass = max(0.0, 1.0 - sum(category_scores.values()))
            if unknown_mass > 0:
                category_scores["neutral"] += unknown_mass

        best_category = max(category_scores, key=category_scores.get)
        best_raw_idx = int(torch.argmax(probs).item())
        best_raw_label = self.id2label.get(best_raw_idx, f"LABEL_{best_raw_idx}")
        best_raw_conf = float(probs[best_raw_idx].item())

        return {
            "label": best_category,
            "negativeScore": max(0.0, min(1.0, float(category_scores["negative"]))),
            "scores": {
                "negative": float(category_scores["negative"]),
                "neutral": float(category_scores["neutral"]),
                "positive": float(category_scores["positive"]),
            },
            "topLabelRaw": best_raw_label,
            "topConfidenceRaw": best_raw_conf,
            "rawScores": raw_scores,
        }

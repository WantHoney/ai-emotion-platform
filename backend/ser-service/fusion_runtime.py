import json
from pathlib import Path

import numpy as np
import torch
from torch import nn


DEFAULT_LABELS = ("ANG", "HAP", "NEU", "SAD")
DEFAULT_FEATURE_COLUMNS = (
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
    "text_length_norm",
    "lang_is_zh",
)


class FusionMLP(nn.Module):
    def __init__(self, in_dim: int, out_dim: int, hidden_size: int, dropout: float) -> None:
        super().__init__()
        if hidden_size <= 0:
            self.net = nn.Linear(in_dim, out_dim)
        else:
            self.net = nn.Sequential(
                nn.Linear(in_dim, hidden_size),
                nn.ReLU(),
                nn.Dropout(dropout),
                nn.Linear(hidden_size, out_dim),
            )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x)


class FusionRuntime:
    def __init__(self, model_dir: str, device_name: str = "auto") -> None:
        self.model_dir = Path(model_dir).resolve()
        model_path = self.model_dir / "fusion_model.pt"
        scaler_path = self.model_dir / "feature_scaler.json"
        if not model_path.exists():
            raise FileNotFoundError(f"fusion model not found: {model_path}")
        if not scaler_path.exists():
            raise FileNotFoundError(f"fusion scaler not found: {scaler_path}")

        if device_name == "auto":
            self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        elif device_name == "cuda":
            if not torch.cuda.is_available():
                raise RuntimeError("FUSION_DEVICE=cuda but CUDA is unavailable")
            self.device = torch.device("cuda")
        else:
            self.device = torch.device("cpu")

        checkpoint = torch.load(model_path, map_location=self.device)
        with scaler_path.open("r", encoding="utf-8") as f:
            scaler = json.load(f)

        feature_columns = checkpoint.get("feature_columns") or scaler.get("feature_columns") or list(DEFAULT_FEATURE_COLUMNS)
        self.feature_columns = [str(col) for col in feature_columns]
        self.labels = [str(label) for label in checkpoint.get("labels", list(DEFAULT_LABELS))]

        self.temperature = float(checkpoint.get("temperature", 1.0))
        self.temperature = max(0.05, min(20.0, self.temperature))

        self.calibration_mode = str(checkpoint.get("calibration_mode", "global_temperature"))
        self.calibration_params = checkpoint.get("calibration_params") or {
            "mode": "global_temperature",
            "temperature": self.temperature,
            "temperature_global": self.temperature,
        }
        if "mode" not in self.calibration_params:
            self.calibration_params["mode"] = self.calibration_mode

        self.vector_scale = None
        self.vector_bias = None
        if self.calibration_mode == "vector_scaling":
            scale = np.asarray(self.calibration_params.get("scale", []), dtype=np.float32)
            bias = np.asarray(self.calibration_params.get("bias", []), dtype=np.float32)
            if scale.shape[0] != len(self.labels) or bias.shape[0] != len(self.labels):
                raise RuntimeError(
                    "vector scaling params shape mismatch: "
                    f"scale={scale.shape[0]}, bias={bias.shape[0]}, labels={len(self.labels)}"
                )
            self.vector_scale = torch.from_numpy(scale).to(self.device)
            self.vector_bias = torch.from_numpy(bias).to(self.device)

        mean = np.asarray(scaler.get("mean", []), dtype=np.float32)
        std = np.asarray(scaler.get("std", []), dtype=np.float32)
        if mean.shape[0] != len(self.feature_columns) or std.shape[0] != len(self.feature_columns):
            raise RuntimeError(
                "fusion scaler shape mismatch: "
                f"mean={mean.shape[0]}, std={std.shape[0]}, features={len(self.feature_columns)}"
            )
        std = np.where(std < 1e-6, 1.0, std)
        self.mean = mean
        self.std = std

        input_dim = int(checkpoint.get("input_dim", len(self.feature_columns)))
        hidden_size = int(checkpoint.get("hidden_size", 64))
        dropout = float(checkpoint.get("dropout", 0.2))
        self.model = FusionMLP(
            in_dim=input_dim,
            out_dim=len(self.labels),
            hidden_size=hidden_size,
            dropout=dropout,
        ).to(self.device)
        self.model.load_state_dict(checkpoint["model_state_dict"])
        self.model.eval()

    def _vectorize(self, features: dict[str, float]) -> tuple[np.ndarray, dict[str, float]]:
        used: dict[str, float] = {}
        values: list[float] = []
        for name in self.feature_columns:
            value = float(features.get(name, 0.0))
            used[name] = value
            values.append(value)
        vec = np.asarray(values, dtype=np.float32)
        vec = (vec - self.mean) / self.std
        return vec, used

    def _effective_temperature(self, lang_is_zh: bool) -> float:
        if self.calibration_mode == "per_language_temperature":
            if lang_is_zh:
                temp = float(self.calibration_params.get("temperature_zh", self.calibration_params.get("temperature_global", self.temperature)))
            else:
                temp = float(self.calibration_params.get("temperature_en", self.calibration_params.get("temperature_global", self.temperature)))
            return max(0.05, min(20.0, temp))

        if self.calibration_mode == "global_temperature":
            temp = float(self.calibration_params.get("temperature", self.calibration_params.get("temperature_global", self.temperature)))
            return max(0.05, min(20.0, temp))

        return self.temperature

    def _calibrate_logits(self, logits: torch.Tensor, lang_is_zh: bool) -> tuple[torch.Tensor, float | None]:
        if self.calibration_mode == "vector_scaling":
            if self.vector_scale is None or self.vector_bias is None:
                raise RuntimeError("vector scaling params not initialized")
            calibrated = logits * self.vector_scale + self.vector_bias
            return calibrated, None

        effective_temp = self._effective_temperature(lang_is_zh)
        return logits / effective_temp, effective_temp

    def predict(self, features: dict[str, float]) -> dict:
        vec, used_features = self._vectorize(features)
        lang_is_zh = float(used_features.get("lang_is_zh", 0.0)) >= 0.5

        tensor = torch.from_numpy(vec).unsqueeze(0).to(self.device)
        with torch.no_grad():
            logits = self.model(tensor)[0]
            logits_cal, effective_temp = self._calibrate_logits(logits, lang_is_zh)
            probs_uncal = torch.softmax(logits, dim=-1).detach().cpu().numpy()
            probs_cal = torch.softmax(logits_cal, dim=-1).detach().cpu().numpy()

        pred_id = int(np.argmax(probs_cal))
        top_label = self.labels[pred_id]
        top_conf = float(probs_cal[pred_id])
        return {
            "label": top_label,
            "confidence": top_conf,
            "temperature": float(effective_temp) if effective_temp is not None else self.temperature,
            "calibrationMode": self.calibration_mode,
            "scores": {label: float(probs_cal[idx]) for idx, label in enumerate(self.labels)},
            "scoresUncalibrated": {label: float(probs_uncal[idx]) for idx, label in enumerate(self.labels)},
            "features": used_features,
            "featureColumns": list(self.feature_columns),
        }

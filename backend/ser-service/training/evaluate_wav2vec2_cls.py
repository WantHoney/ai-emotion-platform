import argparse
import csv
import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch
from torch.utils.data import DataLoader, Dataset
from transformers import AutoFeatureExtractor, Wav2Vec2ForSequenceClassification

from audio_utils import load_audio_mono_resample
from train_wav2vec2_cls import balanced_accuracy, build_confusion_matrix, macro_f1_score


@dataclass(frozen=True)
class Sample:
    path: str
    label: str


@dataclass
class EvalBatchCollator:
    feature_extractor: AutoFeatureExtractor
    sample_rate: int
    include_paths: bool = True

    def __call__(self, batch: list[dict[str, object]]) -> dict[str, object]:
        waveforms = [item["input_values"] for item in batch]
        encoded = self.feature_extractor(
            waveforms,
            sampling_rate=self.sample_rate,
            return_tensors="pt",
            padding=True,
            return_attention_mask=True,
        )
        encoded["labels"] = torch.tensor([int(item["labels"]) for item in batch], dtype=torch.long)
        if self.include_paths:
            encoded["paths"] = [str(item["path"]) for item in batch]
        return encoded


class EvalDataset(Dataset):
    def __init__(self, samples: list[Sample], label2id: dict[str, int], sample_rate: int, max_duration_sec: float) -> None:
        self.samples = samples
        self.label2id = label2id
        self.sample_rate = sample_rate
        self.max_duration_sec = max_duration_sec

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int) -> dict[str, object]:
        sample = self.samples[index]
        audio = load_audio_mono_resample(sample.path, target_sr=self.sample_rate, max_duration_sec=self.max_duration_sec)
        return {"input_values": audio, "labels": self.label2id[sample.label], "path": sample.path}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate a fine-tuned Wav2Vec2 emotion model.")
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--sample-rate", type=int, default=16000)
    parser.add_argument("--max-duration-sec", type=float, default=15.0)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--num-workers", type=int, default=0)
    parser.add_argument("--device", default="auto", choices=["auto", "cpu", "cuda"])
    parser.add_argument("--output-json", default="")
    parser.add_argument("--output-predictions-csv", default="")
    parser.add_argument("--output-confusion-csv", default="")
    return parser.parse_args()


def read_manifest(path: str | Path) -> list[Sample]:
    rows: list[Sample] = []
    with Path(path).open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            p = (row.get("path") or "").strip()
            label = (row.get("label") or "").strip().upper()
            if p and label:
                rows.append(Sample(path=p, label=label))
    if not rows:
        raise ValueError(f"empty manifest: {path}")
    return rows


def resolve_device(name: str) -> torch.device:
    if name == "cpu":
        return torch.device("cpu")
    if name == "cuda":
        if not torch.cuda.is_available():
            raise RuntimeError("CUDA requested but unavailable")
        return torch.device("cuda")
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")


def main() -> None:
    args = parse_args()
    model_dir = Path(args.model_dir).resolve()
    samples = read_manifest(args.manifest)

    model = Wav2Vec2ForSequenceClassification.from_pretrained(model_dir)
    feature_extractor = AutoFeatureExtractor.from_pretrained(model_dir)
    config = model.config
    id2label = {int(k): v for k, v in config.id2label.items()} if isinstance(config.id2label, dict) else {}
    label2id = {v.upper(): int(k) for k, v in id2label.items()}
    if not label2id:
        raw = config.label2id or {}
        label2id = {str(k).upper(): int(v) for k, v in raw.items()}
        id2label = {int(v): str(k) for k, v in raw.items()}
    if not label2id:
        raise RuntimeError("model has no label mapping (id2label/label2id)")

    unknown = sorted({s.label for s in samples if s.label not in label2id})
    if unknown:
        raise ValueError(f"manifest has labels not in model: {unknown}")

    dataset = EvalDataset(samples, label2id, args.sample_rate, args.max_duration_sec)
    collate_fn = EvalBatchCollator(feature_extractor=feature_extractor, sample_rate=args.sample_rate, include_paths=True)

    loader = DataLoader(
        dataset,
        batch_size=args.batch_size,
        shuffle=False,
        collate_fn=collate_fn,
        num_workers=args.num_workers,
    )

    device = resolve_device(args.device)
    model = model.to(device).eval()

    all_true: list[np.ndarray] = []
    all_pred: list[np.ndarray] = []
    all_paths: list[str] = []
    all_prob: list[np.ndarray] = []

    with torch.no_grad():
        for batch in loader:
            labels = batch["labels"].to(device)
            inputs = {"input_values": batch["input_values"].to(device)}
            attention_mask = batch.get("attention_mask")
            if attention_mask is not None:
                inputs["attention_mask"] = attention_mask.to(device)
            outputs = model(**inputs)
            logits = outputs.logits
            probs = torch.softmax(logits, dim=-1).detach().cpu().numpy()
            preds = np.argmax(probs, axis=-1)

            all_true.append(labels.detach().cpu().numpy())
            all_pred.append(preds)
            all_prob.append(probs)
            all_paths.extend(batch["paths"])

    y_true = np.concatenate(all_true, axis=0)
    y_pred = np.concatenate(all_pred, axis=0)
    y_prob = np.concatenate(all_prob, axis=0)

    num_labels = len(label2id)
    metrics = {
        "accuracy": float(np.mean(y_true == y_pred)),
        "macro_f1": macro_f1_score(y_true, y_pred, num_labels),
        "balanced_accuracy": balanced_accuracy(y_true, y_pred, num_labels),
        "num_samples": int(y_true.shape[0]),
    }

    print(json.dumps(metrics, ensure_ascii=False, indent=2))

    if args.output_json:
        out = Path(args.output_json)
        out.parent.mkdir(parents=True, exist_ok=True)
        with out.open("w", encoding="utf-8") as f:
            json.dump(metrics, f, ensure_ascii=False, indent=2)

    if args.output_confusion_csv:
        labels_sorted = [id2label[i] if i in id2label else str(i) for i in range(num_labels)]
        matrix = build_confusion_matrix(y_true, y_pred, num_labels)
        out = Path(args.output_confusion_csv)
        out.parent.mkdir(parents=True, exist_ok=True)
        with out.open("w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["truth\\pred", *labels_sorted])
            for i, label in enumerate(labels_sorted):
                writer.writerow([label, *matrix[i].tolist()])

    if args.output_predictions_csv:
        labels_sorted = [id2label[i] if i in id2label else str(i) for i in range(num_labels)]
        out = Path(args.output_predictions_csv)
        out.parent.mkdir(parents=True, exist_ok=True)
        with out.open("w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["path", "truth", "pred", "confidence", *[f"p_{name}" for name in labels_sorted]])
            for idx in range(len(all_paths)):
                prob = y_prob[idx]
                pred_id = int(y_pred[idx])
                truth_id = int(y_true[idx])
                writer.writerow(
                    [
                        all_paths[idx],
                        id2label.get(truth_id, str(truth_id)),
                        id2label.get(pred_id, str(pred_id)),
                        float(prob[pred_id]),
                        *[float(x) for x in prob.tolist()],
                    ]
                )


if __name__ == "__main__":
    main()

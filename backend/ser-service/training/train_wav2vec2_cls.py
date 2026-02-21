import argparse
import csv
import json
import math
import random
import time
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch
from torch.optim import AdamW
from torch.utils.data import DataLoader, Dataset
from transformers import (
    AutoFeatureExtractor,
    Wav2Vec2ForSequenceClassification,
    get_linear_schedule_with_warmup,
)

from audio_utils import load_audio_mono_resample


@dataclass(frozen=True)
class Sample:
    path: str
    label: str


@dataclass
class Wav2Vec2BatchCollator:
    feature_extractor: AutoFeatureExtractor
    sample_rate: int

    def __call__(self, batch: list[dict[str, object]]) -> dict[str, torch.Tensor]:
        waveforms = [item["input_values"] for item in batch]
        encoded = self.feature_extractor(
            waveforms,
            sampling_rate=self.sample_rate,
            return_tensors="pt",
            padding=True,
            return_attention_mask=True,
        )
        encoded["labels"] = torch.tensor([int(item["labels"]) for item in batch], dtype=torch.long)
        return encoded


class AudioEmotionDataset(Dataset):
    def __init__(
        self,
        samples: list[Sample],
        label2id: dict[str, int],
        target_sr: int,
        max_duration_sec: float,
    ) -> None:
        self.samples = samples
        self.label2id = label2id
        self.target_sr = target_sr
        self.max_duration_sec = max_duration_sec

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int) -> dict[str, object]:
        sample = self.samples[index]
        audio = load_audio_mono_resample(
            sample.path,
            target_sr=self.target_sr,
            max_duration_sec=self.max_duration_sec,
        )
        label_id = self.label2id[sample.label]
        return {"input_values": audio, "labels": label_id, "path": sample.path}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train Wav2Vec2 + classification head for speech emotion.")
    parser.add_argument("--train-manifest", required=True)
    parser.add_argument("--val-manifest", required=True)
    parser.add_argument("--test-manifest", default="")
    parser.add_argument("--base-model", default="facebook/wav2vec2-base")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--sample-rate", type=int, default=16000)
    parser.add_argument("--max-duration-sec", type=float, default=15.0)
    parser.add_argument("--epochs", type=int, default=12)
    parser.add_argument("--batch-size", type=int, default=4)
    parser.add_argument("--learning-rate", type=float, default=1e-5)
    parser.add_argument("--weight-decay", type=float, default=0.01)
    parser.add_argument("--warmup-ratio", type=float, default=0.1)
    parser.add_argument("--gradient-accumulation", type=int, default=1)
    parser.add_argument("--patience", type=int, default=3)
    parser.add_argument("--num-workers", type=int, default=0)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--freeze-feature-encoder", action="store_true")
    parser.add_argument("--fp16", action="store_true")
    parser.add_argument("--device", default="auto", choices=["auto", "cpu", "cuda"])
    return parser.parse_args()


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def read_manifest(path: str | Path) -> list[Sample]:
    rows: list[Sample] = []
    with Path(path).open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            file_path = (row.get("path") or "").strip()
            label = (row.get("label") or "").strip().upper()
            if not file_path or not label:
                continue
            rows.append(Sample(path=file_path, label=label))
    if not rows:
        raise ValueError(f"manifest is empty: {path}")
    return rows


def build_label_set(train_samples: list[Sample]) -> list[str]:
    labels = sorted({s.label for s in train_samples})
    if len(labels) < 2:
        raise ValueError("need at least 2 labels for classification")
    return labels


def macro_f1_score(y_true: np.ndarray, y_pred: np.ndarray, num_labels: int) -> float:
    eps = 1e-12
    scores = []
    for label in range(num_labels):
        tp = np.sum((y_true == label) & (y_pred == label))
        fp = np.sum((y_true != label) & (y_pred == label))
        fn = np.sum((y_true == label) & (y_pred != label))
        precision = tp / (tp + fp + eps)
        recall = tp / (tp + fn + eps)
        f1 = 2 * precision * recall / (precision + recall + eps)
        scores.append(float(f1))
    return float(np.mean(scores))


def balanced_accuracy(y_true: np.ndarray, y_pred: np.ndarray, num_labels: int) -> float:
    eps = 1e-12
    recalls = []
    for label in range(num_labels):
        tp = np.sum((y_true == label) & (y_pred == label))
        fn = np.sum((y_true == label) & (y_pred != label))
        recalls.append(tp / (tp + fn + eps))
    return float(np.mean(recalls))


def build_confusion_matrix(y_true: np.ndarray, y_pred: np.ndarray, num_labels: int) -> np.ndarray:
    matrix = np.zeros((num_labels, num_labels), dtype=np.int64)
    for truth, pred in zip(y_true, y_pred):
        matrix[int(truth), int(pred)] += 1
    return matrix


def evaluate_model(
    model: Wav2Vec2ForSequenceClassification,
    loader: DataLoader,
    device: torch.device,
    num_labels: int,
) -> tuple[dict[str, float], np.ndarray, np.ndarray]:
    model.eval()
    all_labels: list[np.ndarray] = []
    all_preds: list[np.ndarray] = []
    total_loss = 0.0
    total = 0
    with torch.no_grad():
        for batch in loader:
            labels = batch["labels"].to(device)
            inputs = {
                "input_values": batch["input_values"].to(device),
                "labels": labels,
            }
            attention_mask = batch.get("attention_mask")
            if attention_mask is not None:
                inputs["attention_mask"] = attention_mask.to(device)
            outputs = model(**inputs)
            loss = float(outputs.loss.detach().cpu().item())
            logits = outputs.logits.detach().cpu().numpy()
            preds = np.argmax(logits, axis=-1)

            total_loss += loss * labels.size(0)
            total += labels.size(0)
            all_labels.append(labels.detach().cpu().numpy())
            all_preds.append(preds)

    y_true = np.concatenate(all_labels, axis=0)
    y_pred = np.concatenate(all_preds, axis=0)
    accuracy = float(np.mean(y_true == y_pred))
    macro_f1 = macro_f1_score(y_true, y_pred, num_labels=num_labels)
    bal_acc = balanced_accuracy(y_true, y_pred, num_labels=num_labels)
    avg_loss = total_loss / max(total, 1)
    return (
        {
            "loss": avg_loss,
            "accuracy": accuracy,
            "macro_f1": macro_f1,
            "balanced_accuracy": bal_acc,
        },
        y_true,
        y_pred,
    )


def save_confusion_matrix(
    csv_path: Path,
    labels: list[str],
    y_true: np.ndarray,
    y_pred: np.ndarray,
) -> None:
    matrix = build_confusion_matrix(y_true, y_pred, num_labels=len(labels))
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    with csv_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["truth\\pred", *labels])
        for idx, label in enumerate(labels):
            writer.writerow([label, *matrix[idx].tolist()])


def to_device(device_name: str) -> torch.device:
    if device_name == "cuda":
        if not torch.cuda.is_available():
            raise RuntimeError("CUDA requested but not available")
        return torch.device("cuda")
    if device_name == "cpu":
        return torch.device("cpu")
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")


def main() -> None:
    args = parse_args()
    set_seed(args.seed)

    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    best_dir = output_dir / "best_model"

    train_samples = read_manifest(args.train_manifest)
    val_samples = read_manifest(args.val_manifest)
    test_samples = read_manifest(args.test_manifest) if args.test_manifest else []

    labels = build_label_set(train_samples)
    label2id = {label: idx for idx, label in enumerate(labels)}
    id2label = {idx: label for label, idx in label2id.items()}

    unknown_val_labels = sorted({s.label for s in val_samples if s.label not in label2id})
    unknown_test_labels = sorted({s.label for s in test_samples if s.label not in label2id})
    if unknown_val_labels:
        raise ValueError(f"val manifest contains unknown labels: {unknown_val_labels}")
    if unknown_test_labels:
        raise ValueError(f"test manifest contains unknown labels: {unknown_test_labels}")

    feature_extractor = AutoFeatureExtractor.from_pretrained(args.base_model)
    model = Wav2Vec2ForSequenceClassification.from_pretrained(
        args.base_model,
        num_labels=len(labels),
        label2id=label2id,
        id2label=id2label,
        problem_type="single_label_classification",
        ignore_mismatched_sizes=True,
    )
    if args.freeze_feature_encoder:
        model.freeze_feature_encoder()

    collate_fn = Wav2Vec2BatchCollator(feature_extractor=feature_extractor, sample_rate=args.sample_rate)

    train_ds = AudioEmotionDataset(train_samples, label2id, args.sample_rate, args.max_duration_sec)
    val_ds = AudioEmotionDataset(val_samples, label2id, args.sample_rate, args.max_duration_sec)
    test_ds = AudioEmotionDataset(test_samples, label2id, args.sample_rate, args.max_duration_sec) if test_samples else None

    train_loader = DataLoader(
        train_ds,
        batch_size=args.batch_size,
        shuffle=True,
        collate_fn=collate_fn,
        num_workers=args.num_workers,
        pin_memory=torch.cuda.is_available(),
    )
    val_loader = DataLoader(
        val_ds,
        batch_size=args.batch_size,
        shuffle=False,
        collate_fn=collate_fn,
        num_workers=args.num_workers,
        pin_memory=torch.cuda.is_available(),
    )
    test_loader = (
        DataLoader(
            test_ds,
            batch_size=args.batch_size,
            shuffle=False,
            collate_fn=collate_fn,
            num_workers=args.num_workers,
            pin_memory=torch.cuda.is_available(),
        )
        if test_ds is not None
        else None
    )

    device = to_device(args.device)
    model.to(device)
    optimizer = AdamW(model.parameters(), lr=args.learning_rate, weight_decay=args.weight_decay)

    update_steps_per_epoch = math.ceil(len(train_loader) / max(1, args.gradient_accumulation))
    total_steps = max(1, args.epochs * update_steps_per_epoch)
    warmup_steps = int(total_steps * max(0.0, min(1.0, args.warmup_ratio)))
    scheduler = get_linear_schedule_with_warmup(
        optimizer=optimizer,
        num_warmup_steps=warmup_steps,
        num_training_steps=total_steps,
    )

    use_amp = args.fp16 and device.type == "cuda"
    scaler = torch.amp.GradScaler(device="cuda", enabled=use_amp)

    best_f1 = -1.0
    best_epoch = -1
    bad_epochs = 0
    history: list[dict[str, float | int]] = []

    global_step = 0
    train_start = time.time()

    for epoch in range(1, args.epochs + 1):
        model.train()
        optimizer.zero_grad(set_to_none=True)
        running_loss = 0.0
        batch_count = 0

        for step, batch in enumerate(train_loader, start=1):
            inputs = {
                "input_values": batch["input_values"].to(device),
                "labels": batch["labels"].to(device),
            }
            attention_mask = batch.get("attention_mask")
            if attention_mask is not None:
                inputs["attention_mask"] = attention_mask.to(device)
            with torch.amp.autocast(device_type="cuda", enabled=use_amp):
                outputs = model(**inputs)
                loss = outputs.loss / max(1, args.gradient_accumulation)

            scaler.scale(loss).backward()
            running_loss += float(loss.detach().cpu().item()) * max(1, args.gradient_accumulation)
            batch_count += 1

            if step % args.gradient_accumulation == 0 or step == len(train_loader):
                scaler.step(optimizer)
                scaler.update()
                scheduler.step()
                optimizer.zero_grad(set_to_none=True)
                global_step += 1

        train_loss = running_loss / max(batch_count, 1)
        val_metrics, val_true, val_pred = evaluate_model(model, val_loader, device, num_labels=len(labels))
        epoch_report = {
            "epoch": epoch,
            "train_loss": train_loss,
            "val_loss": val_metrics["loss"],
            "val_accuracy": val_metrics["accuracy"],
            "val_macro_f1": val_metrics["macro_f1"],
            "val_balanced_accuracy": val_metrics["balanced_accuracy"],
            "learning_rate": float(scheduler.get_last_lr()[0]),
            "global_step": global_step,
        }
        history.append(epoch_report)
        print(json.dumps(epoch_report, ensure_ascii=False))

        improved = val_metrics["macro_f1"] > best_f1
        if improved:
            best_f1 = val_metrics["macro_f1"]
            best_epoch = epoch
            bad_epochs = 0
            best_dir.mkdir(parents=True, exist_ok=True)
            model.save_pretrained(best_dir)
            feature_extractor.save_pretrained(best_dir)
            save_confusion_matrix(output_dir / "val_confusion_matrix_best.csv", labels, val_true, val_pred)
        else:
            bad_epochs += 1

        if bad_epochs >= args.patience:
            print(f"early stopping at epoch={epoch}, patience={args.patience}")
            break

    total_seconds = time.time() - train_start

    if not best_dir.exists():
        raise RuntimeError("training finished without saving best model")

    best_model = Wav2Vec2ForSequenceClassification.from_pretrained(best_dir).to(device)
    best_feature_extractor = AutoFeatureExtractor.from_pretrained(best_dir)
    best_collate_fn = Wav2Vec2BatchCollator(feature_extractor=best_feature_extractor, sample_rate=args.sample_rate)

    val_loader_best = DataLoader(
        val_ds,
        batch_size=args.batch_size,
        shuffle=False,
        collate_fn=best_collate_fn,
        num_workers=args.num_workers,
        pin_memory=torch.cuda.is_available(),
    )
    best_val_metrics, best_val_true, best_val_pred = evaluate_model(
        best_model,
        val_loader_best,
        device=device,
        num_labels=len(labels),
    )
    save_confusion_matrix(output_dir / "val_confusion_matrix_final.csv", labels, best_val_true, best_val_pred)

    test_metrics = None
    if test_loader is not None:
        test_loader_best = DataLoader(
            test_ds,
            batch_size=args.batch_size,
            shuffle=False,
            collate_fn=best_collate_fn,
            num_workers=args.num_workers,
            pin_memory=torch.cuda.is_available(),
        )
        test_metrics, test_true, test_pred = evaluate_model(
            best_model,
            test_loader_best,
            device=device,
            num_labels=len(labels),
        )
        save_confusion_matrix(output_dir / "test_confusion_matrix.csv", labels, test_true, test_pred)

    report = {
        "base_model": args.base_model,
        "labels": labels,
        "label2id": label2id,
        "train_samples": len(train_samples),
        "val_samples": len(val_samples),
        "test_samples": len(test_samples),
        "epochs_requested": args.epochs,
        "epochs_ran": len(history),
        "best_epoch": best_epoch,
        "best_val_macro_f1": best_f1,
        "best_val_metrics": best_val_metrics,
        "test_metrics": test_metrics,
        "history": history,
        "runtime_seconds": total_seconds,
        "device": str(device),
        "global_steps": global_step,
    }
    with (output_dir / "train_report.json").open("w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    with (output_dir / "label_map.json").open("w", encoding="utf-8") as f:
        json.dump({"label2id": label2id, "id2label": id2label}, f, ensure_ascii=False, indent=2)

    print(json.dumps({"status": "ok", "output_dir": str(output_dir), "best_epoch": best_epoch}, ensure_ascii=False))


if __name__ == "__main__":
    main()

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
from torch import nn
from torch.optim import AdamW
from torch.utils.data import DataLoader, Dataset
from transformers import AutoModelForSequenceClassification, AutoTokenizer, get_linear_schedule_with_warmup


EMOTION_LABELS = ("ANG", "HAP", "NEU", "SAD")


@dataclass(frozen=True)
class TextRow:
    text: str
    label_id: int
    language: str


class TextDataset(Dataset):
    def __init__(self, rows: list[TextRow], tokenizer: AutoTokenizer, max_length: int) -> None:
        self.rows = rows
        self.tokenizer = tokenizer
        self.max_length = max_length

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, idx: int) -> dict:
        row = self.rows[idx]
        encoded = self.tokenizer(
            row.text,
            truncation=True,
            max_length=self.max_length,
            return_attention_mask=True,
        )
        encoded["labels"] = row.label_id
        return encoded


def collate_fn(batch: list[dict], tokenizer: AutoTokenizer) -> dict[str, torch.Tensor]:
    labels = torch.tensor([int(item["labels"]) for item in batch], dtype=torch.long)
    items = []
    for item in batch:
        items.append({k: v for k, v in item.items() if k != "labels"})
    padded = tokenizer.pad(items, return_tensors="pt")
    padded["labels"] = labels
    return padded


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train 4-class text emotion model from fusion feature transcripts.")
    parser.add_argument("--train-features", required=True)
    parser.add_argument("--val-features", required=True)
    parser.add_argument("--test-features", default="")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--base-model", default="./text_models/zh_roberta_sentiment")
    parser.add_argument("--language-filter", default="zh", choices=["zh", "en", "all"])
    parser.add_argument("--max-length", type=int, default=128)
    parser.add_argument("--min-text-chars", type=int, default=2)
    parser.add_argument("--epochs", type=int, default=5)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--learning-rate", type=float, default=2e-5)
    parser.add_argument("--weight-decay", type=float, default=1e-2)
    parser.add_argument("--warmup-ratio", type=float, default=0.1)
    parser.add_argument("--patience", type=int, default=2)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", default="auto", choices=["auto", "cpu", "cuda"])
    parser.add_argument("--num-workers", type=int, default=0)
    parser.add_argument("--disable-class-weighted-loss", action="store_true")
    return parser.parse_args()


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def resolve_device(name: str) -> torch.device:
    if name == "cpu":
        return torch.device("cpu")
    if name == "cuda":
        if not torch.cuda.is_available():
            raise RuntimeError("device=cuda but CUDA unavailable")
        return torch.device("cuda")
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")


def normalize_emotion(label: str) -> str:
    key = (label or "").strip().upper()
    if key in {"ANG", "ANGRY"}:
        return "ANG"
    if key in {"HAP", "HAPPY", "EXC"}:
        return "HAP"
    if key in {"NEU", "NEUTRAL", "CALM"}:
        return "NEU"
    if key in {"SAD", "SADNESS"}:
        return "SAD"
    raise ValueError(f"unsupported emotion label: {label}")


def parse_language(row: dict[str, str]) -> str:
    lang = (row.get("language") or "").strip().lower()
    if lang.startswith("zh"):
        return "zh"
    if lang.startswith("en"):
        return "en"
    lang_flag = (row.get("lang_is_zh") or "").strip()
    if lang_flag:
        try:
            return "zh" if float(lang_flag) >= 0.5 else "en"
        except Exception:
            return "en"
    return "en"


def read_rows(path: str | Path, language_filter: str, min_text_chars: int) -> tuple[list[TextRow], dict[str, int]]:
    label2id = {label: idx for idx, label in enumerate(EMOTION_LABELS)}
    rows: list[TextRow] = []
    stats = {
        "total": 0,
        "kept": 0,
        "dropped_empty_text": 0,
        "dropped_language": 0,
        "dropped_bad_label": 0,
        "kept_zh": 0,
        "kept_en": 0,
    }

    with Path(path).open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            stats["total"] += 1
            text = (row.get("transcript") or row.get("text") or "").strip()
            if len(text) < min_text_chars:
                stats["dropped_empty_text"] += 1
                continue

            lang = parse_language(row)
            if language_filter != "all" and lang != language_filter:
                stats["dropped_language"] += 1
                continue

            label = (row.get("label") or "").strip()
            label_id = (row.get("label_id") or "").strip()
            try:
                if label:
                    emotion = normalize_emotion(label)
                elif label_id:
                    idx = int(float(label_id))
                    emotion = EMOTION_LABELS[idx]
                else:
                    raise ValueError("missing label")
            except Exception:
                stats["dropped_bad_label"] += 1
                continue

            rows.append(TextRow(text=text, label_id=label2id[emotion], language=lang))
            stats["kept"] += 1
            if lang == "zh":
                stats["kept_zh"] += 1
            else:
                stats["kept_en"] += 1

    if not rows:
        raise ValueError(f"no valid rows loaded from {path}")
    return rows, stats


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


def expected_calibration_error(y_true: np.ndarray, probs: np.ndarray, bins: int = 15) -> float:
    confidences = np.max(probs, axis=1)
    predictions = np.argmax(probs, axis=1)
    correctness = (predictions == y_true).astype(np.float32)
    edges = np.linspace(0.0, 1.0, bins + 1)
    ece = 0.0
    n = len(y_true)
    for i in range(bins):
        lo, hi = edges[i], edges[i + 1]
        if i == bins - 1:
            mask = (confidences >= lo) & (confidences <= hi)
        else:
            mask = (confidences >= lo) & (confidences < hi)
        if not np.any(mask):
            continue
        bin_acc = float(np.mean(correctness[mask]))
        bin_conf = float(np.mean(confidences[mask]))
        ece += (np.sum(mask) / max(n, 1)) * abs(bin_acc - bin_conf)
    return float(ece)


def multiclass_nll(y_true: np.ndarray, probs: np.ndarray) -> float:
    eps = 1e-12
    log_probs = np.log(np.clip(probs, eps, 1.0))
    return float(-np.mean(log_probs[np.arange(y_true.shape[0]), y_true]))


def build_confusion_matrix(y_true: np.ndarray, y_pred: np.ndarray, num_labels: int) -> np.ndarray:
    matrix = np.zeros((num_labels, num_labels), dtype=np.int64)
    for truth, pred in zip(y_true, y_pred):
        matrix[int(truth), int(pred)] += 1
    return matrix


def evaluate(
    model: AutoModelForSequenceClassification,
    loader: DataLoader,
    device: torch.device,
) -> tuple[dict[str, float], np.ndarray, np.ndarray, np.ndarray]:
    model.eval()
    logits_all: list[np.ndarray] = []
    labels_all: list[np.ndarray] = []
    loss_sum = 0.0
    total = 0
    loss_fn = nn.CrossEntropyLoss()

    with torch.no_grad():
        for batch in loader:
            labels = batch["labels"].to(device)
            inputs = {k: v.to(device) for k, v in batch.items() if k != "labels"}
            outputs = model(**inputs)
            logits = outputs.logits
            loss = loss_fn(logits, labels)
            logits_all.append(logits.detach().cpu().numpy())
            labels_all.append(labels.detach().cpu().numpy())
            loss_sum += float(loss.item()) * labels.shape[0]
            total += int(labels.shape[0])

    logits_np = np.concatenate(logits_all, axis=0)
    labels_np = np.concatenate(labels_all, axis=0)
    probs = torch.softmax(torch.from_numpy(logits_np), dim=-1).numpy()
    preds = np.argmax(probs, axis=-1)

    metrics = {
        "loss": loss_sum / max(total, 1),
        "accuracy": float(np.mean(preds == labels_np)),
        "macro_f1": macro_f1_score(labels_np, preds, num_labels=len(EMOTION_LABELS)),
        "balanced_accuracy": balanced_accuracy(labels_np, preds, num_labels=len(EMOTION_LABELS)),
        "nll": multiclass_nll(labels_np, probs),
        "ece": expected_calibration_error(labels_np, probs),
    }
    return metrics, labels_np, preds, probs


def class_weights_from_rows(rows: list[TextRow]) -> torch.Tensor:
    counts = np.zeros(len(EMOTION_LABELS), dtype=np.float64)
    for row in rows:
        counts[row.label_id] += 1.0
    total = float(np.sum(counts))
    weights = np.where(counts > 0.0, total / (len(EMOTION_LABELS) * counts), 0.0)
    return torch.tensor(weights, dtype=torch.float32)


def save_confusion(path: Path, y_true: np.ndarray, y_pred: np.ndarray) -> None:
    matrix = build_confusion_matrix(y_true, y_pred, num_labels=len(EMOTION_LABELS))
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["truth\\pred", *EMOTION_LABELS])
        for idx, label in enumerate(EMOTION_LABELS):
            writer.writerow([label, *matrix[idx].tolist()])

def main() -> None:
    args = parse_args()
    set_seed(args.seed)
    device = resolve_device(args.device)

    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    best_dir = output_dir / "best_model"

    train_rows, train_stats = read_rows(args.train_features, args.language_filter, args.min_text_chars)
    val_rows, val_stats = read_rows(args.val_features, args.language_filter, args.min_text_chars)
    test_rows = None
    test_stats = None
    if args.test_features:
        test_rows, test_stats = read_rows(args.test_features, args.language_filter, args.min_text_chars)

    tokenizer = AutoTokenizer.from_pretrained(args.base_model)
    model = AutoModelForSequenceClassification.from_pretrained(
        args.base_model,
        num_labels=len(EMOTION_LABELS),
        id2label={idx: label for idx, label in enumerate(EMOTION_LABELS)},
        label2id={label: idx for idx, label in enumerate(EMOTION_LABELS)},
        ignore_mismatched_sizes=True,
    ).to(device)

    train_ds = TextDataset(train_rows, tokenizer, args.max_length)
    val_ds = TextDataset(val_rows, tokenizer, args.max_length)
    test_ds = TextDataset(test_rows, tokenizer, args.max_length) if test_rows is not None else None

    train_loader = DataLoader(
        train_ds,
        batch_size=args.batch_size,
        shuffle=True,
        num_workers=args.num_workers,
        collate_fn=lambda batch: collate_fn(batch, tokenizer),
    )
    val_loader = DataLoader(
        val_ds,
        batch_size=args.batch_size,
        shuffle=False,
        num_workers=args.num_workers,
        collate_fn=lambda batch: collate_fn(batch, tokenizer),
    )
    test_loader = (
        DataLoader(
            test_ds,
            batch_size=args.batch_size,
            shuffle=False,
            num_workers=args.num_workers,
            collate_fn=lambda batch: collate_fn(batch, tokenizer),
        )
        if test_ds is not None
        else None
    )

    optimizer = AdamW(model.parameters(), lr=args.learning_rate, weight_decay=args.weight_decay)
    steps_per_epoch = max(1, math.ceil(len(train_loader)))
    total_steps = max(1, args.epochs * steps_per_epoch)
    warmup_steps = int(total_steps * max(0.0, min(1.0, args.warmup_ratio)))
    scheduler = get_linear_schedule_with_warmup(optimizer, warmup_steps, total_steps)

    class_weights = None
    if not args.disable_class_weighted_loss:
        class_weights = class_weights_from_rows(train_rows).to(device)
    loss_fn = nn.CrossEntropyLoss(weight=class_weights)

    best_state = None
    best_epoch = -1
    best_val_f1 = -1.0
    bad_epochs = 0
    history: list[dict[str, float | int]] = []
    start_time = time.time()

    for epoch in range(1, args.epochs + 1):
        model.train()
        running_loss = 0.0
        seen = 0

        for batch in train_loader:
            labels = batch["labels"].to(device)
            inputs = {k: v.to(device) for k, v in batch.items() if k != "labels"}
            optimizer.zero_grad(set_to_none=True)
            outputs = model(**inputs)
            logits = outputs.logits
            loss = loss_fn(logits, labels)
            loss.backward()
            optimizer.step()
            scheduler.step()

            count = int(labels.shape[0])
            running_loss += float(loss.item()) * count
            seen += count

        val_metrics, _, _, _ = evaluate(model, val_loader, device)
        train_metrics, _, _, _ = evaluate(model, train_loader, device)

        epoch_report = {
            "epoch": epoch,
            "train_loss": running_loss / max(seen, 1),
            "train_macro_f1": train_metrics["macro_f1"],
            "val_macro_f1": val_metrics["macro_f1"],
            "val_accuracy": val_metrics["accuracy"],
            "learning_rate": float(scheduler.get_last_lr()[0]),
        }
        history.append(epoch_report)
        print(json.dumps(epoch_report, ensure_ascii=False))

        if val_metrics["macro_f1"] > best_val_f1:
            best_val_f1 = val_metrics["macro_f1"]
            best_epoch = epoch
            bad_epochs = 0
            best_state = {k: v.detach().cpu() for k, v in model.state_dict().items()}
        else:
            bad_epochs += 1
            if bad_epochs >= args.patience:
                print(f"early stopping at epoch={epoch}, patience={args.patience}")
                break

    if best_state is None:
        raise RuntimeError("training finished without best_state")

    model.load_state_dict(best_state)

    val_metrics, val_true, val_pred, _ = evaluate(model, val_loader, device)
    save_confusion(output_dir / "val_confusion.csv", val_true, val_pred)

    test_metrics = None
    if test_loader is not None:
        test_metrics, test_true, test_pred, _ = evaluate(model, test_loader, device)
        save_confusion(output_dir / "test_confusion.csv", test_true, test_pred)

    best_dir.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(best_dir)
    tokenizer.save_pretrained(best_dir)

    with (output_dir / "label_map.json").open("w", encoding="utf-8") as f:
        json.dump(
            {
                "labels": list(EMOTION_LABELS),
                "label2id": {label: idx for idx, label in enumerate(EMOTION_LABELS)},
                "id2label": {idx: label for idx, label in enumerate(EMOTION_LABELS)},
                "task": "emotion_4class",
            },
            f,
            ensure_ascii=False,
            indent=2,
        )

    report = {
        "base_model": args.base_model,
        "language_filter": args.language_filter,
        "train_rows": len(train_rows),
        "val_rows": len(val_rows),
        "test_rows": len(test_rows) if test_rows is not None else 0,
        "train_stats": train_stats,
        "val_stats": val_stats,
        "test_stats": test_stats,
        "best_epoch": best_epoch,
        "best_val_macro_f1": best_val_f1,
        "val_metrics": val_metrics,
        "test_metrics": test_metrics,
        "epochs_requested": args.epochs,
        "epochs_ran": len(history),
        "history": history,
        "runtime_seconds": time.time() - start_time,
        "device": str(device),
    }
    with (output_dir / "train_report.json").open("w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print(json.dumps({"status": "ok", "output_dir": str(output_dir), "best_epoch": best_epoch}, ensure_ascii=False))


if __name__ == "__main__":
    main()

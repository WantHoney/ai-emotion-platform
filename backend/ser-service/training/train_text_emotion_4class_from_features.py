from __future__ import annotations

import argparse
import csv
import json
import math
import random
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

import numpy as np
import torch
from torch import nn
from torch.optim import AdamW
from torch.utils.data import DataLoader, Dataset, WeightedRandomSampler
from transformers import AutoModelForSequenceClassification, AutoTokenizer, get_linear_schedule_with_warmup


EMOTION_LABELS = ("ANG", "HAP", "NEU", "SAD")


@dataclass(frozen=True)
class TextRow:
    sample_id: str
    text: str
    label: str
    label_id: int
    language: str
    path: str
    source: str
    source_kind: str
    speaker: str
    is_key_hard_case: bool
    label_source: str
    human_label: str
    teacher_confidence: float | None
    teacher_conflict_hint: str
    teacher_scores4: tuple[float, float, float, float] | None


class TextDataset(Dataset):
    def __init__(self, rows: list[TextRow], tokenizer: AutoTokenizer, max_length: int) -> None:
        self.rows = rows
        self.tokenizer = tokenizer
        self.max_length = max_length

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, idx: int) -> dict[str, Any]:
        row = self.rows[idx]
        encoded = self.tokenizer(
            row.text,
            truncation=True,
            max_length=self.max_length,
            return_attention_mask=True,
        )
        encoded["labels"] = row.label_id
        encoded["teacher_scores4"] = list(row.teacher_scores4) if row.teacher_scores4 is not None else [0.0] * len(EMOTION_LABELS)
        encoded["teacher_weight"] = float(row.teacher_confidence) if row.teacher_confidence is not None else 0.0
        return encoded


def collate_fn(batch: list[dict[str, Any]], tokenizer: AutoTokenizer) -> dict[str, torch.Tensor]:
    labels = torch.tensor([int(item["labels"]) for item in batch], dtype=torch.long)
    teacher_scores4 = torch.tensor([item["teacher_scores4"] for item in batch], dtype=torch.float32)
    teacher_weight = torch.tensor([float(item["teacher_weight"]) for item in batch], dtype=torch.float32)
    items = [{k: v for k, v in item.items() if k not in {"labels", "teacher_scores4", "teacher_weight"}} for item in batch]
    padded = tokenizer.pad(items, return_tensors="pt")
    padded["labels"] = labels
    padded["teacher_scores4"] = teacher_scores4
    padded["teacher_weight"] = teacher_weight
    return padded


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train thesis v4 local 4-class text emotion model from frozen manifests.")
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
    parser.add_argument("--distill-alpha", type=float, default=0.35)
    parser.add_argument("--distill-temperature", type=float, default=2.0)
    parser.add_argument("--disable-distillation", action="store_true")
    parser.add_argument("--real-world-sample-weight", type=float, default=1.0)
    parser.add_argument("--controlled-sample-weight", type=float, default=1.0)
    parser.add_argument("--run-metadata", default="")
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


def parse_bool(value: Any) -> bool:
    return str(value or "").strip().lower() in {"1", "true", "yes"}


def parse_optional_float(value: Any) -> float | None:
    if value in {None, ""}:
        return None
    try:
        return float(value)
    except Exception:
        return None


def parse_teacher_scores4(row: dict[str, str]) -> tuple[float, float, float, float] | None:
    raw = (row.get("teacher_scores4_json") or "").strip()
    payload: dict[str, Any] | None = None
    if raw:
        try:
            decoded = json.loads(raw)
            if isinstance(decoded, dict):
                payload = decoded
        except Exception:
            payload = None

    if payload:
        values = [max(0.0, float(payload.get(label, 0.0) or 0.0)) for label in EMOTION_LABELS]
        total = sum(values)
        if total > 0.0:
            return tuple(value / total for value in values)  # type: ignore[return-value]

    fallback_label = (row.get("teacher_label4") or row.get("label") or "").strip()
    if fallback_label:
        emotion = normalize_emotion(fallback_label)
        return tuple(1.0 if label == emotion else 0.0 for label in EMOTION_LABELS)
    return None


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

    with Path(path).open("r", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
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
                    emotion = EMOTION_LABELS[int(float(label_id))]
                else:
                    raise ValueError("missing label")
            except Exception:
                stats["dropped_bad_label"] += 1
                continue

            teacher_scores4 = parse_teacher_scores4(row)
            teacher_confidence = parse_optional_float(row.get("teacher_confidence"))
            if teacher_confidence is not None:
                teacher_confidence = max(0.0, min(1.0, teacher_confidence))
            elif teacher_scores4 is not None:
                teacher_confidence = 1.0

            rows.append(
                TextRow(
                    sample_id=(row.get("sample_id") or "").strip(),
                    text=text,
                    label=emotion,
                    label_id=label2id[emotion],
                    language=lang,
                    path=(row.get("path") or "").strip(),
                    source=(row.get("source") or "").strip(),
                    source_kind=(row.get("source_kind") or "").strip(),
                    speaker=(row.get("speaker") or "").strip(),
                    is_key_hard_case=parse_bool(row.get("is_key_hard_case")),
                    label_source=(row.get("label_source") or "").strip(),
                    human_label=(row.get("human_label") or "").strip(),
                    teacher_confidence=teacher_confidence,
                    teacher_conflict_hint=(row.get("teacher_conflict_hint") or "").strip(),
                    teacher_scores4=teacher_scores4,
                )
            )
            stats["kept"] += 1
            stats[f"kept_{lang}"] += 1

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
        scores.append(float(2 * precision * recall / (precision + recall + eps)))
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
    for idx in range(bins):
        lo, hi = edges[idx], edges[idx + 1]
        if idx == bins - 1:
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


def per_class_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> dict[str, dict[str, float | int]]:
    eps = 1e-12
    metrics: dict[str, dict[str, float | int]] = {}
    for idx, label in enumerate(EMOTION_LABELS):
        tp = np.sum((y_true == idx) & (y_pred == idx))
        fp = np.sum((y_true != idx) & (y_pred == idx))
        fn = np.sum((y_true == idx) & (y_pred != idx))
        support = int(np.sum(y_true == idx))
        precision = tp / (tp + fp + eps)
        recall = tp / (tp + fn + eps)
        f1 = 2 * precision * recall / (precision + recall + eps)
        metrics[label] = {
            "precision": float(precision),
            "recall": float(recall),
            "f1": float(f1),
            "support": support,
        }
    return metrics


def compute_batch_loss(
    *,
    logits: torch.Tensor,
    labels: torch.Tensor,
    teacher_scores4: torch.Tensor | None,
    teacher_weight: torch.Tensor | None,
    ce_loss_fn: nn.Module,
    distill_alpha: float,
    distill_temperature: float,
) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    ce_loss = ce_loss_fn(logits, labels)
    if teacher_scores4 is None or teacher_weight is None or distill_alpha <= 0.0:
        zero = torch.zeros((), device=logits.device)
        return ce_loss, ce_loss.detach(), zero

    teacher_probs = teacher_scores4.to(logits.device)
    weights = torch.clamp(teacher_weight.to(logits.device), min=0.0, max=1.0)
    if not torch.any(weights > 0):
        zero = torch.zeros((), device=logits.device)
        return ce_loss, ce_loss.detach(), zero

    teacher_probs = torch.clamp(teacher_probs, min=1e-8)
    teacher_probs = teacher_probs / teacher_probs.sum(dim=-1, keepdim=True)
    student_log_probs = torch.log_softmax(logits / distill_temperature, dim=-1)
    teacher_log_probs = torch.log(teacher_probs)
    kl_per_sample = torch.sum(teacher_probs * (teacher_log_probs - student_log_probs), dim=-1)
    distill_loss = ((kl_per_sample * weights).sum() / torch.clamp(weights.sum(), min=1e-8)) * (distill_temperature ** 2)
    loss = ((1.0 - distill_alpha) * ce_loss) + (distill_alpha * distill_loss)
    return loss, ce_loss.detach(), distill_loss.detach()


def evaluate(
    model: AutoModelForSequenceClassification,
    loader: DataLoader,
    device: torch.device,
    ce_loss_fn: nn.Module,
    distill_alpha: float,
    distill_temperature: float,
) -> tuple[dict[str, float | dict[str, dict[str, float | int]]], np.ndarray, np.ndarray, np.ndarray]:
    model.eval()
    logits_all: list[np.ndarray] = []
    labels_all: list[np.ndarray] = []
    loss_sum = 0.0
    ce_loss_sum = 0.0
    distill_loss_sum = 0.0
    total = 0

    with torch.no_grad():
        for batch in loader:
            labels = batch["labels"].to(device)
            teacher_scores4 = batch.get("teacher_scores4")
            teacher_weight = batch.get("teacher_weight")
            inputs = {
                k: v.to(device)
                for k, v in batch.items()
                if k not in {"labels", "teacher_scores4", "teacher_weight"}
            }
            outputs = model(**inputs)
            logits = outputs.logits
            loss, ce_loss, distill_loss = compute_batch_loss(
                logits=logits,
                labels=labels,
                teacher_scores4=teacher_scores4,
                teacher_weight=teacher_weight,
                ce_loss_fn=ce_loss_fn,
                distill_alpha=distill_alpha,
                distill_temperature=distill_temperature,
            )
            logits_all.append(logits.detach().cpu().numpy())
            labels_all.append(labels.detach().cpu().numpy())
            loss_sum += float(loss.item()) * labels.shape[0]
            ce_loss_sum += float(ce_loss.item()) * labels.shape[0]
            distill_loss_sum += float(distill_loss.item()) * labels.shape[0]
            total += int(labels.shape[0])

    logits_np = np.concatenate(logits_all, axis=0)
    labels_np = np.concatenate(labels_all, axis=0)
    probs = torch.softmax(torch.from_numpy(logits_np), dim=-1).numpy()
    preds = np.argmax(probs, axis=-1)
    metrics = {
        "loss": loss_sum / max(total, 1),
        "ce_loss": ce_loss_sum / max(total, 1),
        "distill_loss": distill_loss_sum / max(total, 1),
        "accuracy": float(np.mean(preds == labels_np)),
        "macro_f1": macro_f1_score(labels_np, preds, num_labels=len(EMOTION_LABELS)),
        "balanced_accuracy": balanced_accuracy(labels_np, preds, num_labels=len(EMOTION_LABELS)),
        "nll": multiclass_nll(labels_np, probs),
        "ece": expected_calibration_error(labels_np, probs),
    }
    metrics["per_class"] = per_class_metrics(labels_np, preds)
    return metrics, labels_np, preds, probs


def class_weights_from_rows(rows: list[TextRow]) -> torch.Tensor:
    counts = np.zeros(len(EMOTION_LABELS), dtype=np.float64)
    for row in rows:
        counts[row.label_id] += 1.0
    total = float(np.sum(counts))
    weights = np.where(counts > 0.0, total / (len(EMOTION_LABELS) * counts), 0.0)
    return torch.tensor(weights, dtype=torch.float32)


def is_real_domain_row(row: TextRow) -> bool:
    return row.source == "real_world" or row.source_kind in {"intake_recording", "db_audio_file"}


def build_train_sampler(
    rows: list[TextRow],
    real_world_sample_weight: float,
    controlled_sample_weight: float,
) -> WeightedRandomSampler | None:
    real_weight = max(0.0, real_world_sample_weight)
    controlled_weight = max(0.0, controlled_sample_weight)
    weights: list[float] = []
    for row in rows:
        weights.append(real_weight if is_real_domain_row(row) else controlled_weight)
    if not weights or all(abs(weight - weights[0]) < 1e-8 for weight in weights):
        return None
    if max(weights) <= 0.0:
        raise ValueError("source sampler weights must include at least one positive value")
    return WeightedRandomSampler(weights=weights, num_samples=len(rows), replacement=True)


def save_confusion(path: Path, y_true: np.ndarray, y_pred: np.ndarray) -> None:
    matrix = build_confusion_matrix(y_true, y_pred, num_labels=len(EMOTION_LABELS))
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["truth\\pred", *EMOTION_LABELS])
        for idx, label in enumerate(EMOTION_LABELS):
            writer.writerow([label, *matrix[idx].tolist()])


def save_predictions(path: Path, rows: list[TextRow], y_true: np.ndarray, y_pred: np.ndarray, probs: np.ndarray) -> None:
    fieldnames = [
        "sample_id",
        "path",
        "source",
        "source_kind",
        "speaker",
        "truth_label",
        "pred_label",
        "confidence",
        "is_correct",
        "is_key_hard_case",
        "label_source",
        "human_label",
        "teacher_confidence",
        "teacher_conflict_hint",
        "teacher_scores4_json",
        "transcript",
        "probs_json",
    ]
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for idx, row in enumerate(rows):
            prob_map = {label: float(probs[idx][label_idx]) for label_idx, label in enumerate(EMOTION_LABELS)}
            writer.writerow(
                {
                    "sample_id": row.sample_id,
                    "path": row.path,
                    "source": row.source,
                    "source_kind": row.source_kind,
                    "speaker": row.speaker,
                    "truth_label": EMOTION_LABELS[int(y_true[idx])],
                    "pred_label": EMOTION_LABELS[int(y_pred[idx])],
                    "confidence": float(np.max(probs[idx])),
                    "is_correct": bool(int(y_true[idx]) == int(y_pred[idx])),
                    "is_key_hard_case": row.is_key_hard_case,
                    "label_source": row.label_source,
                    "human_label": row.human_label,
                    "teacher_confidence": row.teacher_confidence,
                    "teacher_conflict_hint": row.teacher_conflict_hint,
                    "teacher_scores4_json": json.dumps(
                        {
                            label: None if row.teacher_scores4 is None else float(row.teacher_scores4[label_idx])
                            for label_idx, label in enumerate(EMOTION_LABELS)
                        },
                        ensure_ascii=False,
                    ),
                    "transcript": row.text,
                    "probs_json": json.dumps(prob_map, ensure_ascii=False),
                }
            )


def save_key_sample_report(path: Path, rows: list[TextRow], y_true: np.ndarray, y_pred: np.ndarray, probs: np.ndarray) -> None:
    payload = []
    for idx, row in enumerate(rows):
        if not (row.is_key_hard_case or row.source == "real_world" or row.human_label):
            continue
        payload.append(
            {
                "sample_id": row.sample_id,
                "path": row.path,
                "source": row.source,
                "source_kind": row.source_kind,
                "truth_label": EMOTION_LABELS[int(y_true[idx])],
                "pred_label": EMOTION_LABELS[int(y_pred[idx])],
                "confidence": float(np.max(probs[idx])),
                "is_correct": bool(int(y_true[idx]) == int(y_pred[idx])),
                "is_key_hard_case": row.is_key_hard_case,
                "label_source": row.label_source,
                "human_label": row.human_label,
                "teacher_confidence": row.teacher_confidence,
                "teacher_conflict_hint": row.teacher_conflict_hint,
                "teacher_scores4": {
                    label: None if row.teacher_scores4 is None else float(row.teacher_scores4[label_idx])
                    for label_idx, label in enumerate(EMOTION_LABELS)
                },
                "transcript": row.text,
                "probabilities": {label: float(probs[idx][label_idx]) for label_idx, label in enumerate(EMOTION_LABELS)},
            }
        )
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def load_run_metadata(path: str) -> dict[str, Any] | None:
    if not path:
        return None
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError(f"run metadata must be a JSON object: {path}")
    return payload


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
    train_sampler = build_train_sampler(train_rows, args.real_world_sample_weight, args.controlled_sample_weight)

    train_loader = DataLoader(
        train_ds,
        batch_size=args.batch_size,
        shuffle=train_sampler is None,
        sampler=train_sampler,
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
    distill_alpha = 0.0 if args.disable_distillation else max(0.0, min(1.0, args.distill_alpha))
    distill_temperature = max(1e-3, args.distill_temperature)

    best_state = None
    best_epoch = -1
    best_val_f1 = -1.0
    bad_epochs = 0
    history: list[dict[str, float | int]] = []
    start_time = time.time()

    for epoch in range(1, args.epochs + 1):
        model.train()
        running_loss = 0.0
        running_ce_loss = 0.0
        running_distill_loss = 0.0
        seen = 0
        for batch in train_loader:
            labels = batch["labels"].to(device)
            teacher_scores4 = batch.get("teacher_scores4")
            teacher_weight = batch.get("teacher_weight")
            inputs = {
                k: v.to(device)
                for k, v in batch.items()
                if k not in {"labels", "teacher_scores4", "teacher_weight"}
            }
            optimizer.zero_grad(set_to_none=True)
            outputs = model(**inputs)
            logits = outputs.logits
            loss, ce_loss, distill_loss = compute_batch_loss(
                logits=logits,
                labels=labels,
                teacher_scores4=teacher_scores4,
                teacher_weight=teacher_weight,
                ce_loss_fn=loss_fn,
                distill_alpha=distill_alpha,
                distill_temperature=distill_temperature,
            )
            loss.backward()
            optimizer.step()
            scheduler.step()
            count = int(labels.shape[0])
            running_loss += float(loss.item()) * count
            running_ce_loss += float(ce_loss.item()) * count
            running_distill_loss += float(distill_loss.item()) * count
            seen += count

        val_metrics, _, _, _ = evaluate(
            model,
            val_loader,
            device,
            loss_fn,
            distill_alpha,
            distill_temperature,
        )
        train_metrics, _, _, _ = evaluate(
            model,
            train_loader,
            device,
            loss_fn,
            distill_alpha,
            distill_temperature,
        )
        epoch_report = {
            "epoch": epoch,
            "train_loss": running_loss / max(seen, 1),
            "train_ce_loss": running_ce_loss / max(seen, 1),
            "train_distill_loss": running_distill_loss / max(seen, 1),
            "train_macro_f1": train_metrics["macro_f1"],
            "val_loss": val_metrics["loss"],
            "val_ce_loss": val_metrics["ce_loss"],
            "val_distill_loss": val_metrics["distill_loss"],
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
            best_state = {key: value.detach().cpu().clone() for key, value in model.state_dict().items()}
        else:
            bad_epochs += 1
            if bad_epochs >= args.patience:
                print(f"early stopping at epoch={epoch}, patience={args.patience}")
                break

    if best_state is None:
        raise RuntimeError("training finished without best_state")

    model.load_state_dict(best_state)

    val_metrics, val_true, val_pred, val_probs = evaluate(
        model,
        val_loader,
        device,
        loss_fn,
        distill_alpha,
        distill_temperature,
    )
    save_confusion(output_dir / "val_confusion.csv", val_true, val_pred)
    save_predictions(output_dir / "val_predictions.csv", val_rows, val_true, val_pred, val_probs)

    test_metrics = None
    if test_loader is not None and test_rows is not None:
        test_metrics, test_true, test_pred, test_probs = evaluate(
            model,
            test_loader,
            device,
            loss_fn,
            distill_alpha,
            distill_temperature,
        )
        save_confusion(output_dir / "test_confusion.csv", test_true, test_pred)
        save_predictions(output_dir / "test_predictions.csv", test_rows, test_true, test_pred, test_probs)
        save_key_sample_report(output_dir / "test_key_samples.json", test_rows, test_true, test_pred, test_probs)

    best_dir.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(best_dir)
    tokenizer.save_pretrained(best_dir)

    with (output_dir / "label_map.json").open("w", encoding="utf-8") as handle:
        json.dump(
            {
                "labels": list(EMOTION_LABELS),
                "label2id": {label: idx for idx, label in enumerate(EMOTION_LABELS)},
                "id2label": {idx: label for idx, label in enumerate(EMOTION_LABELS)},
                "task": "emotion_4class",
            },
            handle,
            ensure_ascii=False,
            indent=2,
        )

    run_metadata = load_run_metadata(args.run_metadata)
    artifact_paths = {
        "bestModelDir": str(best_dir),
        "labelMap": str((output_dir / "label_map.json").resolve()),
        "valConfusion": str((output_dir / "val_confusion.csv").resolve()),
        "valPredictions": str((output_dir / "val_predictions.csv").resolve()),
        "testConfusion": None if test_metrics is None else str((output_dir / "test_confusion.csv").resolve()),
        "testPredictions": None if test_metrics is None else str((output_dir / "test_predictions.csv").resolve()),
        "testKeySamples": None if test_metrics is None else str((output_dir / "test_key_samples.json").resolve()),
    }
    report = {
        "schemaVersion": "thesis_v4.local_text_train_report.v3",
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "base_model": args.base_model,
        "language_filter": args.language_filter,
        "distillation_enabled": not args.disable_distillation and distill_alpha > 0.0,
        "distill_alpha": distill_alpha,
        "distill_temperature": distill_temperature,
        "real_world_sample_weight": args.real_world_sample_weight,
        "controlled_sample_weight": args.controlled_sample_weight,
        "source_weighted_sampler": train_sampler is not None,
        "training_config": {
            "epochs": args.epochs,
            "batch_size": args.batch_size,
            "learning_rate": args.learning_rate,
            "weight_decay": args.weight_decay,
            "warmup_ratio": args.warmup_ratio,
            "patience": args.patience,
            "class_weighted_loss": not args.disable_class_weighted_loss,
            "distillation_enabled": not args.disable_distillation and distill_alpha > 0.0,
            "distill_alpha": distill_alpha,
            "distill_temperature": distill_temperature,
            "real_world_sample_weight": args.real_world_sample_weight,
            "controlled_sample_weight": args.controlled_sample_weight,
            "source_weighted_sampler": train_sampler is not None,
        },
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
        "artifacts": artifact_paths,
        "provenance": run_metadata,
        "thesisPipelineArtifactsComplete": {
            "metrics": True,
            "confusionMatrix": True,
            "perClassMetrics": True,
            "keySampleComparisons": bool(test_metrics is not None),
            "provenanceMetadata": bool(run_metadata),
        },
    }
    with (output_dir / "train_report.json").open("w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)

    print(json.dumps({"status": "ok", "output_dir": str(output_dir), "best_epoch": best_epoch}, ensure_ascii=False))


if __name__ == "__main__":
    main()

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


CANONICAL_EMOTIONS = ("ANG", "HAP", "NEU", "SAD")
AUDIO_FEATURES = (
    "audio_prob_ang",
    "audio_prob_hap",
    "audio_prob_neu",
    "audio_prob_sad",
    "audio_confidence",
    "audio_entropy",
)
TEXT_FEATURES = (
    "text_negative",
    "text_neutral",
    "text_positive",
    "text_negative_score",
    "text_length_norm",
)


@dataclass(frozen=True)
class DatasetSplit:
    x: np.ndarray
    y: np.ndarray


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train learnable late-fusion classifier.")
    parser.add_argument("--train-features", required=True)
    parser.add_argument("--val-features", required=True)
    parser.add_argument("--test-features", default="")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--mode", default="fusion", choices=["fusion", "audio_only", "text_only"])
    parser.add_argument("--hidden-size", type=int, default=64)
    parser.add_argument("--dropout", type=float, default=0.2)
    parser.add_argument("--epochs", type=int, default=80)
    parser.add_argument("--batch-size", type=int, default=128)
    parser.add_argument("--learning-rate", type=float, default=2e-3)
    parser.add_argument("--weight-decay", type=float, default=1e-4)
    parser.add_argument("--patience", type=int, default=12)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", default="auto", choices=["auto", "cpu", "cuda"])
    parser.add_argument("--calibration-max-iter", type=int, default=200)
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


def feature_columns(mode: str) -> tuple[str, ...]:
    if mode == "audio_only":
        return AUDIO_FEATURES
    if mode == "text_only":
        return TEXT_FEATURES
    return AUDIO_FEATURES + TEXT_FEATURES


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


def multiclass_nll(y_true: np.ndarray, probs: np.ndarray) -> float:
    eps = 1e-12
    log_probs = np.log(np.clip(probs, eps, 1.0))
    return float(-np.mean(log_probs[np.arange(y_true.shape[0]), y_true]))


def multiclass_brier(y_true: np.ndarray, probs: np.ndarray, num_labels: int) -> float:
    onehot = np.zeros((y_true.shape[0], num_labels), dtype=np.float32)
    onehot[np.arange(y_true.shape[0]), y_true] = 1.0
    return float(np.mean(np.sum((probs - onehot) ** 2, axis=1)))


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


def normalize_emotion(label: str) -> str:
    key = label.strip().upper()
    if key in {"ANG", "ANGRY"}:
        return "ANG"
    if key in {"HAP", "HAPPY", "EXC"}:
        return "HAP"
    if key in {"NEU", "NEUTRAL", "CALM"}:
        return "NEU"
    if key in {"SAD", "SADNESS"}:
        return "SAD"
    raise ValueError(f"unsupported label: {label}")


def load_split(path: str | Path, mode: str) -> DatasetSplit:
    cols = feature_columns(mode)
    x_rows: list[list[float]] = []
    y_rows: list[int] = []
    label2id = {label: idx for idx, label in enumerate(CANONICAL_EMOTIONS)}
    with Path(path).open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            label = (row.get("label") or "").strip()
            label_id = (row.get("label_id") or "").strip()
            if label_id:
                y = int(float(label_id))
            else:
                y = label2id[normalize_emotion(label)]
            feature = [float(row.get(c, 0.0) or 0.0) for c in cols]
            x_rows.append(feature)
            y_rows.append(y)
    if not x_rows:
        raise ValueError(f"empty feature file: {path}")
    return DatasetSplit(
        x=np.asarray(x_rows, dtype=np.float32),
        y=np.asarray(y_rows, dtype=np.int64),
    )


def standardize(train: np.ndarray, x: np.ndarray, eps: float = 1e-6) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    mean = np.mean(train, axis=0)
    std = np.std(train, axis=0)
    std = np.where(std < eps, 1.0, std)
    return (x - mean) / std, mean, std


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


def to_batches(x: np.ndarray, y: np.ndarray, batch_size: int, shuffle: bool = False) -> list[tuple[np.ndarray, np.ndarray]]:
    indices = np.arange(x.shape[0])
    if shuffle:
        np.random.shuffle(indices)
    out = []
    for start in range(0, x.shape[0], batch_size):
        idx = indices[start : start + batch_size]
        out.append((x[idx], y[idx]))
    return out


def evaluate_logits(
    model: FusionMLP,
    x: np.ndarray,
    y: np.ndarray,
    device: torch.device,
    batch_size: int,
) -> tuple[np.ndarray, dict]:
    model.eval()
    logits_list: list[np.ndarray] = []
    loss_sum = 0.0
    total = 0
    loss_fn = nn.CrossEntropyLoss()
    with torch.no_grad():
        for xb, yb in to_batches(x, y, batch_size=batch_size, shuffle=False):
            tx = torch.from_numpy(xb).to(device)
            ty = torch.from_numpy(yb).to(device)
            logits = model(tx)
            loss = loss_fn(logits, ty)
            logits_np = logits.detach().cpu().numpy()
            logits_list.append(logits_np)
            loss_sum += float(loss.item()) * yb.shape[0]
            total += yb.shape[0]

    logits_all = np.concatenate(logits_list, axis=0)
    probs = torch.softmax(torch.from_numpy(logits_all), dim=-1).numpy()
    preds = np.argmax(probs, axis=-1)
    metrics = {
        "loss": loss_sum / max(total, 1),
        "accuracy": float(np.mean(preds == y)),
        "macro_f1": macro_f1_score(y, preds, num_labels=len(CANONICAL_EMOTIONS)),
        "balanced_accuracy": balanced_accuracy(y, preds, num_labels=len(CANONICAL_EMOTIONS)),
        "nll": multiclass_nll(y, probs),
        "brier": multiclass_brier(y, probs, num_labels=len(CANONICAL_EMOTIONS)),
        "ece": expected_calibration_error(y, probs),
    }
    return logits_all, metrics


def fit_temperature(logits: np.ndarray, y: np.ndarray, max_iter: int, device: torch.device) -> float:
    logits_t = torch.tensor(logits, dtype=torch.float32, device=device)
    labels_t = torch.tensor(y, dtype=torch.long, device=device)
    log_temp = torch.nn.Parameter(torch.zeros(1, device=device))
    optimizer = torch.optim.LBFGS([log_temp], lr=0.1, max_iter=max_iter)
    loss_fn = nn.CrossEntropyLoss()

    def closure():
        optimizer.zero_grad()
        temperature = torch.exp(log_temp)
        loss = loss_fn(logits_t / temperature, labels_t)
        loss.backward()
        return loss

    optimizer.step(closure)
    with torch.no_grad():
        temperature = float(torch.exp(log_temp).detach().cpu().item())
    return float(max(0.05, min(20.0, temperature)))


def metrics_from_logits(logits: np.ndarray, y: np.ndarray, temperature: float = 1.0) -> dict:
    probs = torch.softmax(torch.from_numpy(logits / temperature), dim=-1).numpy()
    preds = np.argmax(probs, axis=-1)
    return {
        "accuracy": float(np.mean(preds == y)),
        "macro_f1": macro_f1_score(y, preds, num_labels=len(CANONICAL_EMOTIONS)),
        "balanced_accuracy": balanced_accuracy(y, preds, num_labels=len(CANONICAL_EMOTIONS)),
        "nll": multiclass_nll(y, probs),
        "brier": multiclass_brier(y, probs, num_labels=len(CANONICAL_EMOTIONS)),
        "ece": expected_calibration_error(y, probs),
    }


def save_confusion(path: Path, y_true: np.ndarray, y_pred: np.ndarray) -> None:
    matrix = build_confusion_matrix(y_true, y_pred, num_labels=len(CANONICAL_EMOTIONS))
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["truth\\pred", *CANONICAL_EMOTIONS])
        for idx, label in enumerate(CANONICAL_EMOTIONS):
            writer.writerow([label, *matrix[idx].tolist()])


def main() -> None:
    args = parse_args()
    set_seed(args.seed)
    device = resolve_device(args.device)
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    train = load_split(args.train_features, args.mode)
    val = load_split(args.val_features, args.mode)
    test = load_split(args.test_features, args.mode) if args.test_features else None

    x_train, mean, std = standardize(train.x, train.x)
    x_val = (val.x - mean) / std
    x_test = (test.x - mean) / std if test is not None else None

    model = FusionMLP(
        in_dim=x_train.shape[1],
        out_dim=len(CANONICAL_EMOTIONS),
        hidden_size=args.hidden_size,
        dropout=args.dropout,
    ).to(device)
    optimizer = AdamW(model.parameters(), lr=args.learning_rate, weight_decay=args.weight_decay)
    loss_fn = nn.CrossEntropyLoss()

    best_state = None
    best_f1 = -1.0
    best_epoch = -1
    bad_epochs = 0
    history = []
    start_time = time.time()

    for epoch in range(1, args.epochs + 1):
        model.train()
        losses = []
        for xb, yb in to_batches(x_train, train.y, batch_size=args.batch_size, shuffle=True):
            tx = torch.from_numpy(xb).to(device)
            ty = torch.from_numpy(yb).to(device)
            optimizer.zero_grad(set_to_none=True)
            logits = model(tx)
            loss = loss_fn(logits, ty)
            loss.backward()
            optimizer.step()
            losses.append(float(loss.item()))

        val_logits, val_metrics = evaluate_logits(model, x_val, val.y, device=device, batch_size=args.batch_size)
        train_logits, train_metrics = evaluate_logits(model, x_train, train.y, device=device, batch_size=args.batch_size)
        epoch_report = {
            "epoch": epoch,
            "train_loss": float(np.mean(losses)) if losses else 0.0,
            "train_macro_f1": train_metrics["macro_f1"],
            "val_macro_f1": val_metrics["macro_f1"],
            "val_accuracy": val_metrics["accuracy"],
            "val_nll": val_metrics["nll"],
        }
        history.append(epoch_report)
        print(json.dumps(epoch_report, ensure_ascii=False))

        if val_metrics["macro_f1"] > best_f1:
            best_f1 = val_metrics["macro_f1"]
            best_epoch = epoch
            bad_epochs = 0
            best_state = {
                "model": model.state_dict(),
                "val_logits": val_logits.copy(),
            }
        else:
            bad_epochs += 1
            if bad_epochs >= args.patience:
                print(f"early stopping at epoch={epoch}, patience={args.patience}")
                break

    if best_state is None:
        raise RuntimeError("training finished without best_state")

    model.load_state_dict(best_state["model"])
    val_logits, val_metrics_uncal = evaluate_logits(model, x_val, val.y, device=device, batch_size=args.batch_size)
    temperature = fit_temperature(val_logits, val.y, max_iter=args.calibration_max_iter, device=device)
    val_metrics_cal = metrics_from_logits(val_logits, val.y, temperature=temperature)

    test_metrics_uncal = None
    test_metrics_cal = None
    if test is not None and x_test is not None:
        test_logits, test_metrics_uncal_tmp = evaluate_logits(model, x_test, test.y, device=device, batch_size=args.batch_size)
        test_metrics_uncal = test_metrics_uncal_tmp
        test_metrics_cal = metrics_from_logits(test_logits, test.y, temperature=temperature)
        test_preds = np.argmax(torch.softmax(torch.from_numpy(test_logits / temperature), dim=-1).numpy(), axis=-1)
        save_confusion(output_dir / "test_confusion_calibrated.csv", test.y, test_preds)

    val_preds = np.argmax(torch.softmax(torch.from_numpy(val_logits / temperature), dim=-1).numpy(), axis=-1)
    save_confusion(output_dir / "val_confusion_calibrated.csv", val.y, val_preds)

    torch.save(
        {
            "model_state_dict": model.state_dict(),
            "input_dim": int(x_train.shape[1]),
            "hidden_size": args.hidden_size,
            "dropout": args.dropout,
            "labels": list(CANONICAL_EMOTIONS),
            "feature_columns": list(feature_columns(args.mode)),
            "temperature": temperature,
        },
        output_dir / "fusion_model.pt",
    )
    with (output_dir / "feature_scaler.json").open("w", encoding="utf-8") as f:
        json.dump(
            {
                "mean": mean.tolist(),
                "std": std.tolist(),
                "feature_columns": list(feature_columns(args.mode)),
            },
            f,
            ensure_ascii=False,
            indent=2,
        )

    report = {
        "mode": args.mode,
        "device": str(device),
        "train_samples": int(train.x.shape[0]),
        "val_samples": int(val.x.shape[0]),
        "test_samples": int(test.x.shape[0]) if test is not None else 0,
        "feature_columns": list(feature_columns(args.mode)),
        "best_epoch": best_epoch,
        "best_val_macro_f1": best_f1,
        "temperature": temperature,
        "val_metrics_uncalibrated": val_metrics_uncal,
        "val_metrics_calibrated": val_metrics_cal,
        "test_metrics_uncalibrated": test_metrics_uncal,
        "test_metrics_calibrated": test_metrics_cal,
        "history": history,
        "runtime_seconds": time.time() - start_time,
    }
    with (output_dir / "train_report.json").open("w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print(json.dumps({"status": "ok", "output_dir": str(output_dir), "best_epoch": best_epoch}, ensure_ascii=False))


if __name__ == "__main__":
    main()

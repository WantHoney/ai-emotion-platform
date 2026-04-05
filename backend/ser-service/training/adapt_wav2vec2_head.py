import argparse
import json
import time
from pathlib import Path

import torch
from torch.optim import AdamW
from torch.utils.data import DataLoader
from transformers import AutoFeatureExtractor, Wav2Vec2ForSequenceClassification, get_linear_schedule_with_warmup

from train_wav2vec2_cls import (
    AudioEmotionDataset,
    Wav2Vec2BatchCollator,
    build_label_set,
    evaluate_model,
    read_manifest,
    save_confusion_matrix,
    set_seed,
    to_device,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Adapt a Wav2Vec2 SER checkpoint by training only projector/classifier heads.")
    parser.add_argument("--train-manifest", required=True)
    parser.add_argument("--val-manifest", required=True)
    parser.add_argument("--test-manifest", default="")
    parser.add_argument("--base-model", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--sample-rate", type=int, default=16000)
    parser.add_argument("--max-duration-sec", type=float, default=15.0)
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--batch-size", type=int, default=2)
    parser.add_argument("--learning-rate", type=float, default=1e-4)
    parser.add_argument("--weight-decay", type=float, default=1e-2)
    parser.add_argument("--warmup-ratio", type=float, default=0.1)
    parser.add_argument("--patience", type=int, default=3)
    parser.add_argument("--num-workers", type=int, default=0)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", default="auto", choices=["auto", "cpu", "cuda"])
    parser.add_argument(
        "--train-projector",
        action="store_true",
        help="When set, train both projector and classifier; otherwise only classifier is trainable.",
    )
    return parser.parse_args()


def freeze_for_head_adaptation(model: Wav2Vec2ForSequenceClassification, train_projector: bool) -> dict[str, int]:
    total_params = 0
    trainable_params = 0
    for name, parameter in model.named_parameters():
        allow = name.startswith("classifier.")
        if train_projector:
            allow = allow or name.startswith("projector.")
        parameter.requires_grad = allow
        total_params += parameter.numel()
        if parameter.requires_grad:
            trainable_params += parameter.numel()
    return {
        "total": int(total_params),
        "trainable": int(trainable_params),
    }


def main() -> None:
    args = parse_args()
    start_time = time.time()
    set_seed(args.seed)

    train_samples = read_manifest(args.train_manifest)
    val_samples = read_manifest(args.val_manifest)
    test_samples = read_manifest(args.test_manifest) if args.test_manifest else []

    labels = build_label_set(train_samples)
    label2id = {label: idx for idx, label in enumerate(labels)}
    id2label = {idx: label for label, idx in label2id.items()}

    device = to_device(args.device)
    base_model_dir = Path(args.base_model).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    feature_extractor = AutoFeatureExtractor.from_pretrained(base_model_dir)
    model = Wav2Vec2ForSequenceClassification.from_pretrained(
        base_model_dir,
        num_labels=len(labels),
        id2label=id2label,
        label2id=label2id,
        ignore_mismatched_sizes=True,
    ).to(device)

    param_counts = freeze_for_head_adaptation(model, train_projector=args.train_projector)

    train_dataset = AudioEmotionDataset(train_samples, label2id, args.sample_rate, args.max_duration_sec)
    val_dataset = AudioEmotionDataset(val_samples, label2id, args.sample_rate, args.max_duration_sec)
    test_dataset = AudioEmotionDataset(test_samples, label2id, args.sample_rate, args.max_duration_sec) if test_samples else None
    collate_fn = Wav2Vec2BatchCollator(feature_extractor=feature_extractor, sample_rate=args.sample_rate)

    train_loader = DataLoader(
        train_dataset,
        batch_size=args.batch_size,
        shuffle=True,
        num_workers=args.num_workers,
        collate_fn=collate_fn,
    )
    val_loader = DataLoader(
        val_dataset,
        batch_size=args.batch_size,
        shuffle=False,
        num_workers=args.num_workers,
        collate_fn=collate_fn,
    )
    test_loader = (
        DataLoader(
            test_dataset,
            batch_size=args.batch_size,
            shuffle=False,
            num_workers=args.num_workers,
            collate_fn=collate_fn,
        )
        if test_dataset is not None
        else None
    )

    trainable_parameters = [parameter for parameter in model.parameters() if parameter.requires_grad]
    optimizer = AdamW(trainable_parameters, lr=args.learning_rate, weight_decay=args.weight_decay)
    total_steps = max(len(train_loader) * max(args.epochs, 1), 1)
    warmup_steps = int(total_steps * max(args.warmup_ratio, 0.0))
    scheduler = get_linear_schedule_with_warmup(optimizer, warmup_steps, total_steps)

    best_val_macro_f1 = float("-inf")
    best_val_metrics: dict[str, float] | None = None
    best_epoch = 0
    best_state_dict = None
    patience_counter = 0
    history: list[dict[str, float | int]] = []
    global_step = 0

    for epoch in range(1, args.epochs + 1):
        model.train()
        total_loss = 0.0
        total_examples = 0

        for batch in train_loader:
            optimizer.zero_grad(set_to_none=True)
            labels_tensor = batch["labels"].to(device)
            inputs = {"input_values": batch["input_values"].to(device), "labels": labels_tensor}
            attention_mask = batch.get("attention_mask")
            if attention_mask is not None:
                inputs["attention_mask"] = attention_mask.to(device)
            outputs = model(**inputs)
            loss = outputs.loss
            loss.backward()
            optimizer.step()
            scheduler.step()

            batch_size = int(labels_tensor.size(0))
            total_loss += float(loss.detach().cpu().item()) * batch_size
            total_examples += batch_size
            global_step += 1

        train_loss = total_loss / max(total_examples, 1)
        val_metrics, y_true_val, y_pred_val = evaluate_model(model, val_loader, device, len(labels))
        history.append(
            {
                "epoch": epoch,
                "train_loss": train_loss,
                "val_loss": val_metrics["loss"],
                "val_accuracy": val_metrics["accuracy"],
                "val_macro_f1": val_metrics["macro_f1"],
                "val_balanced_accuracy": val_metrics["balanced_accuracy"],
                "learning_rate": float(scheduler.get_last_lr()[0]),
                "global_step": global_step,
            }
        )

        if val_metrics["macro_f1"] > best_val_macro_f1:
            best_val_macro_f1 = float(val_metrics["macro_f1"])
            best_val_metrics = val_metrics
            best_epoch = epoch
            best_state_dict = {key: value.detach().cpu().clone() for key, value in model.state_dict().items()}
            patience_counter = 0
            save_confusion_matrix(output_dir / "val_confusion_matrix_best.csv", labels, y_true_val, y_pred_val)
        else:
            patience_counter += 1
            if patience_counter >= args.patience:
                break

    if best_state_dict is None:
        raise RuntimeError("training did not produce a best checkpoint")

    model.load_state_dict(best_state_dict)
    best_model_dir = output_dir / "best_model"
    best_model_dir.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(best_model_dir)
    feature_extractor.save_pretrained(best_model_dir)

    final_val_metrics, y_true_final_val, y_pred_final_val = evaluate_model(model, val_loader, device, len(labels))
    save_confusion_matrix(output_dir / "val_confusion_matrix_final.csv", labels, y_true_final_val, y_pred_final_val)

    test_metrics = None
    if test_loader is not None:
        test_metrics, y_true_test, y_pred_test = evaluate_model(model, test_loader, device, len(labels))
        save_confusion_matrix(output_dir / "test_confusion_matrix.csv", labels, y_true_test, y_pred_test)

    label_map = {"label2id": label2id, "id2label": id2label}
    (output_dir / "label_map.json").write_text(json.dumps(label_map, ensure_ascii=False, indent=2), encoding="utf-8")

    train_report = {
        "base_model": str(base_model_dir),
        "labels": labels,
        "label2id": label2id,
        "train_samples": len(train_samples),
        "val_samples": len(val_samples),
        "test_samples": len(test_samples),
        "epochs_requested": args.epochs,
        "epochs_ran": len(history),
        "best_epoch": best_epoch,
        "best_val_macro_f1": best_val_macro_f1,
        "best_val_metrics": best_val_metrics,
        "final_val_metrics": final_val_metrics,
        "test_metrics": test_metrics,
        "history": history,
        "runtime_seconds": time.time() - start_time,
        "device": str(device),
        "global_steps": global_step,
        "trainable_modules": ["classifier", "projector"] if args.train_projector else ["classifier"],
        "parameter_counts": param_counts,
    }
    (output_dir / "train_report.json").write_text(json.dumps(train_report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(train_report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

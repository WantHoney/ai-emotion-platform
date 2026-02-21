import argparse
import csv
import json
import subprocess
import sys
from pathlib import Path


MODES = ("audio_only", "text_only", "fusion")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run ablation for late-fusion models.")
    parser.add_argument("--train-features", required=True)
    parser.add_argument("--val-features", required=True)
    parser.add_argument("--test-features", default="")
    parser.add_argument("--output-root", required=True)
    parser.add_argument("--python-bin", default=sys.executable)
    parser.add_argument("--epochs", type=int, default=80)
    parser.add_argument("--batch-size", type=int, default=128)
    parser.add_argument("--learning-rate", type=float, default=2e-3)
    parser.add_argument("--weight-decay", type=float, default=1e-4)
    parser.add_argument("--hidden-size", type=int, default=64)
    parser.add_argument("--dropout", type=float, default=0.2)
    parser.add_argument("--patience", type=int, default=12)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", default="auto", choices=["auto", "cpu", "cuda"])
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output_root = Path(args.output_root).resolve()
    output_root.mkdir(parents=True, exist_ok=True)

    summary_rows = []

    for mode in MODES:
        mode_dir = output_root / mode
        mode_dir.mkdir(parents=True, exist_ok=True)
        cmd = [
            args.python_bin,
            "training/train_late_fusion.py",
            "--train-features",
            args.train_features,
            "--val-features",
            args.val_features,
            "--output-dir",
            str(mode_dir),
            "--mode",
            mode,
            "--epochs",
            str(args.epochs),
            "--batch-size",
            str(args.batch_size),
            "--learning-rate",
            str(args.learning_rate),
            "--weight-decay",
            str(args.weight_decay),
            "--hidden-size",
            str(args.hidden_size),
            "--dropout",
            str(args.dropout),
            "--patience",
            str(args.patience),
            "--seed",
            str(args.seed),
            "--device",
            args.device,
        ]
        if args.test_features:
            cmd.extend(["--test-features", args.test_features])

        print("[run]", " ".join(cmd))
        subprocess.run(cmd, check=True)

        report_path = mode_dir / "train_report.json"
        if not report_path.exists():
            raise RuntimeError(f"missing train_report.json for mode={mode}")
        with report_path.open("r", encoding="utf-8") as f:
            report = json.load(f)
        test_cal = report.get("test_metrics_calibrated") or {}
        val_cal = report.get("val_metrics_calibrated") or {}

        summary_rows.append(
            {
                "mode": mode,
                "best_epoch": report.get("best_epoch"),
                "temperature": report.get("temperature"),
                "val_macro_f1_cal": val_cal.get("macro_f1"),
                "val_accuracy_cal": val_cal.get("accuracy"),
                "test_macro_f1_cal": test_cal.get("macro_f1"),
                "test_accuracy_cal": test_cal.get("accuracy"),
                "test_ece_cal": test_cal.get("ece"),
            }
        )

    summary_json_path = output_root / "ablation_summary.json"
    summary_csv_path = output_root / "ablation_summary.csv"
    with summary_json_path.open("w", encoding="utf-8") as f:
        json.dump({"rows": summary_rows}, f, ensure_ascii=False, indent=2)
    with summary_csv_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(summary_rows[0].keys()))
        writer.writeheader()
        writer.writerows(summary_rows)

    print(json.dumps({"status": "ok", "output_root": str(output_root), "rows": summary_rows}, ensure_ascii=False))


if __name__ == "__main__":
    main()

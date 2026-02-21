import argparse
import json
from pathlib import Path

from huggingface_hub import snapshot_download


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Download text sentiment models for ser-service.")
    parser.add_argument(
        "--output-dir",
        default="text_models",
        help="Directory to store downloaded models.",
    )
    parser.add_argument(
        "--en-model",
        default="cardiffnlp/twitter-roberta-base-sentiment-latest",
        help="English text sentiment model repo id.",
    )
    parser.add_argument(
        "--zh-model",
        default="uer/roberta-base-finetuned-jd-binary-chinese",
        help="Chinese text sentiment model repo id.",
    )
    parser.add_argument(
        "--force-download",
        action="store_true",
        help="Force re-download model files.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    base = Path(args.output_dir).resolve()
    base.mkdir(parents=True, exist_ok=True)

    mapping = {
        "en_roberta_sentiment": args.en_model,
        "zh_roberta_sentiment": args.zh_model,
    }
    summary: dict[str, str] = {}

    for local_name, repo_id in mapping.items():
        target = base / local_name
        target.mkdir(parents=True, exist_ok=True)
        print(f"[download] {repo_id} -> {target}")
        path = snapshot_download(
            repo_id=repo_id,
            local_dir=str(target),
            force_download=args.force_download,
        )
        summary[local_name] = str(Path(path).resolve())
        print(f"[ok] {repo_id}")

    out = {
        "status": "ok",
        "output_dir": str(base),
        "models": summary,
    }
    print(json.dumps(out, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

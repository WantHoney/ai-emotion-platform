import argparse
import csv
import json
from collections import Counter
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Merge multiple manifest directories into one.")
    parser.add_argument(
        "--input-dirs",
        required=True,
        help="Comma-separated manifest directories. Each dir must contain train.csv/val.csv/test.csv.",
    )
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--deduplicate-by-path", action="store_true")
    return parser.parse_args()


def read_manifest(path: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    with path.open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            p = (row.get("path") or "").strip()
            l = (row.get("label") or "").strip()
            if not p or not l:
                continue
            rows.append(row)
    return rows


def write_manifest(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    header = ["path", "label", "source_label", "duration_sec", "source_dataset"]
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        for row in rows:
            writer.writerow(
                [
                    row.get("path", ""),
                    row.get("label", ""),
                    row.get("source_label", ""),
                    row.get("duration_sec", ""),
                    row.get("source_dataset", ""),
                ]
            )


def main() -> None:
    args = parse_args()
    input_dirs = [Path(x.strip()).resolve() for x in args.input_dirs.split(",") if x.strip()]
    if not input_dirs:
        raise ValueError("input-dirs is empty")
    output_dir = Path(args.output_dir).resolve()

    merged: dict[str, list[dict[str, str]]] = {"train": [], "val": [], "test": []}
    counts_by_dataset: dict[str, Counter] = {}
    label_counter: Counter = Counter()
    split_counter: Counter = Counter()

    seen_paths: set[str] = set()
    for input_dir in input_dirs:
        dataset_name = input_dir.name
        counts_by_dataset[dataset_name] = Counter()
        for split in ("train", "val", "test"):
            manifest_path = input_dir / f"{split}.csv"
            if not manifest_path.exists():
                raise FileNotFoundError(f"missing manifest: {manifest_path}")
            rows = read_manifest(manifest_path)
            for row in rows:
                path = row["path"]
                if args.deduplicate_by_path and path in seen_paths:
                    continue
                seen_paths.add(path)
                row = dict(row)
                row["source_dataset"] = dataset_name
                merged[split].append(row)
                counts_by_dataset[dataset_name][split] += 1
                split_counter[split] += 1
                label_counter[row["label"]] += 1

    for split in ("train", "val", "test"):
        write_manifest(output_dir / f"{split}.csv", merged[split])

    summary = {
        "input_dirs": [str(p) for p in input_dirs],
        "output_dir": str(output_dir),
        "counts": {
            "total": int(sum(split_counter.values())),
            "split": dict(split_counter),
            "label": dict(sorted(label_counter.items())),
            "dataset_split": {k: dict(v) for k, v in counts_by_dataset.items()},
        },
    }
    with (output_dir / "summary.json").open("w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

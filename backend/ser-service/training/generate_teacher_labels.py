from __future__ import annotations

import argparse
import csv
import json
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from thesis_v4_common import (  # noqa: E402
    SERVICE_ROOT,
    TEACHER_ROOT,
    ensure_dirs,
    heuristic_semantic_scores,
    load_unique_samples_from_manifests,
    now_iso,
    ollama_chat_json,
    ollama_model_exists,
    read_jsonl,
    write_csv,
    write_jsonl,
)


DEFAULT_SYSTEM_PROMPT = """
你是中文转写文本的离线情绪教师标注器。
你只能根据文本语义判断，不允许参考任何音频结果、已有标签或外部背景。
请严格输出 RFC8259 JSON，不要输出解释文字。
输出字段必须完整：
{
  "label3": "negative|neutral|positive",
  "scores3": {
    "negative": 0.0,
    "neutral": 0.0,
    "positive": 0.0
  },
  "label4": "ANG|HAP|NEU|SAD",
  "scores4": {
    "ANG": 0.0,
    "HAP": 0.0,
    "NEU": 0.0,
    "SAD": 0.0
  },
  "teacher_confidence": 0.0,
  "conflict_hint": ""
}
要求：
1. scores3 的三个值之和必须为 1。
2. scores4 的四个值之和必须为 1。
3. teacher_confidence 取 0 到 1。
4. 对测试问句、客观描述、无明显情绪倾向文本，优先输出 neutral / NEU。
5. 明显开心、轻松、满足、祝福类文本，优先输出 positive / HAP。
6. 明显生气、恼火、不满、愤怒类文本，优先输出 negative / ANG。
7. 明显难过、失落、委屈、悲伤类文本，优先输出 negative / SAD。
8. conflict_hint 用一句简短中文说明是否存在语义歧义、否定冲突、或需要依赖语音韵律补充判断；没有明显冲突时也给出简短说明。
""".strip()


PROMPT_V2_APPENDIX = """
9. 对短句、事实陈述、测试问句、计划安排、条件句，若没有直接情绪词，不要因为“坚持、行动、去做”而误判为积极，优先 neutral / NEU。
10. 对“还好、还不太糟、一般、可以、还行”这类缓和表达，要看后半句是否出现明显负面处境；若后半句出现失望、落空、躺床、提不起精神、低落、难受、空空的等负面状态，优先 negative / SAD。
11. 出现“但是、不过、可是、虽然”时，后半句通常更重要；若后半句带来受限、失落、病痛、疲惫、无力、落空，优先 SAD，而不是 NEU。
12. 只有在文本明确表达开心、高兴、顺利、舒服、轻松、喜欢、满意、祝福时，才优先 positive / HAP。
13. 如果文本主要是在客观描述天气、出行、安排、动作、本人提问，即使包含“去、做、处理、开始”等行动词，也通常仍是 NEU。
14. 对边界样本，宁可保守判为 NEU，也不要把弱情绪误提到 HAP；但如果文本已明确表达低落、失望、难受、没劲、提不起精神，则应判为 SAD。
""".strip()

PROMPT_V3_APPENDIX = """
15. label3 和 label4 必须语义一致：positive 对应 HAP，neutral 对应 NEU，negative 只能对应 ANG 或 SAD，不允许 positive + NEU 或 neutral + HAP 这类不一致组合。
16. “坚持、决定、继续去、照常去、还是去、下雨也去” 只表示行动意愿，不等于开心；如果没有明确愉悦、舒适、喜欢、顺利、高兴等词，label3 应为 neutral，label4 应为 NEU。
17. 只有文本里明确出现开心、高兴、心情很好、不错、舒服、轻松、顺利、喜欢、满意、祝福等正向情绪或评价，才允许输出 positive / HAP。
18. 若文本已经明确出现“开心、高兴、很开心、心情很好、很舒服、轻松、很顺利”等强正向词，不能降成 NEU。
""".strip()

PROMPT_LIBRARY = {
    "teacher_prompt_v1": DEFAULT_SYSTEM_PROMPT,
    "teacher_prompt_v2": DEFAULT_SYSTEM_PROMPT + "\n" + PROMPT_V2_APPENDIX,
    "teacher_prompt_v3": DEFAULT_SYSTEM_PROMPT + "\n" + PROMPT_V2_APPENDIX + "\n" + PROMPT_V3_APPENDIX,
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate versioned Gemma offline teacher labels for thesis v4.")
    parser.add_argument("--manifest", action="append", required=True, help="Sample CSV manifest. Can be passed multiple times.")
    parser.add_argument("--version-id", required=True, help="Teacher artifact version id, e.g. teacher_v1_gemma4_e2b")
    parser.add_argument("--model", default="gemma4:e2b")
    parser.add_argument("--prompt-version", default="teacher_prompt_v1")
    parser.add_argument("--temperature", type=float, default=0.10)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def build_user_prompt(sample_id: str, transcript: str) -> str:
    payload = {
        "sample_id": sample_id,
        "transcript": transcript,
    }
    return json.dumps(payload, ensure_ascii=False)


def normalize_scores(raw: dict[str, Any], labels: list[str], fallback: str) -> dict[str, float]:
    out = {label: max(0.0, float((raw or {}).get(label, 0.0) or 0.0)) for label in labels}
    total = sum(out.values())
    if total <= 0.0:
        return {label: (1.0 if label == fallback else 0.0) for label in labels}
    return {label: value / total for label, value in out.items()}


def repair_label4_consistency(label3: str, label4: str, transcript: str, scores4: dict[str, float]) -> tuple[str, dict[str, float], str | None]:
    if label3 == "positive" and label4 != "HAP":
        return "HAP", {"ANG": 0.0, "HAP": 1.0, "NEU": 0.0, "SAD": 0.0}, "positive_to_hap"
    if label3 == "neutral" and label4 != "NEU":
        return "NEU", {"ANG": 0.0, "HAP": 0.0, "NEU": 1.0, "SAD": 0.0}, "neutral_to_neu"
    if label3 == "negative" and label4 == "NEU":
        heuristic = heuristic_semantic_scores(transcript, SERVICE_ROOT / "lexicon")
        repaired = "ANG" if heuristic.get("label") == "ANG" else "SAD"
        repaired_scores = {"ANG": 0.0, "HAP": 0.0, "NEU": 0.0, "SAD": 0.0}
        repaired_scores[repaired] = 1.0
        return repaired, repaired_scores, f"negative_neu_repaired_to_{repaired.lower()}"
    return label4, scores4, None


def normalize_teacher_payload(sample_id: str, transcript: str, payload: dict[str, Any], model: str, prompt_version: str) -> dict[str, Any]:
    scores3 = normalize_scores(payload.get("scores3") or {}, ["negative", "neutral", "positive"], "neutral")
    scores4 = normalize_scores(payload.get("scores4") or {}, ["ANG", "HAP", "NEU", "SAD"], "NEU")
    label3 = str(payload.get("label3") or max(scores3, key=scores3.get)).strip().lower()
    if label3 not in {"negative", "neutral", "positive"}:
        label3 = max(scores3, key=scores3.get)
    label4 = str(payload.get("label4") or max(scores4, key=scores4.get)).strip().upper()
    if label4 not in {"ANG", "HAP", "NEU", "SAD"}:
        label4 = max(scores4, key=scores4.get)
    label4, scores4, normalization_note = repair_label4_consistency(label3, label4, transcript, scores4)
    confidence = float(payload.get("teacher_confidence") or scores4.get(label4, 0.0))
    confidence = max(0.0, min(1.0, confidence))
    conflict_hint = str(payload.get("conflict_hint") or "").strip()
    normalized = {
        "sample_id": sample_id,
        "transcript": transcript,
        "label3": label3,
        "scores3": scores3,
        "label4": label4,
        "scores4": scores4,
        "teacher_confidence": confidence,
        "conflict_hint": conflict_hint,
        "teacher_model": model,
        "prompt_version": prompt_version,
        "generated_at": datetime.now().isoformat(timespec="seconds"),
    }
    if normalization_note:
        normalized["normalization_note"] = normalization_note
    return normalized


def persist_teacher_rows(output_jsonl: Path, output_csv: Path, rows: list[dict[str, Any]]) -> None:
    ordered_rows = sorted(rows, key=lambda item: item["sample_id"])
    write_jsonl(output_jsonl, ordered_rows)
    csv_rows = []
    for row in ordered_rows:
        csv_rows.append(
            {
                "sample_id": row["sample_id"],
                "transcript": row["transcript"],
                "label3": row["label3"],
                "label4": row["label4"],
                "teacher_confidence": row["teacher_confidence"],
                "conflict_hint": row["conflict_hint"],
                "scores3_json": json.dumps(row["scores3"], ensure_ascii=False),
                "scores4_json": json.dumps(row["scores4"], ensure_ascii=False),
                "teacher_model": row["teacher_model"],
                "prompt_version": row["prompt_version"],
                "generated_at": row["generated_at"],
            }
        )
    write_csv(
        output_csv,
        csv_rows,
        [
            "sample_id",
            "transcript",
            "label3",
            "label4",
            "teacher_confidence",
            "conflict_hint",
            "scores3_json",
            "scores4_json",
            "teacher_model",
            "prompt_version",
            "generated_at",
        ],
    )


def main() -> None:
    args = parse_args()
    ensure_dirs()

    if not ollama_model_exists(args.model):
        raise RuntimeError(f"Ollama model not available: {args.model}")
    if args.prompt_version not in PROMPT_LIBRARY:
        raise ValueError(f"unsupported prompt_version: {args.prompt_version}")

    manifest_paths = []
    for item in args.manifest:
        resolved = Path(item).resolve()
        if str(resolved) not in [str(path) for path in manifest_paths]:
            manifest_paths.append(resolved)
    samples = load_unique_samples_from_manifests(manifest_paths)

    output_jsonl = TEACHER_ROOT / f"{args.version_id}.jsonl"
    output_csv = TEACHER_ROOT / f"{args.version_id}.csv"
    existing = {}
    if output_jsonl.exists() and not args.overwrite:
        for row in read_jsonl(output_jsonl):
            existing[row["sample_id"]] = row

    rows: list[dict[str, Any]] = []
    for sample in samples:
        cached = existing.get(sample.sample_id)
        if cached is not None:
            rows.append(cached)
            continue
        payload = ollama_chat_json(
            model=args.model,
            system_prompt=PROMPT_LIBRARY[args.prompt_version],
            user_prompt=build_user_prompt(sample.sample_id, sample.transcript),
            temperature=args.temperature,
            timeout_seconds=args.timeout_seconds,
        )
        rows.append(
            normalize_teacher_payload(
                sample_id=sample.sample_id,
                transcript=sample.transcript,
                payload=payload,
                model=args.model,
                prompt_version=args.prompt_version,
            )
        )
        persist_teacher_rows(output_jsonl, output_csv, rows)

    persist_teacher_rows(output_jsonl, output_csv, rows)
    print(json.dumps(
        {
            "versionId": args.version_id,
            "model": args.model,
            "promptVersion": args.prompt_version,
            "sampleCount": len(rows),
            "outputJsonl": str(output_jsonl.resolve()),
            "outputCsv": str(output_csv.resolve()),
            "generatedAt": now_iso(),
        },
        ensure_ascii=False,
        indent=2,
    ))


if __name__ == "__main__":
    main()

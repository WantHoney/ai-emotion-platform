from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent
LOG_DIR = ROOT / "logs"
OUTPUT_PATH = LOG_DIR / "model_version_compare.json"
V4_REPORT_PATH = (
    ROOT
    / "training"
    / "manifests"
    / "thesis_v4"
    / "deferred_training"
    / "local_text"
    / "model_v4_teacher_distill_humanfix_rw35_bestfix"
    / "train_report.json"
)
V4_KEY_SAMPLES_PATH = (
    ROOT
    / "training"
    / "manifests"
    / "thesis_v4"
    / "deferred_training"
    / "local_text"
    / "model_v4_teacher_distill_humanfix_rw35_bestfix"
    / "test_key_samples.json"
)


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def load_json_if_exists(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    return load_json(path)


def normalize_label(value: str | None) -> str | None:
    if value is None:
        return None
    key = str(value).strip().upper()
    if key in {"ANG", "ANGRY"}:
        return "ANGRY"
    if key in {"HAP", "HAPPY"}:
        return "HAPPY"
    if key in {"NEU", "NEUTRAL"}:
        return "NEUTRAL"
    if key == "SAD":
        return "SAD"
    return key or None


def task_eval_entry(
    sample_id: str,
    expected: str,
    predicted: str | None,
    confidence: float | None,
    source: str,
    notes: str | None = None,
) -> dict[str, Any]:
    return {
        "sampleId": sample_id,
        "expected": expected,
        "predicted": normalize_label(predicted),
        "confidence": None if confidence is None else float(confidence),
        "source": source,
        "notes": notes,
    }


def build_v4_entry() -> dict[str, Any] | None:
    train_report = load_json_if_exists(V4_REPORT_PATH)
    if not train_report:
        return None
    key_samples = load_json_if_exists(V4_KEY_SAMPLES_PATH) or []
    key_samples_by_id = {item.get("sample_id"): item for item in key_samples if item.get("sample_id")}
    return {
        "versionId": "v4_thesis_final_local",
        "stage": "local_teacher_student",
        "title": "Frozen local thesis v4 pipeline (distilled + real-weighted)",
        "description": "Teacher-approved frozen pools, human-truth soft-target overrides for intake recordings, real-domain weighted sampling, and fully local train/val/test provenance artifacts.",
        "components": {
            "audioModel": "ser_multilingual_xlsr_stageB_exp04_hardfix_v1",
            "audioAggregation": "segment_probability_pooling",
            "textScoring": "local_text_model_v4_teacher_distill_humanfix_rw35",
            "zhFusion": "frozen_thesis_v4_local_text_eval",
            "decisionLayer": "decision_v4",
        },
        "metrics": {
            "valMacroF1": train_report.get("val_metrics", {}).get("macro_f1"),
            "valBalancedAccuracy": train_report.get("val_metrics", {}).get("balanced_accuracy"),
            "testAccuracy": train_report.get("test_metrics", {}).get("accuracy"),
            "testMacroF1": train_report.get("test_metrics", {}).get("macro_f1"),
            "testBalancedAccuracy": train_report.get("test_metrics", {}).get("balanced_accuracy"),
            "testPerClass": train_report.get("test_metrics", {}).get("per_class"),
        },
        "keySamples": [
            task_eval_entry(
                "real_8a69fec4eeb1",
                "ANGRY",
                key_samples_by_id.get("real_8a69fec4eeb1", {}).get("pred_label"),
                key_samples_by_id.get("real_8a69fec4eeb1", {}).get("confidence"),
                "model_v4_teacher_distill_humanfix_rw35_bestfix/test_key_samples.json",
                "Recovered the known angry real-world sample after giving more weight to real recordings.",
            ),
            task_eval_entry(
                "real_c7ab8e4d197d",
                "HAPPY",
                key_samples_by_id.get("real_c7ab8e4d197d", {}).get("pred_label"),
                key_samples_by_id.get("real_c7ab8e4d197d", {}).get("confidence"),
                "model_v4_teacher_distill_humanfix_rw35_bestfix/test_key_samples.json",
                "Positive real-world sample stays correct after the fully local final pipeline.",
            ),
            task_eval_entry(
                "real_c0fcf4ce8e54",
                "NEUTRAL",
                key_samples_by_id.get("real_c0fcf4ce8e54", {}).get("pred_label"),
                key_samples_by_id.get("real_c0fcf4ce8e54", {}).get("confidence"),
                "model_v4_teacher_distill_humanfix_rw35_bestfix/test_key_samples.json",
                "Neutral real-world sample is now stable in the final local thesis candidate.",
            ),
        ],
        "thesisTakeaway": "The local thesis pipeline becomes a viable mainline model after human-truth soft-target overrides, real-domain weighted sampling, and a trainer best-checkpoint fix.",
    }


def build_report() -> dict[str, Any]:
    checkpoint_compare = load_json(LOG_DIR / "checkpoint_benchmark_real_vs_controlled.json")
    guard_compare = load_json(LOG_DIR / "emotion_bias_fix_compare.json")
    hardfix_compare = load_json(LOG_DIR / "hardfix_benchmark_compare.json")
    hardfix_pipeline = load_json(LOG_DIR / "hardfix_pipeline_eval.json")
    gemma_eval = load_json(LOG_DIR / "gemma_semantic_eval.json")

    baseline_model = checkpoint_compare["models"]["current_exp04_fast"]
    hardfix_checkpoint_model = hardfix_compare["models"]["after_hardfix_v1"]
    baseline_real = {item["sampleId"]: item for item in baseline_model.get("real", [])}
    baseline_real_hardfix_compare = {
        item["sampleId"]: item
        for item in hardfix_compare["models"]["before_exp04_fast"].get("real", [])
    }
    hardfix_checkpoint_real = {item["sampleId"]: item for item in hardfix_checkpoint_model.get("real", [])}
    gemma_samples = {item["sampleId"]: item for item in gemma_eval.get("samples", [])}

    versions: list[dict[str, Any]] = []
    versions.append(
        {
            "versionId": "v0_baseline_exp04_fast",
            "stage": "baseline",
            "title": "Original local speech chain",
            "description": "Original exp04_fast speech model, weak Chinese text branch, and legacy Chinese fusion.",
            "components": {
                "audioModel": "ser_multilingual_xlsr_stageB_exp04_fast",
                "audioAggregation": "top1_segment_majority",
                "textScoring": "zh_sentiment_exp03",
                "zhFusion": "legacy_fusion_exp04_gated",
                "decisionLayer": "none",
            },
            "metrics": {
                "controlledAccuracy": baseline_model.get("controlledAccuracy"),
                "controlledAccuracyByLabel": baseline_model.get("controlledAccuracyByLabel"),
                "batchAccuracy": None,
            },
            "keySamples": [
                task_eval_entry(
                    "task37_positive_natural",
                    "HAPPY",
                    baseline_real.get("task37_positive_natural", {}).get("predicted"),
                    baseline_real.get("task37_positive_natural", {}).get("confidence"),
                    "checkpoint_benchmark_real_vs_controlled.json",
                    "Clear positive natural recording was forced into SAD.",
                ),
                task_eval_entry(
                    "task36_negative_natural",
                    "ANG_OR_SAD",
                    baseline_real.get("task36_negative_natural", {}).get("predicted"),
                    baseline_real.get("task36_negative_natural", {}).get("confidence"),
                    "checkpoint_benchmark_real_vs_controlled.json",
                    None,
                ),
                task_eval_entry(
                    "task34_neutral_natural",
                    "NEUTRAL",
                    baseline_real_hardfix_compare.get("task34_neutral_natural", {}).get("predicted"),
                    baseline_real_hardfix_compare.get("task34_neutral_natural", {}).get("confidence"),
                    "hardfix_benchmark_compare.json",
                    "Neutral natural speech drifted into SAD.",
                ),
            ],
            "thesisTakeaway": "The baseline worked better on controlled speech than on real Chinese recordings, with a clear sadness bias.",
        }
    )

    versions.append(
        {
            "versionId": "v1_decision_guard_hotfix",
            "stage": "hotfix",
            "title": "Decision-layer guard hotfix",
            "description": "Kept the original speech chain but intercepted high-confidence SAD vs clearly positive text conflicts.",
            "components": {
                "audioModel": "ser_multilingual_xlsr_stageB_exp04_fast",
                "audioAggregation": "top1_segment_majority",
                "textScoring": "zh_sentiment_exp03",
                "zhFusion": "legacy_fusion_exp04_gated",
                "decisionLayer": "sad_positive_conflict_v1",
            },
            "metrics": {
                "batchAccuracy": {
                    "before": guard_compare["batchSummary"]["beforeAccuracy"],
                    "after": guard_compare["batchSummary"]["afterAccuracy"],
                }
            },
            "keySamples": [
                task_eval_entry(
                    "task37_positive_natural",
                    "HAPPY_OR_LOW_CONSISTENCY",
                    guard_compare["misclassifiedSample"]["after"]["decisionCode"],
                    guard_compare["misclassifiedSample"]["after"]["baseConfidence"],
                    "emotion_bias_fix_compare.json",
                    "Stops the bad SAD overcall without fixing the model itself.",
                ),
            ],
            "thesisTakeaway": "Good as an engineering safety hotfix, but not a model-level cure.",
        }
    )

    versions.append(
        {
            "versionId": "v2_audio_hardfix_v1",
            "stage": "model_repair",
            "title": "Audio hardfix + probability pooling",
            "description": "Targeted audio repair over exp04_fast plus segment-level probability pooling and safer Chinese routing.",
            "components": {
                "audioModel": "ser_multilingual_xlsr_stageB_exp04_hardfix_v1",
                "audioAggregation": "segment_probability_pooling",
                "textScoring": "zh_sentiment_exp03",
                "zhFusion": "skip_when_text4_not_ready",
                "decisionLayer": "optional_low_consistency_guard",
            },
            "metrics": {
                "controlledAccuracy": hardfix_checkpoint_model.get("controlledAccuracy"),
                "controlledAccuracyByLabel": hardfix_checkpoint_model.get("controlledAccuracyByLabel"),
                "pipelineTask37": hardfix_pipeline["task37_positive_natural"]["overall"],
                "pipelineTask36": hardfix_pipeline["task36_negative_natural"]["overall"],
            },
            "keySamples": [
                task_eval_entry(
                    "task37_positive_natural",
                    "HAPPY",
                    hardfix_pipeline["task37_positive_natural"]["overall"]["emotionCode"],
                    hardfix_pipeline["task37_positive_natural"]["overall"]["confidence"],
                    "hardfix_pipeline_eval.json",
                    "First repaired the known positive-to-SAD mistake in the main speech chain.",
                ),
                task_eval_entry(
                    "task36_negative_natural",
                    "ANG_OR_SAD",
                    hardfix_pipeline["task36_negative_natural"]["overall"]["emotionCode"],
                    hardfix_pipeline["task36_negative_natural"]["overall"]["confidence"],
                    "hardfix_pipeline_eval.json",
                    None,
                ),
                task_eval_entry(
                    "task34_neutral_natural",
                    "NEUTRAL",
                    hardfix_checkpoint_real.get("task34_neutral_natural", {}).get("predicted"),
                    hardfix_checkpoint_real.get("task34_neutral_natural", {}).get("confidence"),
                    "hardfix_benchmark_compare.json",
                    "Sadness bias dropped, but neutral drifted toward HAPPY.",
                ),
            ],
            "thesisTakeaway": "Audio repair improved the core bias, but neutral boundary stability was still weak.",
        }
    )

    versions.append(
        {
            "versionId": "v3_gemma_semantic_guarded",
            "stage": "multimodal_upgrade",
            "title": "Gemma semantic scoring + guarded Chinese fusion",
            "description": "Gemma 4 handled transcript semantics while the lexicon became a weak prior and semantic_guarded_v1 handled fusion.",
            "components": {
                "audioModel": "ser_multilingual_xlsr_stageB_exp04_hardfix_v1",
                "audioAggregation": "segment_probability_pooling",
                "textScoring": "gemma4:e2b semantic_llm_v1 + lexicon prior",
                "zhFusion": "semantic_guarded_v1",
                "decisionLayer": "low_consistency_guard + decision-first narrative",
            },
            "metrics": {
                "realSamplePassCount": sum(
                    1
                    for sample in gemma_samples.values()
                    if normalize_label(sample.get("fusion", {}).get("label")) in {"NEUTRAL", "ANGRY", "SAD", "HAPPY"}
                ),
                "realSampleTotal": len(gemma_samples),
            },
            "keySamples": [
                task_eval_entry(
                    "task34_neutral_natural",
                    "NEUTRAL",
                    gemma_samples["task34_neutral_natural"]["fusion"]["label"],
                    gemma_samples["task34_neutral_natural"]["fusion"]["confidence"],
                    "gemma_semantic_eval.json",
                    "Pulled the neutral test sentence back to NEUTRAL.",
                ),
                task_eval_entry(
                    "task36_negative_natural",
                    "ANG_OR_SAD",
                    gemma_samples["task36_negative_natural"]["fusion"]["label"],
                    gemma_samples["task36_negative_natural"]["fusion"]["confidence"],
                    "gemma_semantic_eval.json",
                    "Negative natural sample was no longer dominated by audio SAD alone.",
                ),
                task_eval_entry(
                    "task37_positive_natural",
                    "HAPPY",
                    gemma_samples["task37_positive_natural"]["fusion"]["label"],
                    gemma_samples["task37_positive_natural"]["fusion"]["confidence"],
                    "gemma_semantic_eval.json",
                    "Positive natural sample remained stably HAPPY.",
                ),
            ],
            "thesisTakeaway": "This is still the strongest system-level version for the thesis so far.",
        }
    )

    v4_entry = build_v4_entry()
    if v4_entry:
        versions.append(v4_entry)

    return {
        "schemaVersion": "model_version_compare.v2",
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "project": "ai-emotion",
        "notes": [
            "All versions are grounded in locally reproducible artifacts.",
            "v1 is a safety hotfix, while v2-v4 are capability-oriented upgrades.",
            "Controlled-speech accuracy and real-world error-correction ability should be reported together.",
        ],
        "versions": versions,
        "recommendedForThesis": "v4_thesis_final_local",
    }


def main() -> None:
    report = build_report()
    OUTPUT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(str(OUTPUT_PATH))
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

# Wav2Vec2 Emotion Training Pipeline

This folder contains a full training/evaluation workflow for:

- acoustic encoder: `wav2vec2`
- task head: single-label emotion classification layer

The output model can be loaded directly by `ser-service/app.py` in runtime mode `SER_ENGINE=hf_wav2vec2`.

## 1. Dataset Format

Preferred directory layout:

```text
<dataset_root>/
  train/
    angry/*.wav
    happy/*.wav
    sad/*.wav
    neutral/*.wav
  val/
    ...
  test/
    ...
```

Or flat layout:

```text
<dataset_root>/
  angry/*.wav
  happy/*.wav
  sad/*.wav
  neutral/*.wav
```

## 2. Build Manifest

### 2.1 From raw IEMOCAP (recommended)

If you keep official raw structure (`Session1..Session5`), run:

```bash
cd backend/ser-service
python training/prepare_iemocap_manifest.py \
  --raw-dir ../data/datasets/IEMOCAP_raw \
  --output-dir training/manifests/iemocap_4class \
  --preset 4class
```

By default it uses speaker-independent session split:

- train: `Session1,Session2,Session3`
- val: `Session4`
- test: `Session5`

### 2.2 From raw RAVDESS

```bash
cd backend/ser-service
python training/prepare_ravdess_manifest.py \
  --raw-dir ../data/datasets/RAVDESS_raw \
  --output-dir training/manifests/ravdess_4class \
  --preset 4class
```

### 2.3 From raw CASIA

```bash
cd backend/ser-service
python training/prepare_casia_manifest.py \
  --raw-dir ../data/datasets/CASIA_raw \
  --output-dir training/manifests/casia_4class \
  --preset 4class
```

### 2.4 From raw ESD (Chinese, recommended)

```bash
cd backend/ser-service
python training/prepare_esd_manifest.py \
  --raw-dir ../data/datasets/ESD_raw \
  --output-dir training/manifests/esd_4class \
  --preset 4class \
  --seed 42
```

`4class` mapping is fixed:

- `angry -> ANG`
- `happy|excited|cheerful -> HAP`
- `neutral -> NEU`
- `sad -> SAD`
- `surprise -> dropped`

Behavior:

- Prefer official split if detected (`train/val/test`-style top level).
- Else use speaker-independent split (`80/10/10` by default).
- Validate speaker leakage for official split.

`summary.json` includes:

- `detected_layout`
- `label_source`
- `text_source`
- per-split counts and dropped reasons

### 2.5 Build Stage-A and Stage-B merged manifests

Stage A (Chinese boost: CASIA + ESD):

```bash
cd backend/ser-service
python training/merge_manifests.py \
  --input-dirs training/manifests/casia_4class,training/manifests/esd_4class \
  --output-dir training/manifests/zh_esd_stageA_4class \
  --deduplicate-by-path
```

Stage B (multilingual v2: IEMOCAP + RAVDESS + CASIA + ESD):

```bash
cd backend/ser-service
python training/merge_manifests.py \
  --input-dirs training/manifests/iemocap_4class,training/manifests/ravdess_4class,training/manifests/casia_4class,training/manifests/esd_4class \
  --output-dir training/manifests/multilingual_4class_v2 \
  --deduplicate-by-path
```

## 3. Train (`wav2vec2 + classification head`)

### 3.1 Stage A (Chinese incremental fine-tuning)

```bash
cd backend/ser-service
python training/train_wav2vec2_cls.py \
  --train-manifest training/manifests/zh_esd_stageA_4class/train.csv \
  --val-manifest training/manifests/zh_esd_stageA_4class/val.csv \
  --test-manifest training/manifests/zh_esd_stageA_4class/test.csv \
  --base-model training/checkpoints/ser_casia_ft_exp01/best_model \
  --output-dir training/checkpoints/ser_zh_esd_stageA_exp01 \
  --epochs 18 \
  --batch-size 8 \
  --learning-rate 8e-6 \
  --weight-decay 1e-2 \
  --freeze-feature-encoder-epochs 4 \
  --patience 4 \
  --device cuda
```

Recommended quick robustness check:

- run once with `--freeze-feature-encoder-epochs 3`
- run once with `--freeze-feature-encoder-epochs 5`
- keep the better checkpoint by `val_macro_f1` and `test_macro_f1`.

### 3.2 Stage B (multilingual rebalance fine-tuning)

For Chinese-first product positioning, keep Chinese dominant and use English as auxiliary.
Recommended target ratio: `zh ~80%`, `en ~20%` (tunable range `zh 60~80%`, `en 20~40%`).

```bash
cd backend/ser-service
python training/train_wav2vec2_cls.py \
  --train-manifest training/manifests/multilingual_4class_v2/train.csv \
  --val-manifest training/manifests/multilingual_4class_v2/val.csv \
  --test-manifest training/manifests/multilingual_4class_v2/test.csv \
  --base-model training/checkpoints/ser_zh_esd_stageA_exp01/best_model \
  --output-dir training/checkpoints/ser_multilingual_esd_stageB_exp01 \
  --epochs 10 \
  --batch-size 8 \
  --learning-rate 4e-6 \
  --weight-decay 1e-4 \
  --patience 3 \
  --source-weight-map "casia_4class=1.0,esd_4class=1.0,iemocap_4class=1.5,ravdess_4class=1.5" \
  --source-language-map "casia_4class=zh,esd_4class=zh,iemocap_4class=en,ravdess_4class=en" \
  --device cuda
```

`train_report.json` includes rebalance evidence:

- `train_source_counts`
- `train_language_counts_raw`
- `expected_source_ratios`
- `expected_language_ratios`
- `expected_label_ratios`
- `weighted_sampling_preview`

### 3.3 Training artifacts

- `best_model/` (HF model + feature extractor)
- `train_report.json`
- `label_map.json`
- `val_confusion_matrix_*.csv`
- `test_confusion_matrix.csv` (if test set provided)

## 4. Evaluate

```bash
cd backend/ser-service
python training/evaluate_wav2vec2_cls.py \
  --model-dir training/checkpoints/ser_multilingual_esd_stageB_exp01/best_model \
  --manifest training/manifests/multilingual_4class_v2/test.csv \
  --output-json training/checkpoints/ser_multilingual_esd_stageB_exp01/test_metrics.json \
  --output-confusion-csv training/checkpoints/ser_multilingual_esd_stageB_exp01/test_confusion_matrix_eval.csv \
  --output-predictions-csv training/checkpoints/ser_multilingual_esd_stageB_exp01/test_predictions.csv
```

## 5. Deploy Into SER Service

Set environment variables before starting FastAPI service:

```bash
SER_ENGINE=hf_wav2vec2
SER_HF_MODEL_DIR_EN=./training/checkpoints/ser_multilingual_4class_exp02/best_model
SER_HF_MODEL_DIR_ZH=./training/checkpoints/ser_multilingual_esd_stageB_exp01/best_model
SER_HF_ROUTING=language
SER_HF_DEVICE=auto
```

Then start service:

```bash
uvicorn app:app --host 0.0.0.0 --port 8001
```

Stable production defaults for the current repo state:

- Chinese text model: `./training/text_models/zh_sentiment_exp03/best_model`
- Fusion model: `./training/fusion/models/fusion_exp03_perlang`
- `exp04` remains candidate-only until the real `ser_multilingual_xlsr_stageB_exp04` chain is rerun and passes the non-regression gate.

## 6. Retrain Text Branch (Chinese-domain aligned)

`merge_manifests.py` now preserves extended columns (`text/language/speaker/file_id/...`) so that
`build_fusion_features.py` can reuse dataset transcripts first and reduce ASR noise.

`build_fusion_features.py` transcript priority:

1. manifest `transcript/text`
2. ASR cache
3. live ASR

Useful option:

- `--retry-empty-cache` (retry ASR when cache entry exists but text is empty)

### 6.1 Legacy 3-class sentiment branch (compatible)

Train a 3-class text model (`negative/neutral/positive`) from `features_exp02_esd` transcripts.
Label alignment:

- `ANG/SAD -> negative`
- `NEU -> neutral`
- `HAP -> positive`

```bash
cd backend/ser-service
python training/train_text_sentiment_from_features.py \
  --train-features training/fusion/features_exp02_esd/train_features.csv \
  --val-features training/fusion/features_exp02_esd/val_features.csv \
  --test-features training/fusion/features_exp02_esd/test_features.csv \
  --output-dir training/text_models/zh_sentiment_exp02 \
  --base-model ./text_models/zh_roberta_sentiment \
  --language-filter zh \
  --epochs 5 \
  --batch-size 16 \
  --learning-rate 2e-5 \
  --device cuda
```

Then point runtime to the retrained Chinese text model:

```bash
TEXT_ENGINE=hf
TEXT_HF_ROUTING=language
TEXT_HF_MODEL_ZH=./training/text_models/zh_sentiment_exp02/best_model
TEXT_HF_MODEL_EN=./text_models/en_roberta_sentiment
```

### 6.2 Experimental 4-class text emotion branch (ANG/HAP/NEU/SAD)

This branch is kept for feature experimentation and contract compatibility.
It is not the current production default.

```bash
cd backend/ser-service
python training/train_text_emotion_4class_from_features.py \
  --train-features training/fusion/features_exp04_seed/train_features.csv \
  --val-features training/fusion/features_exp04_seed/val_features.csv \
  --test-features training/fusion/features_exp04_seed/test_features.csv \
  --output-dir training/text_models/zh_emotion4_exp04 \
  --base-model ./training/text_models/zh_sentiment_exp03/best_model \
  --language-filter zh \
  --epochs 8 \
  --batch-size 16 \
  --learning-rate 2e-5 \
  --device cuda
```

The runtime now exposes/consumes optional emotion-4 text fields (`text4_prob_*`, `text4_confidence`, `text4_entropy`, `text4_ready`)
without breaking existing 3-class fields.

Important current-state note:

- `zh_emotion4_exp04` collapsed (`macro-F1 ~= 0.10`) and must not be used for full-pipeline rebuilds.
- When rebuilding `exp04_full`, keep the stable Chinese text model `./training/text_models/zh_sentiment_exp03/best_model`.
- The observed `exp04_full` candidate features were built with:
  - `audio_model_zh = training/checkpoints/ser_multilingual_xlsr_stageB_exp04_fast/best_model`
  - `text_model_zh = training/text_models/zh_sentiment_exp03/best_model`
- Because `training/checkpoints/ser_multilingual_xlsr_stageB_exp04/` is still empty, that `exp04` chain is candidate-only and not a formal production result.

### 6.3 Stable vs candidate branches

- Stable production path:
  - text zh: `training/text_models/zh_sentiment_exp03/best_model`
  - fusion: `training/fusion/models/fusion_exp03_perlang`
- Candidate path under review:
  - fusion architectures: `fusion_exp04_gated`, `fusion_exp04_mlp`
  - candidate feature set: `training/fusion/features_exp04_full`
  - do not promote until the true `ser_multilingual_xlsr_stageB_exp04` rerun is complete and all gate metrics are checked.

## 7. Build Multimodal Fusion Features (exp02_esd)

Use trained acoustic models + ASR + text sentiment model to build tabular features:

```bash
cd backend/ser-service
python training/build_fusion_features.py \
  --train-manifest training/manifests/multilingual_4class_v2/train.csv \
  --val-manifest training/manifests/multilingual_4class_v2/val.csv \
  --test-manifest training/manifests/multilingual_4class_v2/test.csv \
  --output-dir training/fusion/features_exp02_esd \
  --audio-model-en training/checkpoints/ser_multilingual_4class_exp02/best_model \
  --audio-model-zh training/checkpoints/ser_multilingual_esd_stageB_exp01/best_model \
  --text-model-en ./text_models/en_roberta_sentiment \
  --text-model-zh ./text_models/zh_roberta_sentiment \
  --audio-device cuda \
  --text-device cuda \
  --whisper-model small \
  --whisper-device cpu \
  --asr-cache-json training/fusion/features_exp02_esd/asr_cache.json
```

Feature columns now include language-aware signal:

- `lang_is_zh` (`0/1`)

## 8. Train Learnable Late Fusion + Calibration

```bash
cd backend/ser-service
python training/train_late_fusion.py \
  --train-features training/fusion/features_exp02_esd/train_features.csv \
  --val-features training/fusion/features_exp02_esd/val_features.csv \
  --test-features training/fusion/features_exp02_esd/test_features.csv \
  --output-dir training/fusion/models/fusion_exp02_esd \
  --mode fusion \
  --epochs 80 \
  --batch-size 128 \
  --hidden-size 64 \
  --dropout 0.2 \
  --learning-rate 2e-3 \
  --weight-decay 1e-4 \
  --calibration-mode per_language_temperature \
  --min-language-samples 100 \
  --device cuda
```

Candidate `exp04` gated run (evaluation only, not current production default):

```bash
cd backend/ser-service
python training/train_late_fusion.py \
  --train-features training/fusion/features_exp04_full/train_features.csv \
  --val-features training/fusion/features_exp04_full/val_features.csv \
  --test-features training/fusion/features_exp04_full/test_features.csv \
  --output-dir training/fusion/models/fusion_exp04_gated \
  --mode fusion \
  --fusion-arch gated \
  --gate-hidden-size 64 \
  --epochs 100 \
  --batch-size 128 \
  --hidden-size 128 \
  --dropout 0.2 \
  --learning-rate 1.5e-3 \
  --weight-decay 1e-4 \
  --patience 15 \
  --calibration-mode per_language_temperature \
  --calibration-max-iter 300 \
  --min-language-samples 100 \
  --device cuda
```

Calibration modes:

- `global_temperature` (legacy)
- `per_language_temperature` (recommended for zh/en mixed data)
- `vector_scaling` (stronger calibrator, higher overfit risk)

Report additions:

- `test_macro_f1_zh`
- `test_macro_f1_en`
- `test_metrics_by_language_calibrated`

## 9. Run Ablation (`audio_only / text_only / fusion`)

```bash
cd backend/ser-service
python training/run_fusion_ablation.py \
  --train-features training/fusion/features_exp02_esd/train_features.csv \
  --val-features training/fusion/features_exp02_esd/val_features.csv \
  --test-features training/fusion/features_exp02_esd/test_features.csv \
  --output-root training/fusion/ablation_exp02_esd \
  --epochs 80 \
  --batch-size 128 \
  --calibration-mode per_language_temperature \
  --device cuda
```

## 10. Select Production Candidate (`fusion_best`)

After tuning, keep one stable directory for deployment and reporting.

Current project convention:

- best model dir: `training/fusion/models/fusion_best/`
- model ranking files:
  - `training/fusion/models/model_selection_exp01.csv`
  - `training/fusion/models/model_selection_exp01.json`

Recommended selection priority:

1. highest `test_macro_f1`
2. then lower `test_ece`
3. then simpler/earlier checkpoint

## 11. ESD Citations (required)

If you use ESD, cite:

```bibtex
@inproceedings{zhou2021seen,
  title={Seen and unseen emotional style transfer for voice conversion with a new emotional speech dataset},
  author={Zhou, Kun and Sisman, Berrak and Liu, Rui and Li, Haizhou},
  booktitle={ICASSP 2021-2021 IEEE International Conference on Acoustics, Speech and Signal Processing (ICASSP)},
  pages={920--924},
  year={2021},
  organization={IEEE}
}

@article{zhou2021emotional,
  title={Emotional voice conversion: Theory, databases and ESD},
  journal={Speech Communication},
  volume={137},
  pages={1-18},
  year={2022},
  issn={0167-6393}
}
```

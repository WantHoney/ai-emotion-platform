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

### 2.2 From already organized label folders

```bash
cd backend/ser-service
python training/build_manifest.py \
  --input-dir <dataset_root> \
  --output-dir training/manifests/demo \
  --mode auto \
  --use-iemocap-map \
  --allowed-labels ANGRY,HAPPY,SAD,NEUTRAL
```

Output:

- `train.csv`
- `val.csv`
- `test.csv`
- `summary.json`

Each CSV row:

- `path,label,source_label,duration_sec`

## 3. Train (`wav2vec2 + classification head`)

```bash
cd backend/ser-service
python training/train_wav2vec2_cls.py \
  --train-manifest training/manifests/iemocap_4class/train.csv \
  --val-manifest training/manifests/iemocap_4class/val.csv \
  --test-manifest training/manifests/iemocap_4class/test.csv \
  --base-model facebook/wav2vec2-base \
  --output-dir wav2vec2_finetuned/exp01 \
  --epochs 12 \
  --batch-size 4 \
  --learning-rate 1e-5 \
  --freeze-feature-encoder
```

Training artifacts:

- `best_model/` (HF model + feature extractor)
- `train_report.json`
- `label_map.json`
- `val_confusion_matrix_*.csv`
- `test_confusion_matrix.csv` (if test set provided)

## 4. Evaluate

```bash
cd backend/ser-service
python training/evaluate_wav2vec2_cls.py \
  --model-dir wav2vec2_finetuned/exp01/best_model \
  --manifest training/manifests/demo/test.csv \
  --output-json wav2vec2_finetuned/exp01/test_metrics.json \
  --output-confusion-csv wav2vec2_finetuned/exp01/test_confusion_matrix_eval.csv \
  --output-predictions-csv wav2vec2_finetuned/exp01/test_predictions.csv
```

## 5. Deploy Into SER Service

Set environment variables before starting FastAPI service:

```bash
SER_ENGINE=hf_wav2vec2
SER_HF_MODEL_DIR=./wav2vec2_finetuned/exp01/best_model
SER_HF_DEVICE=auto
```

Then start service:

```bash
uvicorn app:app --host 0.0.0.0 --port 8001
```

`POST /ser/analyze` will use the fine-tuned model.

## 6. Build Multimodal Fusion Features

Use trained acoustic models + ASR + text sentiment model to build tabular features:

```bash
cd backend/ser-service
python training/build_fusion_features.py \
  --train-manifest training/manifests/multilingual_4class/train.csv \
  --val-manifest training/manifests/multilingual_4class/val.csv \
  --test-manifest training/manifests/multilingual_4class/test.csv \
  --output-dir training/fusion/features_exp01 \
  --audio-model-en training/checkpoints/ser_multilingual_4class_exp02/best_model \
  --audio-model-zh training/checkpoints/ser_casia_ft_exp01/best_model \
  --text-model-en ./text_models/en_roberta_sentiment \
  --text-model-zh ./text_models/zh_roberta_sentiment \
  --audio-device cuda \
  --text-device cuda \
  --whisper-model small \
  --whisper-device cpu \
  --asr-cache-json training/fusion/features_exp01/asr_cache.json
```

Output:

- `train_features.csv`
- `val_features.csv`
- `test_features.csv`
- `summary.json`

## 7. Train Learnable Late Fusion + Temperature Calibration

```bash
cd backend/ser-service
python training/train_late_fusion.py \
  --train-features training/fusion/features_exp01/train_features.csv \
  --val-features training/fusion/features_exp01/val_features.csv \
  --test-features training/fusion/features_exp01/test_features.csv \
  --output-dir training/fusion/models/fusion_exp01 \
  --mode fusion \
  --epochs 80 \
  --batch-size 128 \
  --hidden-size 64 \
  --dropout 0.2 \
  --learning-rate 2e-3 \
  --weight-decay 1e-4 \
  --device cuda
```

Output:

- `fusion_model.pt`
- `feature_scaler.json`
- `train_report.json` (uncalibrated and calibrated metrics)
- `val_confusion_calibrated.csv`
- `test_confusion_calibrated.csv`

## 8. Run Ablation (`audio_only / text_only / fusion`)

```bash
cd backend/ser-service
python training/run_fusion_ablation.py \
  --train-features training/fusion/features_exp01/train_features.csv \
  --val-features training/fusion/features_exp01/val_features.csv \
  --test-features training/fusion/features_exp01/test_features.csv \
  --output-root training/fusion/ablation_exp01 \
  --epochs 80 \
  --batch-size 128 \
  --device cuda
```

Output:

- `ablation_summary.csv`
- `ablation_summary.json`

## 9. Select Production Candidate (`fusion_best`)

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

# SER Service (FastAPI)

Run locally:

```bash
cd ser-service
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8001
```

Environment variables:
- `SER_ENGINE` (default: `speechbrain`, optional: `hf_wav2vec2`)
- `SER_MODEL_NAME` (default: `speechbrain/emotion-recognition-wav2vec2-IEMOCAP`)
- `SER_HF_MODEL_DIR` (used when `SER_ENGINE=hf_wav2vec2`)
- `SER_HF_ROUTING` (default: `single`, optional: `language`)
- `SER_HF_MODEL_DIR_EN` (used when `SER_HF_ROUTING=language`)
- `SER_HF_MODEL_DIR_ZH` (used when `SER_HF_ROUTING=language`)
- `SER_HF_DEFAULT_LANGUAGE` (default: `en`, optional: `zh`)
- `SER_HF_DEVICE` (default: `auto`, can be `cpu` or `cuda`)
- `TEXT_ENGINE` (default: `hf`, optional: `lexicon`)
- `TEXT_HF_ROUTING` (default: `language`, optional: `single`)
- `TEXT_HF_MODEL` (used when `TEXT_HF_ROUTING=single`)
- `TEXT_HF_MODEL_EN` (default: `./text_models/en_roberta_sentiment`)
- `TEXT_HF_MODEL_ZH` (default: `./text_models/zh_roberta_sentiment`)
- `TEXT_HF_DEFAULT_LANGUAGE` (default: `zh`)
- `TEXT_HF_DEVICE` (default: follow `SER_HF_DEVICE`)
- `FUSION_ENABLED` (default: `true`)
- `FUSION_MODEL_DIR` (default: `./training/fusion/models/fusion_best`)
- `FUSION_DEVICE` (default: follow `SER_HF_DEVICE`)
- `WHISPER_MODEL` (default: `small`)
- `WHISPER_DEVICE` (default: `cpu`, can be `cuda`)
- `MAX_ASR_DURATION_MS` (default: `600000`, i.e. 10 minutes)

Endpoints:
- `POST /ser/analyze`
  - form-data: `file=@audio.wav`
  - optional: `segment_ms`, `overlap_ms`
  - optional: `language_hint` (`zh`/`en`), used by HF multilingual routing
  - optional fusion text features: `text_negative`, `text_neutral`, `text_positive`, `text_negative_score`, `text_length_norm`
  - response includes `audioSummary`, `textFeatures`, `fusion` (if fusion model enabled)
- `POST /asr/transcribe`
  - form-data: `file=@audio.wav` (also supports mp3/m4a/flac/ogg/webm)
  - returns text, language, segments and meta
- `POST /text/sentiment`
  - json body: `{"text":"...", "language":"zh|en"}`
  - returns `negativeScore` and class probabilities

Example (HF bilingual routing):

```bash
export SER_ENGINE=hf_wav2vec2
export SER_HF_ROUTING=language
export SER_HF_MODEL_DIR_EN=./training/checkpoints/ser_multilingual_4class_exp02/best_model
export SER_HF_MODEL_DIR_ZH=./training/checkpoints/ser_casia_ft_exp01/best_model
export SER_HF_DEFAULT_LANGUAGE=zh
export SER_HF_DEVICE=cuda
export TEXT_ENGINE=hf
export TEXT_HF_ROUTING=language
export TEXT_HF_DEVICE=cuda
export FUSION_ENABLED=true
export FUSION_MODEL_DIR=./training/fusion/models/fusion_best
export FUSION_DEVICE=cuda
```

Download text models locally:

```bash
python training/download_text_models.py --output-dir text_models
```

Training pipeline (`wav2vec2 + classification head`):

- `training/build_manifest.py`
- `training/train_wav2vec2_cls.py`
- `training/evaluate_wav2vec2_cls.py`
- `training/README.md`

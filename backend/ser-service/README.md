# SER Service (FastAPI)

Run locally:

```bash
cd ser-service
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8001
```

Environment variables:
- `SER_MODEL_NAME` (default: `speechbrain/emotion-recognition-wav2vec2-IEMOCAP`)
- `WHISPER_MODEL` (default: `small`)
- `WHISPER_DEVICE` (default: `cpu`, can be `cuda`)
- `MAX_ASR_DURATION_MS` (default: `600000`, i.e. 10 minutes)

Endpoints:
- `POST /ser/analyze`
  - form-data: `file=@audio.wav`
  - optional: `segment_ms`, `overlap_ms`
- `POST /asr/transcribe`
  - form-data: `file=@audio.wav` (also supports mp3/m4a/flac/ogg/webm)
  - returns text, language, segments and meta

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SER_DIR="$ROOT_DIR/backend/ser-service"
VENV_GPU_DIR="$SER_DIR/venv-gpu"
VENV_DIR="$SER_DIR/venv"
PY="$VENV_DIR/bin/python"
LOG_DIR="$SER_DIR/logs"
STDOUT_LOG="$LOG_DIR/ser-stdout.log"
STDERR_LOG="$LOG_DIR/ser-stderr.log"

if [[ ! -d "$SER_DIR" ]]; then
  echo "[start-ser] SER directory not found: $SER_DIR"
  exit 1
fi

if [[ -x "$VENV_GPU_DIR/bin/python" ]]; then
  PY="$VENV_GPU_DIR/bin/python"
  USING_VENV="venv-gpu"
else
  USING_VENV="venv"
fi

if [[ ! -x "$PY" ]]; then
  echo "[start-ser] venv missing, creating..."
  python3 -m venv "$VENV_DIR"
fi

DEFAULT_SER_MODEL_EN="$SER_DIR/training/checkpoints/ser_multilingual_4class_exp02/best_model"
DEFAULT_SER_MODEL_ZH="$SER_DIR/training/checkpoints/ser_multilingual_xlsr_stageB_exp04_fast/best_model"
DEFAULT_TEXT_MODEL_EN="$SER_DIR/text_models/en_roberta_sentiment"
DEFAULT_TEXT_MODEL_ZH="$SER_DIR/training/text_models/zh_sentiment_exp03/best_model"
DEFAULT_FUSION_MODEL="$SER_DIR/training/fusion/models/fusion_exp04_gated"

: "${SER_ENGINE:=hf_wav2vec2}"
: "${SER_HF_ROUTING:=language}"
: "${SER_HF_DEFAULT_LANGUAGE:=zh}"
: "${SER_HF_MODEL_DIR_EN:=$DEFAULT_SER_MODEL_EN}"
: "${SER_HF_MODEL_DIR_ZH:=$DEFAULT_SER_MODEL_ZH}"
: "${TEXT_ENGINE:=hf}"
: "${TEXT_HF_ROUTING:=language}"
: "${TEXT_HF_DEFAULT_LANGUAGE:=zh}"
: "${TEXT_HF_MODEL_EN:=$DEFAULT_TEXT_MODEL_EN}"
: "${TEXT_HF_MODEL_ZH:=$DEFAULT_TEXT_MODEL_ZH}"
: "${FUSION_ENABLED:=true}"
: "${FUSION_MODEL_DIR:=$DEFAULT_FUSION_MODEL}"
export SER_ENGINE SER_HF_ROUTING SER_HF_DEFAULT_LANGUAGE SER_HF_MODEL_DIR_EN SER_HF_MODEL_DIR_ZH
export TEXT_ENGINE TEXT_HF_ROUTING TEXT_HF_DEFAULT_LANGUAGE TEXT_HF_MODEL_EN TEXT_HF_MODEL_ZH
export FUSION_ENABLED FUSION_MODEL_DIR

echo "[start-ser] using environment:"
echo "  SER_ENGINE=$SER_ENGINE"
echo "  SER_HF_MODEL_DIR_EN=$SER_HF_MODEL_DIR_EN"
echo "  SER_HF_MODEL_DIR_ZH=$SER_HF_MODEL_DIR_ZH"
echo "  TEXT_ENGINE=$TEXT_ENGINE"
echo "  TEXT_HF_MODEL_EN=$TEXT_HF_MODEL_EN"
echo "  TEXT_HF_MODEL_ZH=$TEXT_HF_MODEL_ZH"
echo "  FUSION_MODEL_DIR=$FUSION_MODEL_DIR"
echo "[start-ser] python env=$USING_VENV, executable=$PY"

echo "[start-ser] ensuring dependencies..."
"$PY" -m pip install -r "$SER_DIR/requirements.txt"

mkdir -p "$LOG_DIR"
rm -f "$STDOUT_LOG" "$STDERR_LOG"

if lsof -tiTCP:8001 -sTCP:LISTEN >/dev/null 2>&1; then
  lsof -tiTCP:8001 -sTCP:LISTEN | xargs kill -9 || true
  sleep 1
fi

nohup "$PY" -m uvicorn app:app --host 127.0.0.1 --port 8001 \
  >"$STDOUT_LOG" 2>"$STDERR_LOG" &
echo "[start-ser] process started PID=$!"

ok=0
for _ in {1..30}; do
  if curl -fsS "http://127.0.0.1:8001/health" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 1
done

if [[ "$ok" -ne 1 ]]; then
  echo "[start-ser] failed to become healthy, check logs:"
  echo "  $STDOUT_LOG"
  echo "  $STDERR_LOG"
  exit 1
fi

echo "[start-ser] HEALTH OK => http://127.0.0.1:8001/health"
echo "[start-ser] warming up models via /warmup ..."
if curl --max-time 240 -fsS "http://127.0.0.1:8001/warmup" >/dev/null 2>&1; then
  echo "[start-ser] WARMUP OK"
  if curl -fsS "http://127.0.0.1:8001/health" >/dev/null 2>&1; then
    echo "[start-ser] HEALTH SUMMARY:"
    curl -fsS "http://127.0.0.1:8001/health"
    echo
  fi
else
  echo "[start-ser] warmup request failed, service is still running. Check logs if needed."
fi

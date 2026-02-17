#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SER_DIR="$ROOT_DIR/backend/ser-service"
VENV_DIR="$SER_DIR/venv"
PY="$VENV_DIR/bin/python"
LOG_DIR="$SER_DIR/logs"
STDOUT_LOG="$LOG_DIR/ser-stdout.log"
STDERR_LOG="$LOG_DIR/ser-stderr.log"

if [[ ! -d "$SER_DIR" ]]; then
  echo "[start-ser] SER directory not found: $SER_DIR"
  exit 1
fi

if [[ ! -x "$PY" ]]; then
  echo "[start-ser] venv missing, creating..."
  python3 -m venv "$VENV_DIR"
fi

if lsof -iTCP:8001 -sTCP:LISTEN >/dev/null 2>&1; then
  if curl -fsS "http://127.0.0.1:8001/health" >/dev/null 2>&1; then
    echo "[start-ser] already healthy on 127.0.0.1:8001"
    echo "[start-ser] warming up models via /warmup ..."
    if curl --max-time 240 -fsS "http://127.0.0.1:8001/warmup" >/dev/null 2>&1; then
      echo "[start-ser] WARMUP OK"
    else
      echo "[start-ser] warmup request failed, service is still running. Check logs if needed."
    fi
    exit 0
  fi
fi

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
else
  echo "[start-ser] warmup request failed, service is still running. Check logs if needed."
fi

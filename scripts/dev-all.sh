#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[dev-all] Starting SER service on 127.0.0.1:8001 ..."
bash "$ROOT_DIR/scripts/start-ser.sh"

echo "[dev-all] Starting backend on 127.0.0.1:8080 ..."
(cd "$ROOT_DIR/backend" && mvn spring-boot:run) &
BACK_PID=$!

echo "[dev-all] Starting frontend on 127.0.0.1:5173 ..."
(cd "$ROOT_DIR/frontend" && npm run dev -- --host 127.0.0.1 --port 5173) &
FRONT_PID=$!

cleanup() {
  kill "$BACK_PID" "$FRONT_PID" >/dev/null 2>&1 || true
}

trap cleanup INT TERM

echo "[dev-all] Running. Open http://127.0.0.1:5173"
wait

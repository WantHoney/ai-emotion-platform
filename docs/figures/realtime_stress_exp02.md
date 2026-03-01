# Realtime Stress Exp02

- Date: 2026-03-01
- Command: `scripts/stress-realtime.ps1 -TaskId 31 -Connections 30 -DurationSec 40`
- Target: `ws://127.0.0.1:8080/ws/tasks/stream?taskId=31&accessToken=<token>`
- Result: `success=30`, `failed=0`, `totalMessages=30`, `avg/client=1.0`

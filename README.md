# AI Emotion Monorepo

This repository is a monorepo with:
- `backend/`: Spring Boot + MySQL (API server)
- `frontend/`: Vue 3 + Vite + Element Plus (web client)

## 1. Prerequisites

- JDK 17+
- Maven 3.9+
- Node.js `^20.19.0 || >=22.12.0`
- MySQL 8+

## 2. Required Environment Variables

Backend runs in real AI mode by default. You must provide API key and DB connection.

- `OPENROUTER_API_KEY` (required)
- `OPENROUTER_BASE_URL` (optional, default `https://openrouter.ai/api`)
- `OPENROUTER_MODEL` (optional)
- `SPRING_DATASOURCE_URL` (optional override)
- `SPRING_DATASOURCE_USERNAME` (optional override)
- `SPRING_DATASOURCE_PASSWORD` (optional override)
- `SER_ENABLED` (optional, default `true`)
- `SER_BASE_URL` (optional, default `http://127.0.0.1:8001`)

Windows PowerShell example:

```powershell
$env:OPENROUTER_API_KEY="your_key"
$env:SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/ai_emotion?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
$env:SPRING_DATASOURCE_USERNAME="root"
$env:SPRING_DATASOURCE_PASSWORD="your_password"
```

## 3. Ports

- Backend: `http://127.0.0.1:8080`
- Frontend dev: `http://127.0.0.1:5173`
- SER service: `http://127.0.0.1:8001`
- Vite proxy: `http://127.0.0.1:5173/api/*` -> `http://127.0.0.1:8080/api/*`

Frontend entry routes:

- User Portal: `http://127.0.0.1:5173/app/home`
- User login/register: `http://127.0.0.1:5173/app/login`
- Admin Console login: `http://127.0.0.1:5173/admin/login`
- Admin Console: `http://127.0.0.1:5173/admin/dashboard`

## 4. Start

### 4.1 Start separately

SER service (required for `/api/health` ser status = `UP`):

```powershell
./scripts/start-ser.ps1
```

The startup script now performs `/warmup` after health check, so first start may take longer.

or on macOS/Linux:

```bash
bash ./scripts/start-ser.sh
```

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

### 4.2 One command start

Windows PowerShell:

```powershell
./scripts/dev-all.ps1
```

macOS/Linux:

```bash
bash ./scripts/dev-all.sh
```

Both commands now start `SER -> backend -> frontend` in order.

## 5. Runtime Check

Backend direct checks:

```bash
curl http://127.0.0.1:8001/health
curl http://127.0.0.1:8080/api/health
curl http://127.0.0.1:8080/api/home
curl "http://127.0.0.1:8080/api/psy-centers?cityCode=310100"
```

Frontend proxy checks:

```bash
curl http://127.0.0.1:5173/api/health
curl http://127.0.0.1:5173/api/home
curl "http://127.0.0.1:5173/api/psy-centers?cityCode=310100"
```

One-command smoke test (Windows PowerShell):

```powershell
./scripts/smoke-api.ps1
```

The script validates public APIs, user upload->analysis flow, and admin governance endpoints.

Manual page-by-page acceptance checklist:

- `docs/frontend_page_interaction_checklist.md`

Authenticated checks (register first, then pass Bearer token):

```bash
curl -X POST http://127.0.0.1:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"qa_user","password":"QaPass_123456"}'

curl "http://127.0.0.1:8080/api/tasks?page=1&pageSize=10" \
  -H "Authorization: Bearer <accessToken>"

curl "http://127.0.0.1:8080/api/reports?page=1&pageSize=10" \
  -H "Authorization: Bearer <accessToken>"
```

## 6. Main API Contracts

- Health: `GET /api/health`
- System status (admin): `GET /api/system/status`
- Home: `GET /api/home`
- Psy centers: `GET /api/psy-centers?cityCode=310100`
  - Also supports `city_code`
- Tasks list: `GET /api/tasks?page=1&pageSize=10&status=&keyword=&sortBy=createdAt&sortOrder=desc`
- Reports list: `GET /api/reports?page=1&pageSize=10&riskLevel=&emotion=&keyword=&sortBy=createdAt&sortOrder=desc`
- Report detail: `GET /api/reports/{id}`

List response shape:

```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "pageSize": 10,
  "size": 10
}
```

Error response shape:

```json
{
  "code": "HTTP_401",
  "message": "Unauthorized",
  "traceId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

## 7. Admin Governance API

- Models:
  - `GET /api/admin/models`
  - `POST /api/admin/models`
  - `POST /api/admin/models/{id}/switch`
  - `GET /api/admin/models/switch-logs`
- Warning rules:
  - `GET /api/admin/warning-rules`
  - `POST /api/admin/warning-rules`
  - `PUT /api/admin/warning-rules/{id}`
  - `POST /api/admin/warning-rules/{id}/toggle?enabled=true|false`
- Warning events:
  - `GET /api/admin/warnings?page=1&pageSize=10`
  - `POST /api/admin/warnings/{id}/actions`
  - `GET /api/admin/warnings/{id}/actions`
- Analytics:
  - `GET /api/admin/analytics/daily?days=14`
  - `GET /api/admin/analytics/quality?windowDays=7&baselineDays=7`
  - `GET /api/admin/governance/summary`

## 8. Chunk Upload API

- Init upload session:
  - `POST /api/audio/upload-sessions/init`
  - body: `{fileName, contentType, fileSize, totalChunks}`
- Upload chunk:
  - `PUT /api/audio/upload-sessions/{uploadId}/chunks/{chunkIndex}`
  - form-data: `file=<chunk>`
- Session status:
  - `GET /api/audio/upload-sessions/{uploadId}`
- Complete merge:
  - `POST /api/audio/upload-sessions/{uploadId}/complete`
  - body: `{autoStartTask:true}`
- Cancel session:
  - `DELETE /api/audio/upload-sessions/{uploadId}`

Frontend upload page (`/app/upload`) already uses chunk mode with realtime progress.

## 9. DB migration notes

- Previous governance schema: `backend/docs/db/migrations/V5__model_warning_ops.sql`
- New SLA/quality extension: `backend/docs/db/migrations/V6__warning_sla_and_quality.sql`

If `V6` is not applied yet, backend still runs in backward-compatible mode (SLA fields degrade gracefully).

## 10. Troubleshooting

### `ECONNREFUSED` on frontend `/api/*`

1. Confirm backend is running on `127.0.0.1:8080`.
2. Confirm frontend dev is running on `127.0.0.1:5173`.
3. Confirm `frontend/vite.config.ts` proxy target is `http://127.0.0.1:8080`.

### Backend startup fails with OpenAI API key error

Set `OPENROUTER_API_KEY` in your shell before running backend.

### Backend startup fails with `Port 8080 was already in use`

Stop the process using port `8080`, or change `server.port` in backend config.

### `/api/health` shows `"ser":"DOWN"`

1. Run `./scripts/start-ser.ps1` (Windows) or `bash ./scripts/start-ser.sh` (macOS/Linux).
2. Verify `http://127.0.0.1:8001/health` returns JSON with `"status":"ok"`.
3. If still failing, check logs:
   - `backend/ser-service/logs/ser-stdout.log`
   - `backend/ser-service/logs/ser-stderr.log`

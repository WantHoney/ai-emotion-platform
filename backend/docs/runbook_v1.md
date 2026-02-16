# Runbook (v1 mock-only)

This runbook documents the minimal end-to-end flow for the v1 mock-only demo (no real AI integration, no auth system).

## 1) Import schema into local MySQL

```bash
mysql -u root -p < docs/db/schema_v1.sql
```

If your MySQL instance uses a different user/password or host, adjust the command accordingly:

```bash
mysql -h 127.0.0.1 -P 3306 -u <user> -p < docs/db/schema_v1.sql
```

## 2) Configure `application.yaml`

Required fields:

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `app.upload.dir`

Example (edit `src/main/resources/application.yaml`):

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_emotion?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: root
    password: your_password
app:
  upload:
    dir: /absolute/path/to/uploads
```

> Ensure the upload directory exists and is writable.

## 3) Start the service

```bash
mvn spring-boot:run
```

## 4) Minimal API flow (mock-only)

> The following uses `localhost:8080` and a sample file `./sample.mp3`. Adjust as needed.

### Step 1: Upload audio

```bash
curl -X POST \
  -F "file=@./sample.mp3" \
  http://localhost:8080/api/audio/upload
```

**Expected fields**:
- `audioId`
- `originalName`
- `fileName`
- `downloadUrl`

### Step 2: Start analysis

```bash
curl -X POST \
  "http://localhost:8080/api/audio/<audioId>/analysis/start"
```

**Expected fields**:
- `analysisId`
- `audioId`
- `status` (should be `PENDING`)

### Step 3: Run mock analysis (sync or async)

**Sync:**
```bash
curl -X POST \
  "http://localhost:8080/api/analysis/<analysisId>/mock-run"
```

**Async:**
```bash
curl -X POST \
  "http://localhost:8080/api/analysis/<analysisId>/mock-run-async"
```

**Expected fields**:
- Sync returns the report payload (see Step 4 fields).
- Async returns:
  - `analysisId`
  - `status` (should be `RUNNING`)

### Step 4: Fetch report

```bash
curl -X GET \
  "http://localhost:8080/api/analysis/<analysisId>/report"
```

**Expected fields**:
- `analysisId`
- `audioId`
- `status`
- `overall` (may be `null` if no segments)
- `segments` (array)

---

If any step fails with `400`/`500`, check the application logs. Common issues include a missing DB connection or missing upload directory.

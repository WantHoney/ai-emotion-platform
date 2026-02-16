# Schema Review (schema_v1.sql)

This document summarizes how the current code maps to `docs/db/schema_v1.sql`, flags optional constraints to tighten data integrity, and records a minimal mock-only flow for v1.

## 1) Code ↔ DDL Alignment

### `audio_file`
- **Code usage**: upload inserts `user_id`, `original_name`, `stored_name`, `storage_path`, `content_type`, `size_bytes`, `sha256`, `duration_ms`, `status`.
- **DDL**: all fields exist; `stored_name` has a unique key; `user_id` FK references `auth_user`.
- **Status**: `UPLOADED/DELETED` used by code and schema default.

### `audio_analysis`
- **Code usage**: creates analysis with `audio_id`, `model_name`, `model_version`, `status` (`PENDING/RUNNING/SUCCESS/FAILED`), and updates `summary_json`/`error_message`.
- **DDL**: all fields present; FK to `audio_file` with cascade on delete.

### `core_report`
- **Code usage**: stores JSON report snapshots, upsert by `analysis_id`.
- **DDL**: `report_json` is JSON; unique key on `analysis_id` supports upsert behavior.

### `audio_segment` + `segment_emotion` + `emotion_label`
- **Code usage**: mock flow inserts segments, labels, and segment emotions; query joins are consistent with schema keys.
- **DDL**: composite unique key on `(segment_id, emotion_id)` supports upsert; FKs enforce cascading deletes from `audio_analysis` → `audio_segment` → `segment_emotion`.

## 2) Suggested Constraints (Optional Enhancements)

> These are safe additions that improve data quality but are not required for the current mock flow.

1. **Status checks**
   - `audio_file.status` CHECK in (`UPLOADED`, `DELETED`).
   - `audio_analysis.status` CHECK in (`PENDING`, `RUNNING`, `SUCCESS`, `FAILED`).

2. **Segment uniqueness**
   - Consider `UNIQUE (analysis_id, start_ms, end_ms)` on `audio_segment` to prevent duplicate segments if a run is re-triggered.

3. **Path/filename consistency**
   - `stored_name` is unique; if needed, enforce `storage_path` prefix rules in application logic (or via generated columns).

## 3) Minimal Mock-Only Flow (v1)

1. **Import schema**
   - Load `docs/db/schema_v1.sql` into the local MySQL instance.

2. **Configure app**
   - Ensure `application.yaml` points to the same DB and sets `app.upload.dir`.

3. **Run service**
   ```bash
   mvn spring-boot:run
   ```

4. **API flow**
   - `POST /api/audio/upload` (multipart: `file`)
   - `POST /api/audio/{audioId}/analysis/start`
   - `POST /api/analysis/{analysisId}/mock-run` or `/mock-run-async`
   - `GET /api/analysis/{analysisId}/report`
   - For a step-by-step, runnable curl sequence, see `docs/runbook_v1.md`.

## 4) Deferred Items (Post v1)

- Real AI service integration (URL/auth/request/response/error codes).
- Upload validation (format, duration, checksum).
- Auth system (JWT/Session, user/roles, middleware).

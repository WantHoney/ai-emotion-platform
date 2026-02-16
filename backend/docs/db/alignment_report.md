# Alignment Report (Schema v1)

This document records items that require design decisions rather than mechanical alignment, plus alignment actions taken for schema v1.

## Alignment Actions (Schema v1)

1. **core_report upsert is idempotent**
   - `CoreReportRepository.upsert` uses `ON DUPLICATE KEY UPDATE` keyed by `analysis_id` (matches `uk_core_report_analysis`).
   - `report_json` is written/updated with `CAST(? AS JSON)` to match the JSON column.
   - File: `src/main/java/com/wuhao/aiemotion/repository/CoreReportRepository.java`.

2. **segment_emotion upsert is idempotent**
   - `ReportMockRepository.upsertSegmentEmotion` uses `ON DUPLICATE KEY UPDATE` on `(segment_id, emotion_id)` to prevent duplicate rows.
   - File: `src/main/java/com/wuhao/aiemotion/repository/ReportMockRepository.java`.

## Open Design Questions

1. **`audio_file.user_id` nullability**
   - Schema allows `NULL` and the current upload flow writes `NULL`.
   - Decision needed: should v1 allow anonymous uploads, or enforce user linkage?

2. **Segment uniqueness**
   - `audio_segment` has no uniqueness constraint on `(analysis_id, start_ms, end_ms)`.
   - Decision needed: enforce uniqueness in schema/app or allow duplicates for re-runs?

3. **Status value constraints**
   - `audio_file.status` and `audio_analysis.status` are free-form `varchar`.
   - Decision needed: add CHECK constraints or keep flexible for future states?

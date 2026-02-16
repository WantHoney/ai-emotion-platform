# Alignment Notes - PR01 (Spring AI Mainline Integration)

## Summary
- Introduced a pluggable `AiClient` abstraction with mock and Spring AI placeholder implementations.
- Kept all persistence logic and schema usage intact (no changes to `schema_v1.sql`).
- Added configuration toggles for `ai.mode` to switch between mock and Spring AI flows.

## Schema Impact
- **No schema changes.**
- Writes continue to use the same tables:
  - `audio_analysis` for status + `summary_json` + `error_message`
  - `audio_segment`, `segment_emotion`, `emotion_label` for segment/emotion data
  - `core_report` for report snapshots (upsert by analysis_id)

## Notes
- Mock flow remains default (`ai.mode=mock`).
- Spring AI flow only generates `report_json`; ASR remains TODO.

# V6 Table Usage Audit (ai_emotion)

Audit date: 2026-02-16
Scope:
- Runtime references: `backend/src/main/java` (JdbcTemplate SQL and service/repository usage)
- Database snapshot: `information_schema.tables` in current `ai_emotion` schema

## Summary

- Total tables in DB: **45**
- Runtime-used tables: **28**
- Candidate legacy/unused tables: **17**

## Runtime-used tables (keep)

`analysis_result`, `analysis_segment`, `analysis_task`, `analytics_daily_summary`, `articles`, `audio_analysis`, `audio_file`, `audio_segment`, `audio_upload_chunk`, `audio_upload_session`, `auth_role`, `auth_session`, `auth_user`, `auth_user_role`, `banners`, `books`, `content_events`, `core_report`, `emotion_label`, `model_registry`, `model_switch_log`, `psy_centers`, `quotes`, `report_resource`, `segment_emotion`, `warning_action_log`, `warning_event`, `warning_rule`

## Candidate legacy tables (no runtime ref in backend/src/main/java)

`audio_tag`, `auth_menu`, `auth_role_menu`, `consultation_order`, `core_feedback`, `counselor_info`, `intervention_plan`, `knowledge_article`, `knowledge_category`, `ops_message_template`, `sys_dict`, `sys_login_log`, `sys_notice`, `sys_operation_log`, `tag`, `user_emotion_daily`, `user_plan_log`

## Recommendation

1. Phase 1 (safe): rename candidate tables to backup names (`zbak_yyyymmdd_*`).
2. Observation period: run 3-7 days in dev/staging and verify no errors.
3. Phase 2: drop backup tables when confirmed unused.

## Notes

- This audit is based on repository runtime SQL references. If external jobs, BI scripts, or manual queries depend on these tables, do not remove directly.
- Use `backend/docs/db/audit/V6_cleanup_candidate.sql` for archive-rename and rollback helpers.

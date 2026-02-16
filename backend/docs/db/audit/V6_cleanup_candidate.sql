-- V6_cleanup_candidate.sql
-- Strategy: safe archive-by-rename first, do NOT drop directly.
-- Date tag: 20260216

-- -------------------------------
-- 1) Archive by rename (safe first)
-- Execute one-by-one and verify each statement succeeds.
-- -------------------------------

RENAME TABLE audio_tag TO zbak_20260216_audio_tag;
RENAME TABLE auth_menu TO zbak_20260216_auth_menu;
RENAME TABLE auth_role_menu TO zbak_20260216_auth_role_menu;
RENAME TABLE consultation_order TO zbak_20260216_consultation_order;
RENAME TABLE core_feedback TO zbak_20260216_core_feedback;
RENAME TABLE counselor_info TO zbak_20260216_counselor_info;
RENAME TABLE intervention_plan TO zbak_20260216_intervention_plan;
RENAME TABLE knowledge_article TO zbak_20260216_knowledge_article;
RENAME TABLE knowledge_category TO zbak_20260216_knowledge_category;
RENAME TABLE ops_message_template TO zbak_20260216_ops_message_template;
RENAME TABLE sys_dict TO zbak_20260216_sys_dict;
RENAME TABLE sys_login_log TO zbak_20260216_sys_login_log;
RENAME TABLE sys_notice TO zbak_20260216_sys_notice;
RENAME TABLE sys_operation_log TO zbak_20260216_sys_operation_log;
RENAME TABLE tag TO zbak_20260216_tag;
RENAME TABLE user_emotion_daily TO zbak_20260216_user_emotion_daily;
RENAME TABLE user_plan_log TO zbak_20260216_user_plan_log;

-- -------------------------------
-- 2) Rollback (if needed)
-- -------------------------------

-- RENAME TABLE zbak_20260216_audio_tag TO audio_tag;
-- RENAME TABLE zbak_20260216_auth_menu TO auth_menu;
-- RENAME TABLE zbak_20260216_auth_role_menu TO auth_role_menu;
-- RENAME TABLE zbak_20260216_consultation_order TO consultation_order;
-- RENAME TABLE zbak_20260216_core_feedback TO core_feedback;
-- RENAME TABLE zbak_20260216_counselor_info TO counselor_info;
-- RENAME TABLE zbak_20260216_intervention_plan TO intervention_plan;
-- RENAME TABLE zbak_20260216_knowledge_article TO knowledge_article;
-- RENAME TABLE zbak_20260216_knowledge_category TO knowledge_category;
-- RENAME TABLE zbak_20260216_ops_message_template TO ops_message_template;
-- RENAME TABLE zbak_20260216_sys_dict TO sys_dict;
-- RENAME TABLE zbak_20260216_sys_login_log TO sys_login_log;
-- RENAME TABLE zbak_20260216_sys_notice TO sys_notice;
-- RENAME TABLE zbak_20260216_sys_operation_log TO sys_operation_log;
-- RENAME TABLE zbak_20260216_tag TO tag;
-- RENAME TABLE zbak_20260216_user_emotion_daily TO user_emotion_daily;
-- RENAME TABLE zbak_20260216_user_plan_log TO user_plan_log;

-- -------------------------------
-- 3) Final cleanup (after observation period)
-- -------------------------------

-- DROP TABLE IF EXISTS zbak_20260216_audio_tag;
-- DROP TABLE IF EXISTS zbak_20260216_auth_menu;
-- DROP TABLE IF EXISTS zbak_20260216_auth_role_menu;
-- DROP TABLE IF EXISTS zbak_20260216_consultation_order;
-- DROP TABLE IF EXISTS zbak_20260216_core_feedback;
-- DROP TABLE IF EXISTS zbak_20260216_counselor_info;
-- DROP TABLE IF EXISTS zbak_20260216_intervention_plan;
-- DROP TABLE IF EXISTS zbak_20260216_knowledge_article;
-- DROP TABLE IF EXISTS zbak_20260216_knowledge_category;
-- DROP TABLE IF EXISTS zbak_20260216_ops_message_template;
-- DROP TABLE IF EXISTS zbak_20260216_sys_dict;
-- DROP TABLE IF EXISTS zbak_20260216_sys_login_log;
-- DROP TABLE IF EXISTS zbak_20260216_sys_notice;
-- DROP TABLE IF EXISTS zbak_20260216_sys_operation_log;
-- DROP TABLE IF EXISTS zbak_20260216_tag;
-- DROP TABLE IF EXISTS zbak_20260216_user_emotion_daily;
-- DROP TABLE IF EXISTS zbak_20260216_user_plan_log;

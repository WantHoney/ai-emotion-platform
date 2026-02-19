-- V7__task_report_user_sequence_indexes.sql
-- Purpose:
-- - Improve performance for user-scoped task/report sequence queries.
-- - Safe to run multiple times.

SET @idx_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'analysis_task'
    AND INDEX_NAME = 'idx_analysis_task_audio_id'
);
SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE analysis_task ADD INDEX idx_analysis_task_audio_id (audio_file_id, id)',
  'SELECT ''skip idx_analysis_task_audio_id'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'report_resource'
    AND INDEX_NAME = 'idx_report_resource_audio_id'
);
SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE report_resource ADD INDEX idx_report_resource_audio_id (audio_id, id)',
  'SELECT ''skip idx_report_resource_audio_id'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

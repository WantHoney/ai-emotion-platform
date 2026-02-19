-- V8__cleanup_legacy_sequence_indexes.sql
-- Purpose:
-- - Remove legacy indexes from old V7 version if both old/new indexes coexist.
-- - Safe to run multiple times.

-- analysis_task: drop legacy idx only when new idx exists
SET @old_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'analysis_task'
    AND INDEX_NAME = 'idx_analysis_task_audio_created_id'
);
SET @new_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'analysis_task'
    AND INDEX_NAME = 'idx_analysis_task_audio_id'
);
SET @ddl := IF(
  @old_exists > 0 AND @new_exists > 0,
  'ALTER TABLE analysis_task DROP INDEX idx_analysis_task_audio_created_id',
  'SELECT ''skip drop idx_analysis_task_audio_created_id'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- report_resource: drop legacy idx only when new idx exists
SET @old_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'report_resource'
    AND INDEX_NAME = 'idx_report_resource_audio_created_id'
);
SET @new_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'report_resource'
    AND INDEX_NAME = 'idx_report_resource_audio_id'
);
SET @ddl := IF(
  @old_exists > 0 AND @new_exists > 0,
  'ALTER TABLE report_resource DROP INDEX idx_report_resource_audio_created_id',
  'SELECT ''skip drop idx_report_resource_audio_created_id'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

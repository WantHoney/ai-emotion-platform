-- V6__warning_sla_and_quality.sql
-- MySQL compatibility note:
-- - MySQL 8.0.29+ supports `ADD COLUMN/INDEX IF NOT EXISTS`.
-- - Lower versions do not, so this migration uses INFORMATION_SCHEMA + dynamic SQL.

-- -------------------------------
-- warning_rule: SLA columns
-- -------------------------------

SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warning_rule'
      AND COLUMN_NAME = 'sla_low_minutes'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE warning_rule ADD COLUMN sla_low_minutes INT NOT NULL DEFAULT 1440 COMMENT ''SLA for LOW risk (minutes)''',
    'SELECT ''skip warning_rule.sla_low_minutes'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warning_rule'
      AND COLUMN_NAME = 'sla_medium_minutes'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE warning_rule ADD COLUMN sla_medium_minutes INT NOT NULL DEFAULT 720 COMMENT ''SLA for MEDIUM risk (minutes)''',
    'SELECT ''skip warning_rule.sla_medium_minutes'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warning_rule'
      AND COLUMN_NAME = 'sla_high_minutes'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE warning_rule ADD COLUMN sla_high_minutes INT NOT NULL DEFAULT 240 COMMENT ''SLA for HIGH risk (minutes)''',
    'SELECT ''skip warning_rule.sla_high_minutes'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------
-- warning_event: SLA workflow columns
-- -------------------------------

SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warning_event'
      AND COLUMN_NAME = 'sla_deadline_at'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE warning_event ADD COLUMN sla_deadline_at DATETIME NULL',
    'SELECT ''skip warning_event.sla_deadline_at'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warning_event'
      AND COLUMN_NAME = 'first_acked_at'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE warning_event ADD COLUMN first_acked_at DATETIME NULL',
    'SELECT ''skip warning_event.first_acked_at'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warning_event'
      AND COLUMN_NAME = 'first_followed_at'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE warning_event ADD COLUMN first_followed_at DATETIME NULL',
    'SELECT ''skip warning_event.first_followed_at'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warning_event'
      AND COLUMN_NAME = 'breached'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE warning_event ADD COLUMN breached TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT ''skip warning_event.breached'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------
-- warning_event: indexes
-- -------------------------------

SET @idx_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warning_event'
      AND INDEX_NAME = 'idx_warning_event_sla_deadline'
);
SET @ddl := IF(
    @idx_exists = 0,
    'ALTER TABLE warning_event ADD INDEX idx_warning_event_sla_deadline (sla_deadline_at)',
    'SELECT ''skip idx_warning_event_sla_deadline'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warning_event'
      AND INDEX_NAME = 'idx_warning_event_breached'
);
SET @ddl := IF(
    @idx_exists = 0,
    'ALTER TABLE warning_event ADD INDEX idx_warning_event_breached (breached)',
    'SELECT ''skip idx_warning_event_breached'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warning_event'
      AND INDEX_NAME = 'idx_warning_event_created_status'
);
SET @ddl := IF(
    @idx_exists = 0,
    'ALTER TABLE warning_event ADD INDEX idx_warning_event_created_status (created_at, status)',
    'SELECT ''skip idx_warning_event_created_status'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

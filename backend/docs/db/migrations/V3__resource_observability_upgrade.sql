-- MySQL 8 compatible: add columns if missing
SET @db := DATABASE();

-- max_attempts
SET @col := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='analysis_task' AND COLUMN_NAME='max_attempts'
);
SET @sql := IF(@col=0,
  'ALTER TABLE analysis_task ADD COLUMN max_attempts INT NOT NULL DEFAULT 4',
  'SELECT "max_attempts exists"'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- trace_id
SET @col := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='analysis_task' AND COLUMN_NAME='trace_id'
);
SET @sql := IF(@col=0,
  'ALTER TABLE analysis_task ADD COLUMN trace_id VARCHAR(64) NULL',
  'SELECT "trace_id exists"'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- started_at
SET @col := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='analysis_task' AND COLUMN_NAME='started_at'
);
SET @sql := IF(@col=0,
  'ALTER TABLE analysis_task ADD COLUMN started_at DATETIME NULL',
  'SELECT "started_at exists"'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- finished_at
SET @col := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='analysis_task' AND COLUMN_NAME='finished_at'
);
SET @sql := IF(@col=0,
  'ALTER TABLE analysis_task ADD COLUMN finished_at DATETIME NULL',
  'SELECT "finished_at exists"'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- duration_ms
SET @col := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='analysis_task' AND COLUMN_NAME='duration_ms'
);
SET @sql := IF(@col=0,
  'ALTER TABLE analysis_task ADD COLUMN duration_ms BIGINT NULL',
  'SELECT "duration_ms exists"'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ser_latency_ms
SET @col := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='analysis_task' AND COLUMN_NAME='ser_latency_ms'
);
SET @sql := IF(@col=0,
  'ALTER TABLE analysis_task ADD COLUMN ser_latency_ms BIGINT NULL',
  'SELECT "ser_latency_ms exists"'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS report_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  audio_id BIGINT NOT NULL,
  report_json JSON NOT NULL,
  risk_level VARCHAR(16) NULL,
  overall_emotion VARCHAR(32) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  UNIQUE KEY uk_report_resource_task (task_id),
  KEY idx_report_created (created_at),
  KEY idx_report_filters (risk_level, overall_emotion),
  CONSTRAINT fk_report_resource_task FOREIGN KEY (task_id) REFERENCES analysis_task(id) ON DELETE CASCADE,
  CONSTRAINT fk_report_resource_audio FOREIGN KEY (audio_id) REFERENCES audio_file(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

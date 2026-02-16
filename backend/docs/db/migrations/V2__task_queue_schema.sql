-- V2__task_queue_schema.sql
-- MySQL 8 / InnoDB / utf8mb4

CREATE TABLE IF NOT EXISTS analysis_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  audio_file_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  next_run_at DATETIME NULL,
  locked_at DATETIME NULL,
  locked_by VARCHAR(64) NULL,
  error_message TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_task_status_next_run (status, next_run_at),
  INDEX idx_task_status_locked_at (status, locked_at),
  INDEX idx_task_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS analysis_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  model_name VARCHAR(128) NULL,
  overall_emotion_code VARCHAR(16) NULL,
  overall_confidence DOUBLE NULL,
  duration_ms INT NULL,
  sample_rate INT NULL,
  raw_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_result_task_id (task_id),
  CONSTRAINT fk_result_task FOREIGN KEY (task_id) REFERENCES analysis_task(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS analysis_segment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  start_ms INT NOT NULL,
  end_ms INT NOT NULL,
  emotion_code VARCHAR(16) NOT NULL,
  confidence DOUBLE NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_segment_task_start (task_id, start_ms),
  CONSTRAINT fk_segment_task FOREIGN KEY (task_id) REFERENCES analysis_task(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

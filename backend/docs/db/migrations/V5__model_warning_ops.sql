-- V5__model_warning_ops.sql
-- Purpose:
-- 1) Model management (version registry + switch log)
-- 2) Warning rules / warning events / disposal workflow
-- 3) Aggregated analytics for admin dashboard
-- 4) Chunk upload session metadata (for resumable uploads)

CREATE TABLE IF NOT EXISTS model_registry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_code VARCHAR(64) NOT NULL,
  model_name VARCHAR(128) NOT NULL,
  model_type VARCHAR(32) NOT NULL COMMENT 'ASR|AUDIO_EMOTION|TEXT_SENTIMENT|FUSION|SCORING',
  provider VARCHAR(64) NULL,
  version VARCHAR(64) NOT NULL,
  env VARCHAR(16) NOT NULL DEFAULT 'dev' COMMENT 'dev|staging|prod',
  status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE|OFFLINE|ARCHIVED',
  metrics_json JSON NULL COMMENT 'accuracy/recall/f1/auc etc.',
  config_json JSON NULL,
  published_at DATETIME NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_model_registry_code_version_env (model_code, version, env),
  KEY idx_model_registry_type_env (model_type, env),
  KEY idx_model_registry_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS model_switch_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_type VARCHAR(32) NOT NULL,
  env VARCHAR(16) NOT NULL DEFAULT 'prod',
  from_model_id BIGINT NULL,
  to_model_id BIGINT NOT NULL,
  switch_reason VARCHAR(512) NULL,
  switched_by BIGINT NULL,
  switched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_model_switch_type_env_time (model_type, env, switched_at),
  KEY idx_model_switch_to_model (to_model_id),
  CONSTRAINT fk_model_switch_from_model FOREIGN KEY (from_model_id) REFERENCES model_registry(id),
  CONSTRAINT fk_model_switch_to_model FOREIGN KEY (to_model_id) REFERENCES model_registry(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS warning_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code VARCHAR(64) NOT NULL,
  rule_name VARCHAR(128) NOT NULL,
  description VARCHAR(1024) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  priority INT NOT NULL DEFAULT 100,
  low_threshold DECIMAL(5,2) NOT NULL DEFAULT 40.00,
  medium_threshold DECIMAL(5,2) NOT NULL DEFAULT 60.00,
  high_threshold DECIMAL(5,2) NOT NULL DEFAULT 80.00,
  emotion_combo_json JSON NULL COMMENT 'e.g. {\"required\":[\"SAD\"],\"forbidden\":[]}',
  trend_window_days INT NOT NULL DEFAULT 7,
  trigger_count INT NOT NULL DEFAULT 1 COMMENT 'trigger when appears N times in window',
  suggest_template_code VARCHAR(64) NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_warning_rule_code (rule_code),
  KEY idx_warning_rule_enabled_priority (enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS warning_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id BIGINT NULL,
  task_id BIGINT NULL,
  user_id BIGINT NULL,
  user_mask VARCHAR(128) NULL COMMENT 'desensitized display only, e.g. u_****1234',
  risk_score DECIMAL(6,2) NOT NULL,
  risk_level VARCHAR(16) NOT NULL COMMENT 'LOW|MEDIUM|HIGH',
  top_emotion VARCHAR(32) NULL,
  trigger_rule_id BIGINT NULL,
  trigger_snapshot JSON NULL COMMENT 'frozen rule + model + factors',
  status VARCHAR(16) NOT NULL DEFAULT 'NEW' COMMENT 'NEW|ACKED|FOLLOWING|RESOLVED|CLOSED',
  assigned_to BIGINT NULL,
  resolved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_warning_event_status_level_time (status, risk_level, created_at),
  KEY idx_warning_event_user_time (user_id, created_at),
  KEY idx_warning_event_report (report_id),
  CONSTRAINT fk_warning_event_rule FOREIGN KEY (trigger_rule_id) REFERENCES warning_rule(id),
  CONSTRAINT fk_warning_event_report FOREIGN KEY (report_id) REFERENCES report_resource(id),
  CONSTRAINT fk_warning_event_task FOREIGN KEY (task_id) REFERENCES analysis_task(id),
  CONSTRAINT fk_warning_event_user FOREIGN KEY (user_id) REFERENCES auth_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS warning_action_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  warning_event_id BIGINT NOT NULL,
  action_type VARCHAR(32) NOT NULL COMMENT 'MARK_FOLLOWED|MARK_CALLBACK|PUSH_SUGGESTION|ADD_NOTE|RESOLVE|CLOSE',
  action_note VARCHAR(2048) NULL,
  template_code VARCHAR(64) NULL,
  operator_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_warning_action_event_time (warning_event_id, created_at),
  CONSTRAINT fk_warning_action_event FOREIGN KEY (warning_event_id) REFERENCES warning_event(id) ON DELETE CASCADE,
  CONSTRAINT fk_warning_action_operator FOREIGN KEY (operator_id) REFERENCES auth_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ops_message_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_code VARCHAR(64) NOT NULL,
  title VARCHAR(128) NOT NULL,
  content TEXT NOT NULL,
  channel VARCHAR(16) NOT NULL DEFAULT 'IN_APP' COMMENT 'IN_APP|SMS|EMAIL',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ops_template_code (template_code),
  KEY idx_ops_template_channel_enabled (channel, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS analytics_daily_summary (
  stat_date DATE PRIMARY KEY,
  dau BIGINT NOT NULL DEFAULT 0,
  upload_count BIGINT NOT NULL DEFAULT 0,
  report_count BIGINT NOT NULL DEFAULT 0,
  warning_count BIGINT NOT NULL DEFAULT 0,
  emotion_distribution_json JSON NULL COMMENT 'aggregate only, no raw private text/audio',
  model_monitor_json JSON NULL COMMENT 'distribution shift / sampled error stats',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audio_upload_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  upload_id VARCHAR(64) NOT NULL,
  user_id BIGINT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NULL,
  total_size_bytes BIGINT NULL,
  total_chunks INT NOT NULL DEFAULT 1,
  received_chunks INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'INIT' COMMENT 'INIT|UPLOADING|MERGED|FAILED|CANCELED',
  merged_audio_id BIGINT NULL,
  expires_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_audio_upload_session_upload_id (upload_id),
  KEY idx_audio_upload_session_user_time (user_id, created_at),
  CONSTRAINT fk_audio_upload_session_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  CONSTRAINT fk_audio_upload_session_audio FOREIGN KEY (merged_audio_id) REFERENCES audio_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audio_upload_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  upload_session_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  chunk_size_bytes BIGINT NOT NULL DEFAULT 0,
  chunk_sha256 CHAR(64) NULL,
  storage_path VARCHAR(1024) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_audio_upload_chunk_unique (upload_session_id, chunk_index),
  KEY idx_audio_upload_chunk_session (upload_session_id),
  CONSTRAINT fk_audio_upload_chunk_session FOREIGN KEY (upload_session_id) REFERENCES audio_upload_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- seed templates
INSERT INTO ops_message_template (template_code, title, content, channel, enabled)
SELECT 'WARN_HIGH_FOLLOWUP', '高风险关怀提醒', '我们检测到您近期情绪波动较大，建议优先休息并联系专业支持。', 'IN_APP', 1
WHERE NOT EXISTS (SELECT 1 FROM ops_message_template WHERE template_code='WARN_HIGH_FOLLOWUP');

INSERT INTO ops_message_template (template_code, title, content, channel, enabled)
SELECT 'WARN_MEDIUM_TRACK', '中风险跟踪提醒', '建议保持规律作息并持续记录情绪变化，如有不适请及时求助。', 'IN_APP', 1
WHERE NOT EXISTS (SELECT 1 FROM ops_message_template WHERE template_code='WARN_MEDIUM_TRACK');

-- seed a default warning rule
INSERT INTO warning_rule (
  rule_code, rule_name, description, enabled, priority,
  low_threshold, medium_threshold, high_threshold,
  emotion_combo_json, trend_window_days, trigger_count, suggest_template_code
)
SELECT
  'DEFAULT_RISK_RULE',
  '默认风险评分规则',
  '按心理评分阈值触发预警，支持后续配置叠加情绪组合规则。',
  1, 100,
  40.00, 60.00, 80.00,
  JSON_OBJECT('required', JSON_ARRAY('SAD'), 'optional', JSON_ARRAY('ANGRY')),
  7, 1, 'WARN_HIGH_FOLLOWUP'
WHERE NOT EXISTS (SELECT 1 FROM warning_rule WHERE rule_code='DEFAULT_RISK_RULE');

-- optional admin-side role extension (for finer-grained permission in Admin Console)
INSERT INTO auth_role (code, name)
SELECT 'OPERATOR', '运营人员'
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE code='OPERATOR');

INSERT INTO auth_role (code, name)
SELECT 'RESEARCHER', '研究人员'
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE code='RESEARCHER');

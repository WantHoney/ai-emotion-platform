ALTER TABLE articles
  ADD COLUMN recommend_reason VARCHAR(1024) NULL AFTER summary,
  ADD COLUMN fit_for VARCHAR(1024) NULL AFTER recommend_reason,
  ADD COLUMN highlights TEXT NULL AFTER fit_for,
  ADD COLUMN reading_minutes INT NULL AFTER highlights;

ALTER TABLE books
  ADD COLUMN category VARCHAR(64) NULL AFTER description,
  ADD COLUMN recommend_reason VARCHAR(1024) NULL AFTER category,
  ADD COLUMN fit_for VARCHAR(1024) NULL AFTER recommend_reason,
  ADD COLUMN highlights TEXT NULL AFTER fit_for;

CREATE TABLE IF NOT EXISTS content_daily_schedule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_date DATE NOT NULL,
  theme_key VARCHAR(64) NOT NULL,
  theme_title VARCHAR(255) NOT NULL,
  theme_subtitle VARCHAR(1024) NULL,
  quote_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_content_daily_schedule_date (schedule_date),
  KEY idx_content_daily_schedule_status_date (status, schedule_date),
  CONSTRAINT fk_content_daily_schedule_quote
    FOREIGN KEY (quote_id) REFERENCES quotes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS content_daily_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_id BIGINT NOT NULL,
  content_type VARCHAR(32) NOT NULL,
  content_id BIGINT NOT NULL,
  slot_role VARCHAR(32) NOT NULL DEFAULT 'SECONDARY',
  sort_order INT NOT NULL DEFAULT 100,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_content_daily_item_schedule (schedule_id, content_type, slot_role, sort_order),
  CONSTRAINT fk_content_daily_item_schedule
    FOREIGN KEY (schedule_id) REFERENCES content_daily_schedule (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_content_history (
  user_id BIGINT NOT NULL,
  content_type VARCHAR(32) NOT NULL,
  content_id BIGINT NOT NULL,
  last_viewed_at DATETIME NULL,
  last_outbound_at DATETIME NULL,
  view_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, content_type, content_id),
  KEY idx_user_content_history_recent (user_id, last_viewed_at, last_outbound_at),
  CONSTRAINT fk_user_content_history_user
    FOREIGN KEY (user_id) REFERENCES auth_user (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_articles_category_enabled_sort ON articles(category, is_active, is_enabled, is_recommended, sort_order);
CREATE INDEX idx_books_category_enabled_sort ON books(category, is_active, is_enabled, is_recommended, sort_order);

UPDATE articles
SET recommend_reason = CASE seed_key
    WHEN 'seed_article_stress_001' THEN '适合在压力上来时先稳定身心，再决定下一步。'
    WHEN 'seed_article_sleep_001' THEN '适合想先从睡前节律和环境入手修复睡眠的人。'
    WHEN 'seed_article_anxiety_001' THEN '适合先识别焦虑信号、减少“我是不是太敏感了”自责的人。'
    WHEN 'seed_article_emotion_001' THEN '适合在情绪很乱时，先把感受拆开看清楚。'
    WHEN 'seed_article_help_001' THEN '适合不知道什么时候该求助、该找谁求助的人。'
    WHEN 'seed_article_communication_001' THEN '适合想把支持关系重新接回生活里的人。'
    ELSE recommend_reason
  END,
  fit_for = CASE seed_key
    WHEN 'seed_article_stress_001' THEN '适合刚被任务、考试或人际压力压住，想先稳住节奏的人。'
    WHEN 'seed_article_sleep_001' THEN '适合经常晚睡、难入睡、醒后不解乏的人。'
    WHEN 'seed_article_anxiety_001' THEN '适合最近总担心、反复想、身体也跟着紧起来的人。'
    WHEN 'seed_article_emotion_001' THEN '适合说不清自己到底是烦、委屈、怕还是累的人。'
    WHEN 'seed_article_help_001' THEN '适合情绪已经影响学习工作，或者需要立即支持的人。'
    WHEN 'seed_article_communication_001' THEN '适合想重新开口、重新连上支持系统的人。'
    ELSE fit_for
  END,
  highlights = CASE seed_key
    WHEN 'seed_article_stress_001' THEN '先降噪，再整理，再决定下一步' '\n' '把注意力拉回身体和当下'
    WHEN 'seed_article_sleep_001' THEN '从作息节律开始' '\n' '优先清理光线、咖啡因和午睡长度'
    WHEN 'seed_article_anxiety_001' THEN '识别常见焦虑表现' '\n' '知道什么时候该尽快求助'
    WHEN 'seed_article_emotion_001' THEN '把复杂情绪拆开理解' '\n' '用更具体的入口应对难受'
    WHEN 'seed_article_help_001' THEN '明确 12356 等求助入口' '\n' '减少“我是不是太夸张了”的犹豫'
    WHEN 'seed_article_communication_001' THEN '看见连接本身的保护作用' '\n' '把求助和沟通变成可执行动作'
    ELSE highlights
  END,
  reading_minutes = CASE seed_key
    WHEN 'seed_article_stress_001' THEN 5
    WHEN 'seed_article_sleep_001' THEN 6
    WHEN 'seed_article_anxiety_001' THEN 5
    WHEN 'seed_article_emotion_001' THEN 6
    WHEN 'seed_article_help_001' THEN 4
    WHEN 'seed_article_communication_001' THEN 5
    ELSE reading_minutes
  END
WHERE seed_key IS NOT NULL;

UPDATE books
SET category = CASE seed_key
    WHEN 'seed_book_toad_001' THEN 'emotion'
    WHEN 'seed_book_talk_001' THEN 'help-seeking'
    WHEN 'seed_book_courage_001' THEN 'communication'
    WHEN 'seed_book_firstaid_001' THEN 'stress'
    WHEN 'seed_book_nvc_001' THEN 'communication'
    WHEN 'seed_book_inferiority_001' THEN 'anxiety'
    ELSE category
  END,
  recommend_reason = CASE seed_key
    WHEN 'seed_book_toad_001' THEN '用寓言故事把心理咨询的变化过程讲得很柔和，适合做进入心理世界的第一本。'
    WHEN 'seed_book_talk_001' THEN '能帮助你降低对心理咨询的陌生感，也更容易理解“聊一聊”为什么有帮助。'
    WHEN 'seed_book_courage_001' THEN '如果你总被关系里的期待和评价拖住，这本书很适合拿来重新整理边界。'
    WHEN 'seed_book_firstaid_001' THEN '它会把很多常见的情绪伤口说清楚，并给出很能落地的处理办法。'
    WHEN 'seed_book_nvc_001' THEN '适合想把“我很难开口”“一开口就容易吵起来”慢慢练顺的人。'
    WHEN 'seed_book_inferiority_001' THEN '适合愿意做更长期自我理解和关系整理的阅读。'
    ELSE recommend_reason
  END,
  fit_for = CASE seed_key
    WHEN 'seed_book_toad_001' THEN '适合第一次靠近心理咨询、想理解改变是怎么发生的人。'
    WHEN 'seed_book_talk_001' THEN '适合对咨询好奇、也对求助有犹豫的人。'
    WHEN 'seed_book_courage_001' THEN '适合容易被评价影响、总在关系里失去自己的读者。'
    WHEN 'seed_book_firstaid_001' THEN '适合想把自责、孤独、反刍这些高频情绪问题处理得更具体的人。'
    WHEN 'seed_book_nvc_001' THEN '适合想练习表达感受、需求和边界的人。'
    WHEN 'seed_book_inferiority_001' THEN '适合想从底层理解自卑感和目标感的人。'
    ELSE fit_for
  END,
  highlights = CASE seed_key
    WHEN 'seed_book_toad_001' THEN '咨询关系如何帮助改变' '\n' '用故事理解自我觉察'
    WHEN 'seed_book_talk_001' THEN '降低对咨询的陌生感' '\n' '看见求助背后的现实困境'
    WHEN 'seed_book_courage_001' THEN '课题分离' '\n' '关系中的自我价值'
    WHEN 'seed_book_firstaid_001' THEN '把情绪问题当作可处理的伤口' '\n' '给出具体修复动作'
    WHEN 'seed_book_nvc_001' THEN '表达感受与需求' '\n' '减少沟通中的攻击与防御'
    WHEN 'seed_book_inferiority_001' THEN '理解自卑感' '\n' '重建长期目标感'
    ELSE highlights
  END
WHERE seed_key IS NOT NULL;

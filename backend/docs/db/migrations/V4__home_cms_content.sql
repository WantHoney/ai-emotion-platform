CREATE TABLE IF NOT EXISTS banners (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(128) NOT NULL,
  image_url VARCHAR(512) NOT NULL,
  link_url VARCHAR(512) NULL,
  sort_order INT NOT NULL DEFAULT 100,
  is_recommended TINYINT(1) NOT NULL DEFAULT 0,
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  starts_at DATETIME NULL,
  ends_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_banner_enabled_sort (is_enabled, is_recommended, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quotes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  content VARCHAR(512) NOT NULL,
  author VARCHAR(64) NULL,
  sort_order INT NOT NULL DEFAULT 100,
  is_recommended TINYINT(1) NOT NULL DEFAULT 0,
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_quote_enabled_sort (is_enabled, is_recommended, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS articles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  cover_image_url VARCHAR(512) NULL,
  summary VARCHAR(1024) NULL,
  content_url VARCHAR(512) NULL,
  sort_order INT NOT NULL DEFAULT 100,
  is_recommended TINYINT(1) NOT NULL DEFAULT 0,
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  published_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_article_enabled_sort (is_enabled, is_recommended, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS books (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  author VARCHAR(128) NULL,
  cover_image_url VARCHAR(512) NULL,
  description VARCHAR(1024) NULL,
  purchase_url VARCHAR(512) NULL,
  sort_order INT NOT NULL DEFAULT 100,
  is_recommended TINYINT(1) NOT NULL DEFAULT 0,
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_book_enabled_sort (is_enabled, is_recommended, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS psy_centers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  city_code VARCHAR(32) NOT NULL,
  city_name VARCHAR(64) NOT NULL,
  district VARCHAR(64) NULL,
  address VARCHAR(255) NOT NULL,
  phone VARCHAR(64) NULL,
  latitude DECIMAL(10, 6) NULL,
  longitude DECIMAL(10, 6) NULL,
  is_recommended TINYINT(1) NOT NULL DEFAULT 0,
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_psy_city_enabled (city_code, is_enabled),
  KEY idx_psy_recommend (is_recommended, is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS content_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_type VARCHAR(32) NOT NULL,
  content_type VARCHAR(32) NOT NULL,
  content_id BIGINT NULL,
  event_date DATE NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_content_events_type_date (event_type, event_date),
  KEY idx_content_events_content (content_type, content_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

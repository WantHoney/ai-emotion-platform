-- V9__cms_seed_source_metadata.sql
-- Purpose:
-- 1) Add seed metadata and explicit active state to CMS seedable tables
-- 2) Promote articles.source_url as the canonical external link field
-- 3) Add provenance fields for psychology centers
-- 4) Keep migration idempotent for manual execution

SET @db := DATABASE();

-- --------------------------------
-- quotes
-- --------------------------------
SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='quotes' AND COLUMN_NAME='seed_key'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE quotes ADD COLUMN seed_key VARCHAR(128) NULL',
  'SELECT ''skip quotes.seed_key'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='quotes' AND COLUMN_NAME='data_source'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE quotes ADD COLUMN data_source VARCHAR(16) NOT NULL DEFAULT ''manual''',
  'SELECT ''skip quotes.data_source'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='quotes' AND COLUMN_NAME='is_active'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE quotes ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1',
  'SELECT ''skip quotes.is_active'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='quotes' AND INDEX_NAME='uk_quotes_seed_key'
);
SET @ddl := IF(@idx_exists=0,
  'ALTER TABLE quotes ADD UNIQUE INDEX uk_quotes_seed_key (seed_key)',
  'SELECT ''skip uk_quotes_seed_key'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='quotes' AND INDEX_NAME='idx_quote_active_enabled_sort'
);
SET @ddl := IF(@idx_exists=0,
  'ALTER TABLE quotes ADD INDEX idx_quote_active_enabled_sort (is_active, is_enabled, is_recommended, sort_order)',
  'SELECT ''skip idx_quote_active_enabled_sort'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --------------------------------
-- articles
-- --------------------------------
SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND COLUMN_NAME='seed_key'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE articles ADD COLUMN seed_key VARCHAR(128) NULL',
  'SELECT ''skip articles.seed_key'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND COLUMN_NAME='data_source'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE articles ADD COLUMN data_source VARCHAR(16) NOT NULL DEFAULT ''manual''',
  'SELECT ''skip articles.data_source'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND COLUMN_NAME='is_active'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE articles ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1',
  'SELECT ''skip articles.is_active'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND COLUMN_NAME='category'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE articles ADD COLUMN category VARCHAR(64) NULL',
  'SELECT ''skip articles.category'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND COLUMN_NAME='source_name'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE articles ADD COLUMN source_name VARCHAR(128) NULL',
  'SELECT ''skip articles.source_name'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND COLUMN_NAME='source_url'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE articles ADD COLUMN source_url VARCHAR(512) NULL',
  'SELECT ''skip articles.source_url'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_source := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND COLUMN_NAME='source_url'
);
SET @has_content := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND COLUMN_NAME='content_url'
);
-- Safe-update friendly backfill: keep the primary-key predicate so MySQL Workbench manual execution can pass.
SET @ddl := IF(@has_source>0 AND @has_content>0,
  'UPDATE articles SET source_url=content_url WHERE id > 0 AND (source_url IS NULL OR source_url='''' ) AND content_url IS NOT NULL AND content_url<>''''',
  'SELECT ''skip articles.source_url backfill'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND COLUMN_NAME='is_external'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE articles ADD COLUMN is_external TINYINT(1) NULL DEFAULT 1',
  'SELECT ''skip articles.is_external'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND COLUMN_NAME='difficulty_tag'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE articles ADD COLUMN difficulty_tag VARCHAR(32) NULL',
  'SELECT ''skip articles.difficulty_tag'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND INDEX_NAME='uk_articles_seed_key'
);
SET @ddl := IF(@idx_exists=0,
  'ALTER TABLE articles ADD UNIQUE INDEX uk_articles_seed_key (seed_key)',
  'SELECT ''skip uk_articles_seed_key'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='articles' AND INDEX_NAME='idx_article_active_enabled_sort'
);
SET @ddl := IF(@idx_exists=0,
  'ALTER TABLE articles ADD INDEX idx_article_active_enabled_sort (is_active, is_enabled, is_recommended, sort_order, published_at)',
  'SELECT ''skip idx_article_active_enabled_sort'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --------------------------------
-- books
-- --------------------------------
SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='books' AND COLUMN_NAME='seed_key'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE books ADD COLUMN seed_key VARCHAR(128) NULL',
  'SELECT ''skip books.seed_key'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='books' AND COLUMN_NAME='data_source'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE books ADD COLUMN data_source VARCHAR(16) NOT NULL DEFAULT ''manual''',
  'SELECT ''skip books.data_source'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='books' AND COLUMN_NAME='is_active'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE books ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1',
  'SELECT ''skip books.is_active'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='books' AND INDEX_NAME='uk_books_seed_key'
);
SET @ddl := IF(@idx_exists=0,
  'ALTER TABLE books ADD UNIQUE INDEX uk_books_seed_key (seed_key)',
  'SELECT ''skip uk_books_seed_key'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='books' AND INDEX_NAME='idx_book_active_enabled_sort'
);
SET @ddl := IF(@idx_exists=0,
  'ALTER TABLE books ADD INDEX idx_book_active_enabled_sort (is_active, is_enabled, is_recommended, sort_order)',
  'SELECT ''skip idx_book_active_enabled_sort'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --------------------------------
-- psy_centers
-- --------------------------------
SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='psy_centers' AND COLUMN_NAME='seed_key'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE psy_centers ADD COLUMN seed_key VARCHAR(128) NULL',
  'SELECT ''skip psy_centers.seed_key'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='psy_centers' AND COLUMN_NAME='data_source'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE psy_centers ADD COLUMN data_source VARCHAR(16) NOT NULL DEFAULT ''manual''',
  'SELECT ''skip psy_centers.data_source'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='psy_centers' AND COLUMN_NAME='is_active'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE psy_centers ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1',
  'SELECT ''skip psy_centers.is_active'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='psy_centers' AND COLUMN_NAME='source_name'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE psy_centers ADD COLUMN source_name VARCHAR(128) NULL',
  'SELECT ''skip psy_centers.source_name'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='psy_centers' AND COLUMN_NAME='source_url'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE psy_centers ADD COLUMN source_url VARCHAR(512) NULL',
  'SELECT ''skip psy_centers.source_url'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='psy_centers' AND COLUMN_NAME='source_level'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE psy_centers ADD COLUMN source_level VARCHAR(32) NULL',
  'SELECT ''skip psy_centers.source_level'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='psy_centers' AND INDEX_NAME='uk_psy_centers_seed_key'
);
SET @ddl := IF(@idx_exists=0,
  'ALTER TABLE psy_centers ADD UNIQUE INDEX uk_psy_centers_seed_key (seed_key)',
  'SELECT ''skip uk_psy_centers_seed_key'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='psy_centers' AND INDEX_NAME='idx_psy_city_active_enabled'
);
SET @ddl := IF(@idx_exists=0,
  'ALTER TABLE psy_centers ADD INDEX idx_psy_city_active_enabled (city_code, is_active, is_enabled, is_recommended)',
  'SELECT ''skip idx_psy_city_active_enabled'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='psy_centers' AND INDEX_NAME='idx_psy_active_enabled'
);
SET @ddl := IF(@idx_exists=0,
  'ALTER TABLE psy_centers ADD INDEX idx_psy_active_enabled (is_active, is_enabled, is_recommended)',
  'SELECT ''skip idx_psy_active_enabled'''
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

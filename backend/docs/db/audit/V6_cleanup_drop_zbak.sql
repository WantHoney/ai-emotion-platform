-- V6_cleanup_drop_zbak.sql
-- Execute after verification period.
-- Scope: drop archived backup tables created by phase-1 rename.

SET @schema_name := DATABASE();

SELECT table_name
FROM information_schema.tables
WHERE table_schema = @schema_name
  AND table_name LIKE 'zbak_20260216_%'
ORDER BY table_name;

SET @drop_sql := (
  SELECT IFNULL(
    GROUP_CONCAT(CONCAT('DROP TABLE IF EXISTS `', table_name, '`') SEPARATOR '; '),
    'SELECT ''NO_ZBAK_TABLES'''
  )
  FROM information_schema.tables
  WHERE table_schema = @schema_name
    AND table_name LIKE 'zbak_20260216_%'
);

PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = @schema_name
  AND table_name LIKE 'zbak_20260216_%'
ORDER BY table_name;

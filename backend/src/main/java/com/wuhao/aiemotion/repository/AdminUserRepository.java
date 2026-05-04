package com.wuhao.aiemotion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AdminUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countUsers(String keyword, String role, String status, String accountType) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM auth_user u
                LEFT JOIN auth_user_role ur ON ur.user_id = u.id
                LEFT JOIN auth_role r ON r.id = ur.role_id
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        appendFilters(keyword, role, status, accountType, sql, args);
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public List<Map<String, Object>> listUsers(int offset,
                                               int pageSize,
                                               String keyword,
                                               String role,
                                               String status,
                                               String accountType) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id,
                       u.username,
                       u.status,
                       COALESCE(r.code, 'USER') AS role_code,
                       CASE WHEN %s THEN 1 ELSE 0 END AS is_test_account,
                       u.created_at,
                       u.updated_at,
                       u.last_login_at,
                       COALESCE(task_stats.task_count, 0) AS task_count,
                       COALESCE(report_stats.report_count, 0) AS report_count,
                       COALESCE(warning_stats.warning_count, 0) AS warning_count,
                       COALESCE(session_stats.active_session_count, 0) AS active_session_count
                FROM auth_user u
                LEFT JOIN auth_user_role ur ON ur.user_id = u.id
                LEFT JOIN auth_role r ON r.id = ur.role_id
                LEFT JOIN (
                    SELECT af.user_id, COUNT(*) AS task_count
                    FROM analysis_task t
                    JOIN audio_file af ON af.id = t.audio_file_id
                    GROUP BY af.user_id
                ) task_stats ON task_stats.user_id = u.id
                LEFT JOIN (
                    SELECT af.user_id, COUNT(*) AS report_count
                    FROM report_resource rr
                    JOIN audio_file af ON af.id = rr.audio_id
                    WHERE rr.deleted_at IS NULL
                    GROUP BY af.user_id
                ) report_stats ON report_stats.user_id = u.id
                LEFT JOIN (
                    SELECT we.user_id, COUNT(*) AS warning_count
                    FROM warning_event we
                    WHERE we.user_id IS NOT NULL
                    GROUP BY we.user_id
                ) warning_stats ON warning_stats.user_id = u.id
                LEFT JOIN (
                    SELECT s.user_id, COUNT(*) AS active_session_count
                    FROM auth_session s
                    WHERE s.access_expire_at > NOW()
                    GROUP BY s.user_id
                ) session_stats ON session_stats.user_id = u.id
                WHERE 1 = 1
                """.formatted(testAccountPredicate("u")));
        List<Object> args = new ArrayList<>();
        appendFilters(keyword, role, status, accountType, sql, args);
        sql.append(" ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?");
        args.add(pageSize);
        args.add(offset);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Optional<Map<String, Object>> findUserSummaryById(long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT u.id,
                       u.username,
                       u.status,
                       COALESCE(r.code, 'USER') AS role_code,
                       CASE WHEN %s THEN 1 ELSE 0 END AS is_test_account,
                       u.created_at,
                       u.updated_at,
                       u.last_login_at,
                       COALESCE(task_stats.task_count, 0) AS task_count,
                       COALESCE(report_stats.report_count, 0) AS report_count,
                       COALESCE(warning_stats.warning_count, 0) AS warning_count,
                       COALESCE(session_stats.active_session_count, 0) AS active_session_count
                FROM auth_user u
                LEFT JOIN auth_user_role ur ON ur.user_id = u.id
                LEFT JOIN auth_role r ON r.id = ur.role_id
                LEFT JOIN (
                    SELECT af.user_id, COUNT(*) AS task_count
                    FROM analysis_task t
                    JOIN audio_file af ON af.id = t.audio_file_id
                    GROUP BY af.user_id
                ) task_stats ON task_stats.user_id = u.id
                LEFT JOIN (
                    SELECT af.user_id, COUNT(*) AS report_count
                    FROM report_resource rr
                    JOIN audio_file af ON af.id = rr.audio_id
                    WHERE rr.deleted_at IS NULL
                    GROUP BY af.user_id
                ) report_stats ON report_stats.user_id = u.id
                LEFT JOIN (
                    SELECT we.user_id, COUNT(*) AS warning_count
                    FROM warning_event we
                    WHERE we.user_id IS NOT NULL
                    GROUP BY we.user_id
                ) warning_stats ON warning_stats.user_id = u.id
                LEFT JOIN (
                    SELECT s.user_id, COUNT(*) AS active_session_count
                    FROM auth_session s
                    WHERE s.access_expire_at > NOW()
                    GROUP BY s.user_id
                ) session_stats ON session_stats.user_id = u.id
                WHERE u.id = ?
                LIMIT 1
                """.formatted(testAccountPredicate("u")), userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<Map<String, Object>> listRecentTasksByUser(long userId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT t.id,
                       t.audio_file_id,
                       af.original_name,
                       t.status,
                       t.trace_id,
                       rr.id AS report_id,
                       t.created_at,
                       t.updated_at
                FROM analysis_task t
                JOIN audio_file af ON af.id = t.audio_file_id
                LEFT JOIN report_resource rr ON rr.task_id = t.id AND rr.deleted_at IS NULL
                WHERE af.user_id = ?
                ORDER BY t.created_at DESC, t.id DESC
                LIMIT ?
                """, userId, safeLimit(limit));
    }

    public List<Map<String, Object>> listRecentReportsByUser(long userId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT rr.id,
                       rr.task_id,
                       rr.audio_id,
                       af.original_name,
                       rr.risk_level,
                       rr.overall_emotion,
                       rr.created_at
                FROM report_resource rr
                JOIN audio_file af ON af.id = rr.audio_id
                WHERE rr.deleted_at IS NULL
                  AND af.user_id = ?
                ORDER BY rr.created_at DESC, rr.id DESC
                LIMIT ?
                """, userId, safeLimit(limit));
    }

    public List<Map<String, Object>> listRecentWarningsByUser(long userId, int limit) {
        String sql;
        if (hasColumn("warning_event", "breached")) {
            sql = """
                    SELECT id,
                           task_id,
                           report_id,
                           risk_score,
                           risk_level,
                           top_emotion,
                           status,
                           breached,
                           created_at
                    FROM warning_event
                    WHERE user_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?
                    """;
        } else {
            sql = """
                    SELECT id,
                           task_id,
                           report_id,
                           risk_score,
                           risk_level,
                           top_emotion,
                           status,
                           0 AS breached,
                           created_at
                    FROM warning_event
                    WHERE user_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?
                    """;
        }
        return jdbcTemplate.queryForList(sql, userId, safeLimit(limit));
    }

    public int updateUserStatus(long userId, String status) {
        return jdbcTemplate.update(
                "UPDATE auth_user SET status = ?, updated_at = NOW() WHERE id = ?",
                status,
                userId
        );
    }

    public long countActiveSessions() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_session WHERE access_expire_at > NOW()",
                Long.class
        );
        return total == null ? 0L : total;
    }

    private void appendFilters(String keyword,
                               String role,
                               String status,
                               String accountType,
                               StringBuilder sql,
                               List<Object> args) {
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            sql.append(" AND (CAST(u.id AS CHAR) LIKE ? OR u.username LIKE ?)");
            args.add(like);
            args.add(like);
        }
        if (role != null && !role.isBlank()) {
            sql.append(" AND UPPER(COALESCE(r.code, 'USER')) = UPPER(?)");
            args.add(role.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND UPPER(u.status) = UPPER(?)");
            args.add(status.trim());
        }
        if ("REAL_ONLY".equalsIgnoreCase(accountType)) {
            sql.append(" AND NOT (").append(testAccountPredicate("u")).append(')');
        } else if ("TEST_ONLY".equalsIgnoreCase(accountType)) {
            sql.append(" AND ").append(testAccountPredicate("u"));
        }
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 20));
    }

    private String testAccountPredicate(String alias) {
        String prefix = alias == null || alias.isBlank() ? "" : alias.trim() + ".";
        return """
                LOWER(COALESCE(%susername, '')) REGEXP '^(codex_.*probe_.*|qa(user)?[0-9]+|chunk[0-9]+|u_(smoke|chunk|node)_[0-9]+|smokeu[0-9]+|privu[0-9]+|u[0-9]{4,}|ue[0-9a-f]{6,}|user[0-9]{5,}|[0-9]{1,4})$'
                """.formatted(prefix);
    }

    private boolean hasColumn(String tableName, String columnName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND COLUMN_NAME = ?
                    """,
                    Integer.class,
                    tableName,
                    columnName
            );
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}

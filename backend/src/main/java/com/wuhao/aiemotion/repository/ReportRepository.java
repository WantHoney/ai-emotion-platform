package com.wuhao.aiemotion.repository;

import com.wuhao.aiemotion.domain.ReportResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<ReportResource> ROW_MAPPER = (rs, rowNum) -> new ReportResource(
            rs.getLong("id"),
            rs.getLong("task_id"),
            rs.getLong("audio_id"),
            rs.getString("report_json"),
            rs.getString("risk_level"),
            rs.getString("overall_emotion"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime()
    );

    public void upsert(long taskId, long audioId, String reportJson, String riskLevel, String overallEmotion) {
        jdbcTemplate.update("""
                INSERT INTO report_resource (task_id, audio_id, report_json, risk_level, overall_emotion)
                VALUES (?, ?, CAST(? AS JSON), ?, ?)
                ON DUPLICATE KEY UPDATE
                    report_json = VALUES(report_json),
                    risk_level = VALUES(risk_level),
                    overall_emotion = VALUES(overall_emotion),
                    deleted_at = NULL,
                    created_at = CURRENT_TIMESTAMP
                """, taskId, audioId, reportJson, riskLevel, overallEmotion);
    }

    public long count(String riskLevel, String emotion, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM report_resource rr JOIN audio_file af ON af.id=rr.audio_id WHERE rr.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        appendFilters(riskLevel, emotion, keyword, sql, args);
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public long countByUser(long userId, String riskLevel, String emotion, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM report_resource rr
                JOIN audio_file af ON af.id=rr.audio_id
                WHERE rr.deleted_at IS NULL
                  AND af.user_id=?
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        appendFilters(riskLevel, emotion, keyword, sql, args);
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public List<ReportResource> page(String riskLevel,
                                     String emotion,
                                     String keyword,
                                     int offset,
                                     int size,
                                     String sortBy,
                                     String sortOrder) {
        StringBuilder sql = new StringBuilder("SELECT rr.* FROM report_resource rr JOIN audio_file af ON af.id=rr.audio_id WHERE rr.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        appendFilters(riskLevel, emotion, keyword, sql, args);
        sql.append(" ORDER BY ").append(resolveReportOrderBy(sortBy, sortOrder)).append(" LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public List<ReportResource> pageByUser(long userId,
                                           String riskLevel,
                                           String emotion,
                                           String keyword,
                                           int offset,
                                           int size,
                                           String sortBy,
                                           String sortOrder) {
        StringBuilder sql = new StringBuilder("""
                SELECT rr.*
                FROM report_resource rr
                JOIN audio_file af ON af.id=rr.audio_id
                WHERE rr.deleted_at IS NULL
                  AND af.user_id=?
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        appendFilters(riskLevel, emotion, keyword, sql, args);
        sql.append(" ORDER BY ").append(resolveReportOrderBy(sortBy, sortOrder)).append(" LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    private void appendFilters(String riskLevel, String emotion, String keyword, StringBuilder sql, List<Object> args) {
        String normalizedRiskLevel = normalizeFilterValue(riskLevel);
        if (normalizedRiskLevel != null) {
            sql.append(" AND UPPER(rr.risk_level)=UPPER(?)");
            args.add(normalizedRiskLevel);
        }
        String normalizedEmotion = normalizeFilterValue(emotion);
        if (normalizedEmotion != null) {
            sql.append(" AND UPPER(rr.overall_emotion)=UPPER(?)");
            args.add(normalizedEmotion);
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            sql.append(" AND (CAST(rr.id AS CHAR) LIKE ? OR CAST(rr.task_id AS CHAR) LIKE ? OR af.original_name LIKE ? OR rr.overall_emotion LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
    }

    private String resolveReportOrderBy(String sortBy, String sortOrder) {
        String column = switch (sortBy == null ? "" : sortBy.trim()) {
            case "createdAt" -> "rr.created_at";
            case "riskLevel" -> "rr.risk_level";
            case "overall" -> "rr.overall_emotion";
            default -> "rr.created_at";
        };
        String direction = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        return column + " " + direction;
    }

    static String normalizeFilterValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String upper = normalized.toUpperCase();
        if ("ALL".equals(upper) || "ANY".equals(upper) || "*".equals(normalized) || "全部".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    public Optional<ReportResource> findById(long reportId) {
        List<ReportResource> list = jdbcTemplate.query("SELECT * FROM report_resource WHERE id=? AND deleted_at IS NULL", ROW_MAPPER, reportId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<ReportResource> findByIdForUser(long reportId, long userId) {
        List<ReportResource> list = jdbcTemplate.query(
                """
                SELECT rr.*
                FROM report_resource rr
                JOIN audio_file af ON af.id=rr.audio_id
                WHERE rr.id=? AND rr.deleted_at IS NULL AND af.user_id=?
                LIMIT 1
                """,
                ROW_MAPPER,
                reportId,
                userId
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Long> findIdByTaskId(long taskId) {
        List<Long> rows = jdbcTemplate.query(
                "SELECT id FROM report_resource WHERE task_id=? AND deleted_at IS NULL LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                taskId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public int softDelete(long reportId) {
        return jdbcTemplate.update("UPDATE report_resource SET deleted_at = NOW() WHERE id=? AND deleted_at IS NULL", reportId);
    }

    public void softDeleteByAudioId(long audioId) {
        jdbcTemplate.update("UPDATE report_resource SET deleted_at = NOW() WHERE audio_id=? AND deleted_at IS NULL", audioId);
    }

    public List<Map<String, Object>> listUserDailyTrend(long userId, int days) {
        int safeDays = Math.min(180, Math.max(1, days));
        return jdbcTemplate.queryForList(
                """
                SELECT DATE(rr.created_at) AS stat_date,
                       COUNT(*) AS report_count,
                       AVG(
                         CASE
                           WHEN (
                             CASE
                               WHEN CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.riskAssessment.risk_score')), '0') AS DECIMAL(10,4)) > 0
                                 THEN CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.riskAssessment.risk_score')), '0') AS DECIMAL(10,4))
                               ELSE CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.analysis_result.risk_assessment.risk_score')), '0') AS DECIMAL(10,4))
                             END
                           ) <= 1
                             THEN (
                               CASE
                                 WHEN CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.riskAssessment.risk_score')), '0') AS DECIMAL(10,4)) > 0
                                   THEN CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.riskAssessment.risk_score')), '0') AS DECIMAL(10,4))
                                 ELSE CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.analysis_result.risk_assessment.risk_score')), '0') AS DECIMAL(10,4))
                               END
                             ) * 100
                           ELSE (
                             CASE
                               WHEN CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.riskAssessment.risk_score')), '0') AS DECIMAL(10,4)) > 0
                                 THEN CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.riskAssessment.risk_score')), '0') AS DECIMAL(10,4))
                               ELSE CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.analysis_result.risk_assessment.risk_score')), '0') AS DECIMAL(10,4))
                             END
                           )
                         END
                       ) AS avg_risk_score,
                       SUM(CASE WHEN UPPER(COALESCE(rr.risk_level, 'LOW'))='LOW' THEN 1 ELSE 0 END) AS low_count,
                       SUM(CASE WHEN UPPER(COALESCE(rr.risk_level, 'LOW'))='MEDIUM' THEN 1 ELSE 0 END) AS medium_count,
                       SUM(CASE WHEN UPPER(COALESCE(rr.risk_level, 'LOW'))='HIGH' THEN 1 ELSE 0 END) AS high_count
                FROM report_resource rr
                JOIN audio_file af ON af.id=rr.audio_id
                WHERE rr.deleted_at IS NULL
                  AND af.user_id=?
                  AND rr.created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY DATE(rr.created_at)
                ORDER BY stat_date ASC
                """,
                userId,
                safeDays
        );
    }
}

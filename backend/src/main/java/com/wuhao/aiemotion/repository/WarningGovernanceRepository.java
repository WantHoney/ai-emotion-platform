package com.wuhao.aiemotion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class WarningGovernanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public WarningGovernanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listRules() {
        return jdbcTemplate.queryForList("SELECT * FROM warning_rule ORDER BY priority ASC, id DESC");
    }

    public Optional<Map<String, Object>> findRuleById(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM warning_rule WHERE id=? LIMIT 1", id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<Map<String, Object>> findTopEnabledRule() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM warning_rule WHERE enabled=1 ORDER BY priority ASC, id ASC LIMIT 1"
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public long createRule(String ruleCode,
                           String ruleName,
                           String description,
                           boolean enabled,
                           int priority,
                           double lowThreshold,
                           double mediumThreshold,
                           double highThreshold,
                           String emotionComboJson,
                           int trendWindowDays,
                           int triggerCount,
                           String suggestTemplateCode,
                           Long operatorId) {
        String sql = """
                INSERT INTO warning_rule
                (rule_code, rule_name, description, enabled, priority,
                 low_threshold, medium_threshold, high_threshold,
                 emotion_combo_json, trend_window_days, trigger_count, suggest_template_code, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, ruleCode);
            ps.setString(2, ruleName);
            ps.setString(3, description);
            ps.setBoolean(4, enabled);
            ps.setInt(5, priority);
            ps.setDouble(6, lowThreshold);
            ps.setDouble(7, mediumThreshold);
            ps.setDouble(8, highThreshold);
            ps.setString(9, emotionComboJson);
            ps.setInt(10, trendWindowDays);
            ps.setInt(11, triggerCount);
            ps.setString(12, suggestTemplateCode);
            ps.setObject(13, operatorId);
            return ps;
        }, keyHolder);
        Number key = Objects.requireNonNull(keyHolder.getKey(), "createRule generated key is null");
        return key.longValue();
    }

    public int updateRule(long id,
                          String ruleName,
                          String description,
                          boolean enabled,
                          int priority,
                          double lowThreshold,
                          double mediumThreshold,
                          double highThreshold,
                          String emotionComboJson,
                          int trendWindowDays,
                          int triggerCount,
                          String suggestTemplateCode) {
        return jdbcTemplate.update(
                """
                UPDATE warning_rule
                SET rule_name=?, description=?, enabled=?, priority=?,
                    low_threshold=?, medium_threshold=?, high_threshold=?,
                    emotion_combo_json=CAST(? AS JSON), trend_window_days=?, trigger_count=?,
                    suggest_template_code=?, updated_at=NOW()
                WHERE id=?
                """,
                ruleName,
                description,
                enabled,
                priority,
                lowThreshold,
                mediumThreshold,
                highThreshold,
                emotionComboJson,
                trendWindowDays,
                triggerCount,
                suggestTemplateCode,
                id
        );
    }

    public int toggleRule(long id, boolean enabled) {
        return jdbcTemplate.update("UPDATE warning_rule SET enabled=?, updated_at=NOW() WHERE id=?", enabled, id);
    }

    public long countWarnings(String status, String riskLevel) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM warning_event WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendWarningFilters(status, riskLevel, sql, args);
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public List<Map<String, Object>> listWarnings(int offset, int pageSize, String status, String riskLevel) {
        StringBuilder sql = new StringBuilder("SELECT * FROM warning_event WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendWarningFilters(status, riskLevel, sql, args);
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        args.add(pageSize);
        args.add(offset);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Optional<Map<String, Object>> findWarningById(long warningId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM warning_event WHERE id=? LIMIT 1", warningId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<Long> findOpenWarningByTaskId(long taskId) {
        List<Long> rows = jdbcTemplate.query(
                """
                SELECT id
                FROM warning_event
                WHERE task_id=?
                  AND status IN ('NEW','ACKED','FOLLOWING')
                ORDER BY id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getLong("id"),
                taskId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public long createWarningEvent(Long reportId,
                                   long taskId,
                                   Long userId,
                                   String userMask,
                                   double riskScore,
                                   String riskLevel,
                                   String topEmotion,
                                   Long triggerRuleId,
                                   String triggerSnapshotJson) {
        String sql = """
                INSERT INTO warning_event
                (report_id, task_id, user_id, user_mask, risk_score, risk_level, top_emotion, trigger_rule_id, trigger_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, reportId);
            ps.setLong(2, taskId);
            ps.setObject(3, userId);
            ps.setString(4, userMask);
            ps.setDouble(5, riskScore);
            ps.setString(6, riskLevel);
            ps.setString(7, topEmotion);
            ps.setObject(8, triggerRuleId);
            ps.setString(9, triggerSnapshotJson);
            return ps;
        }, keyHolder);
        Number key = Objects.requireNonNull(keyHolder.getKey(), "createWarningEvent generated key is null");
        return key.longValue();
    }

    public void createWarningAction(long warningEventId,
                                    String actionType,
                                    String actionNote,
                                    String templateCode,
                                    Long operatorId) {
        jdbcTemplate.update(
                """
                INSERT INTO warning_action_log
                (warning_event_id, action_type, action_note, template_code, operator_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                warningEventId,
                actionType,
                actionNote,
                templateCode,
                operatorId
        );
    }

    public int updateWarningStatus(long warningEventId, String nextStatus) {
        if ("RESOLVED".equalsIgnoreCase(nextStatus)) {
            return jdbcTemplate.update(
                    "UPDATE warning_event SET status=?, resolved_at=NOW(), updated_at=NOW() WHERE id=?",
                    nextStatus.toUpperCase(),
                    warningEventId
            );
        }
        return jdbcTemplate.update(
                "UPDATE warning_event SET status=?, updated_at=NOW() WHERE id=?",
                nextStatus.toUpperCase(),
                warningEventId
        );
    }

    public List<Map<String, Object>> listDailySummary(int days) {
        return jdbcTemplate.queryForList(
                """
                SELECT *
                FROM analytics_daily_summary
                WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                ORDER BY stat_date DESC
                """,
                days
        );
    }

    public List<Map<String, Object>> aggregateFallbackDaily(int days) {
        return jdbcTemplate.queryForList(
                """
                SELECT d.stat_date,
                       COALESCE(u.dau, 0) AS dau,
                       COALESCE(a.upload_count, 0) AS upload_count,
                       COALESCE(r.report_count, 0) AS report_count,
                       COALESCE(w.warning_count, 0) AS warning_count
                FROM (
                  SELECT DATE_SUB(CURDATE(), INTERVAL seq.n DAY) AS stat_date
                  FROM (
                    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
                    SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL
                    SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL
                    SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL
                    SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL
                    SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29
                  ) seq
                  WHERE seq.n < ?
                ) d
                LEFT JOIN (
                  SELECT DATE(created_at) AS stat_date, COUNT(DISTINCT user_id) AS dau
                  FROM audio_file
                  WHERE user_id IS NOT NULL
                  GROUP BY DATE(created_at)
                ) u ON u.stat_date=d.stat_date
                LEFT JOIN (
                  SELECT DATE(created_at) AS stat_date, COUNT(*) AS upload_count
                  FROM audio_file
                  GROUP BY DATE(created_at)
                ) a ON a.stat_date=d.stat_date
                LEFT JOIN (
                  SELECT DATE(created_at) AS stat_date, COUNT(*) AS report_count
                  FROM report_resource
                  WHERE deleted_at IS NULL
                  GROUP BY DATE(created_at)
                ) r ON r.stat_date=d.stat_date
                LEFT JOIN (
                  SELECT DATE(created_at) AS stat_date, COUNT(*) AS warning_count
                  FROM warning_event
                  GROUP BY DATE(created_at)
                ) w ON w.stat_date=d.stat_date
                ORDER BY d.stat_date DESC
                """,
                days
        );
    }

    private void appendWarningFilters(String status, String riskLevel, StringBuilder sql, List<Object> args) {
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            args.add(status.trim().toUpperCase());
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            sql.append(" AND risk_level=?");
            args.add(riskLevel.trim().toUpperCase());
        }
    }
}

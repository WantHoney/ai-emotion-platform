package com.wuhao.aiemotion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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

    public List<Map<String, Object>> listEnabledRules() {
        return jdbcTemplate.queryForList("SELECT * FROM warning_rule WHERE enabled=1 ORDER BY priority ASC, id ASC");
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
                           int slaLowMinutes,
                           int slaMediumMinutes,
                           int slaHighMinutes,
                           Long operatorId) {
        boolean hasSlaColumns = hasColumn("warning_rule", "sla_low_minutes")
                && hasColumn("warning_rule", "sla_medium_minutes")
                && hasColumn("warning_rule", "sla_high_minutes");
        String sql = hasSlaColumns
                ? """
                INSERT INTO warning_rule
                (rule_code, rule_name, description, enabled, priority,
                 low_threshold, medium_threshold, high_threshold,
                 emotion_combo_json, trend_window_days, trigger_count, suggest_template_code,
                 sla_low_minutes, sla_medium_minutes, sla_high_minutes, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?, ?, ?)
                """
                : """
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
            if (hasSlaColumns) {
                ps.setInt(13, slaLowMinutes);
                ps.setInt(14, slaMediumMinutes);
                ps.setInt(15, slaHighMinutes);
                ps.setObject(16, operatorId);
            } else {
                ps.setObject(13, operatorId);
            }
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
                          String suggestTemplateCode,
                          int slaLowMinutes,
                          int slaMediumMinutes,
                          int slaHighMinutes) {
        boolean hasSlaColumns = hasColumn("warning_rule", "sla_low_minutes")
                && hasColumn("warning_rule", "sla_medium_minutes")
                && hasColumn("warning_rule", "sla_high_minutes");
        if (hasSlaColumns) {
            return jdbcTemplate.update(
                    """
                    UPDATE warning_rule
                    SET rule_name=?, description=?, enabled=?, priority=?,
                        low_threshold=?, medium_threshold=?, high_threshold=?,
                        emotion_combo_json=CAST(? AS JSON), trend_window_days=?, trigger_count=?,
                        suggest_template_code=?, sla_low_minutes=?, sla_medium_minutes=?, sla_high_minutes=?, updated_at=NOW()
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
                    slaLowMinutes,
                    slaMediumMinutes,
                    slaHighMinutes,
                    id
            );
        }
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

    public Optional<Long> findOpenSystemDriftWarningByEmotion(String emotion) {
        List<Long> rows = jdbcTemplate.query(
                """
                SELECT id
                FROM warning_event
                WHERE report_id IS NULL
                  AND task_id IS NULL
                  AND user_id IS NULL
                  AND UPPER(COALESCE(top_emotion, 'UNKNOWN')) = UPPER(?)
                  AND status IN ('NEW','ACKED','FOLLOWING')
                ORDER BY id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getLong("id"),
                emotion
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public long createWarningEvent(Long reportId,
                                   Long taskId,
                                   Long userId,
                                   String userMask,
                                   double riskScore,
                                   String riskLevel,
                                   String topEmotion,
                                   Long triggerRuleId,
                                   String triggerSnapshotJson,
                                   LocalDateTime slaDeadlineAt) {
        boolean hasSlaColumns = hasColumn("warning_event", "sla_deadline_at");
        String sql = hasSlaColumns
                ? """
                INSERT INTO warning_event
                (report_id, task_id, user_id, user_mask, risk_score, risk_level, top_emotion,
                 trigger_rule_id, trigger_snapshot, sla_deadline_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?)
                """
                : """
                INSERT INTO warning_event
                (report_id, task_id, user_id, user_mask, risk_score, risk_level, top_emotion,
                 trigger_rule_id, trigger_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, reportId);
            ps.setObject(2, taskId);
            ps.setObject(3, userId);
            ps.setString(4, userMask);
            ps.setDouble(5, riskScore);
            ps.setString(6, riskLevel);
            ps.setString(7, topEmotion);
            ps.setObject(8, triggerRuleId);
            ps.setString(9, triggerSnapshotJson);
            if (hasSlaColumns) {
                ps.setTimestamp(10, slaDeadlineAt == null ? null : Timestamp.valueOf(slaDeadlineAt));
            }
            return ps;
        }, keyHolder);
        Number key = Objects.requireNonNull(keyHolder.getKey(), "createWarningEvent generated key is null");
        return key.longValue();
    }

    public int updateWarningSnapshotWorkflow(long warningEventId,
                                             String actionType,
                                             String actionNote,
                                             String templateCode,
                                             String nextStatus,
                                             Long operatorId) {
        return jdbcTemplate.update(
                """
                UPDATE warning_event
                SET trigger_snapshot = JSON_SET(
                        COALESCE(trigger_snapshot, JSON_OBJECT()),
                        '$.workflow.lastAction', ?,
                        '$.workflow.lastActionNote', ?,
                        '$.workflow.lastTemplateCode', ?,
                        '$.workflow.lastStatus', ?,
                        '$.workflow.lastOperatorId', ?,
                        '$.workflow.updatedAt', DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s')
                    ),
                    updated_at = NOW()
                WHERE id = ?
                """,
                actionType,
                actionNote,
                templateCode,
                nextStatus,
                operatorId,
                warningEventId
        );
    }

    public int updateReportGovernanceTrace(long reportId,
                                           long warningEventId,
                                           String status,
                                           String actionType,
                                           String templateCode,
                                           String actionNote) {
        return jdbcTemplate.update(
                """
                UPDATE report_resource
                SET report_json = JSON_SET(
                        COALESCE(report_json, JSON_OBJECT()),
                        '$.governance.warning.warningId', ?,
                        '$.governance.warning.status', ?,
                        '$.governance.warning.lastAction', ?,
                        '$.governance.warning.templateCode', ?,
                        '$.governance.warning.actionNote', ?,
                        '$.governance.warning.updatedAt', DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s')
                    )
                WHERE id = ?
                  AND deleted_at IS NULL
                """,
                warningEventId,
                status,
                actionType,
                templateCode,
                actionNote,
                reportId
        );
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

    public List<Map<String, Object>> listWarningActions(long warningId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id, warning_event_id, action_type, action_note, template_code, operator_id, created_at
                FROM warning_action_log
                WHERE warning_event_id=?
                ORDER BY created_at ASC, id ASC
                """,
                warningId
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

    public void touchWarningWorkflow(long warningId, String actionType, String nextStatus) {
        if (!hasColumn("warning_event", "first_acked_at")) {
            return;
        }
        String normalizedAction = actionType == null ? "" : actionType.trim().toUpperCase();
        String normalizedStatus = nextStatus == null ? "" : nextStatus.trim().toUpperCase();

        if ("MARK_FOLLOWED".equals(normalizedAction) || "ACK".equals(normalizedAction) || "ACKED".equals(normalizedStatus)) {
            jdbcTemplate.update(
                    "UPDATE warning_event SET first_acked_at=COALESCE(first_acked_at, NOW()), updated_at=NOW() WHERE id=?",
                    warningId
            );
        }

        if ("FOLLOWING".equals(normalizedStatus)
                || "MARK_CALLBACK".equals(normalizedAction)
                || "PUSH_SUGGESTION".equals(normalizedAction)
                || "ADD_NOTE".equals(normalizedAction)) {
            jdbcTemplate.update(
                    "UPDATE warning_event SET first_followed_at=COALESCE(first_followed_at, NOW()), updated_at=NOW() WHERE id=?",
                    warningId
            );
        }

        if ("CLOSE".equals(normalizedAction) || "CLOSED".equals(normalizedStatus)) {
            jdbcTemplate.update(
                    "UPDATE warning_event SET status='CLOSED', updated_at=NOW() WHERE id=?",
                    warningId
            );
        }
    }

    public int markOverdueWarningsBreached() {
        if (!hasColumn("warning_event", "breached") || !hasColumn("warning_event", "sla_deadline_at")) {
            return 0;
        }
        return jdbcTemplate.update(
                """
                UPDATE warning_event
                SET breached=1, updated_at=NOW()
                WHERE breached=0
                  AND sla_deadline_at IS NOT NULL
                  AND status IN ('NEW','ACKED','FOLLOWING')
                  AND NOW() > sla_deadline_at
                """
        );
    }

    public long countUserRiskReportsWithinWindow(long userId, int trendWindowDays, double thresholdScore) {
        String riskExpr = riskScoreExpr("rr");
        Long total = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM report_resource rr
                JOIN audio_file af ON af.id=rr.audio_id
                WHERE rr.deleted_at IS NULL
                  AND af.user_id=?
                  AND rr.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND %s >= ?
                """.formatted(riskExpr),
                Long.class,
                userId,
                trendWindowDays,
                thresholdScore
        );
        return total == null ? 0 : total;
    }

    public long countUserEmotionWithinWindow(long userId, int trendWindowDays, String emotionCode) {
        Long total = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM report_resource rr
                JOIN audio_file af ON af.id=rr.audio_id
                WHERE rr.deleted_at IS NULL
                  AND af.user_id=?
                  AND rr.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND UPPER(COALESCE(NULLIF(rr.overall_emotion, ''),
                                     JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.ser.overall.emotionCode')),
                                     'UNKNOWN'))=UPPER(?)
                """,
                Long.class,
                userId,
                trendWindowDays,
                emotionCode
        );
        return total == null ? 0 : total;
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

    public List<Map<String, Object>> listEmotionDistributionLastDays(int days) {
        return jdbcTemplate.queryForList(
                """
                SELECT UPPER(COALESCE(NULLIF(rr.overall_emotion, ''),
                                      JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.ser.overall.emotionCode')),
                                      'UNKNOWN')) AS emotion,
                       COUNT(*) AS count
                FROM report_resource rr
                WHERE rr.deleted_at IS NULL
                  AND rr.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                GROUP BY emotion
                ORDER BY count DESC
                """,
                days
        );
    }

    public List<Map<String, Object>> listEmotionDistributionBeforeDays(int fromDaysAgo, int toDaysAgo) {
        return jdbcTemplate.queryForList(
                """
                SELECT UPPER(COALESCE(NULLIF(rr.overall_emotion, ''),
                                      JSON_UNQUOTE(JSON_EXTRACT(rr.report_json, '$.ser.overall.emotionCode')),
                                      'UNKNOWN')) AS emotion,
                       COUNT(*) AS count
                FROM report_resource rr
                WHERE rr.deleted_at IS NULL
                  AND rr.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND rr.created_at < DATE_SUB(NOW(), INTERVAL ? DAY)
                GROUP BY emotion
                ORDER BY count DESC
                """,
                fromDaysAgo,
                toDaysAgo
        );
    }

    public List<Map<String, Object>> listFailedTaskCategoryStats(int days) {
        return jdbcTemplate.queryForList(
                """
                SELECT CASE
                         WHEN error_message IS NULL OR error_message = '' THEN 'UNKNOWN'
                         WHEN error_message LIKE 'TIMEOUT:%' THEN 'TIMEOUT'
                         WHEN error_message LIKE '%429%' OR error_message LIKE '%rate%' THEN 'RATE_LIMIT'
                         WHEN error_message LIKE '%401%' OR error_message LIKE '%403%' THEN 'AUTH'
                         ELSE 'OTHER'
                       END AS category,
                       COUNT(*) AS count
                FROM analysis_task
                WHERE status='FAILED'
                  AND updated_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                GROUP BY category
                ORDER BY count DESC
                """,
                days
        );
    }

    public List<Map<String, Object>> listFailedTaskSamples(int days, int limit) {
        return jdbcTemplate.queryForList(
                """
                SELECT id, audio_file_id, error_message, updated_at
                FROM analysis_task
                WHERE status='FAILED'
                  AND updated_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                ORDER BY updated_at DESC, id DESC
                LIMIT ?
                """,
                days,
                Math.max(1, Math.min(limit, 100))
        );
    }

    public Map<String, Object> slaOverview(int days) {
        if (!hasColumn("warning_event", "breached")) {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT COUNT(*) AS total,
                           SUM(CASE WHEN status IN ('RESOLVED','CLOSED') THEN 1 ELSE 0 END) AS resolved,
                           0 AS breached,
                           0 AS acked,
                           NULL AS avg_ack_minutes,
                           AVG(CASE WHEN resolved_at IS NULL THEN NULL ELSE TIMESTAMPDIFF(MINUTE, created_at, resolved_at) END) AS avg_resolve_minutes
                    FROM warning_event
                    WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                    """,
                    days
            );
        }
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN status IN ('RESOLVED','CLOSED') THEN 1 ELSE 0 END) AS resolved,
                       SUM(CASE WHEN breached=1 THEN 1 ELSE 0 END) AS breached,
                       SUM(CASE WHEN first_acked_at IS NOT NULL THEN 1 ELSE 0 END) AS acked,
                       AVG(CASE WHEN first_acked_at IS NULL THEN NULL ELSE TIMESTAMPDIFF(MINUTE, created_at, first_acked_at) END) AS avg_ack_minutes,
                       AVG(CASE WHEN resolved_at IS NULL THEN NULL ELSE TIMESTAMPDIFF(MINUTE, created_at, resolved_at) END) AS avg_resolve_minutes
                FROM warning_event
                WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                """,
                days
        );
        return row;
    }

    public List<Map<String, Object>> listSlaTrend(int days) {
        if (!hasColumn("warning_event", "breached")) {
            return jdbcTemplate.queryForList(
                    """
                    SELECT DATE(created_at) AS stat_date,
                           COUNT(*) AS total,
                           SUM(CASE WHEN status IN ('RESOLVED','CLOSED') THEN 1 ELSE 0 END) AS resolved,
                           0 AS breached
                    FROM warning_event
                    WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                    GROUP BY DATE(created_at)
                    ORDER BY stat_date DESC
                    """,
                    days
            );
        }
        return jdbcTemplate.queryForList(
                """
                SELECT DATE(created_at) AS stat_date,
                       COUNT(*) AS total,
                       SUM(CASE WHEN status IN ('RESOLVED','CLOSED') THEN 1 ELSE 0 END) AS resolved,
                       SUM(CASE WHEN breached=1 THEN 1 ELSE 0 END) AS breached
                FROM warning_event
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY DATE(created_at)
                ORDER BY stat_date DESC
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

    private String riskScoreExpr(String alias) {
        String jsonPrimary = "CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(%s.report_json, '$.riskAssessment.risk_score')), '0') AS DECIMAL(10,4))".formatted(alias);
        String jsonFallback = "CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(%s.report_json, '$.analysis_result.risk_assessment.risk_score')), '0') AS DECIMAL(10,4))".formatted(alias);
        String merged = "COALESCE(NULLIF(%s, 0), %s)".formatted(jsonPrimary, jsonFallback);
        return "CASE WHEN %s <= 1 THEN %s * 100 ELSE %s END".formatted(merged, merged, merged);
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

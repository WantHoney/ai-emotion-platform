package com.wuhao.aiemotion.repository;

import com.wuhao.aiemotion.domain.AnalysisTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AnalysisTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalysisTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AnalysisTask> TASK_ROW_MAPPER = (rs, rowNum) -> new AnalysisTask(
            rs.getLong("id"),
            rs.getObject("audio_file_id") == null ? null : rs.getLong("audio_file_id"),
            rs.getString("status"),
            rs.getInt("attempt_count"),
            rs.getObject("max_attempts") == null ? null : rs.getInt("max_attempts"),
            rs.getString("trace_id"),
            rs.getTimestamp("next_run_at") == null ? null : rs.getTimestamp("next_run_at").toLocalDateTime(),
            rs.getTimestamp("locked_at") == null ? null : rs.getTimestamp("locked_at").toLocalDateTime(),
            rs.getString("locked_by"),
            rs.getString("error_message"),
            rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toLocalDateTime(),
            rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime(),
            rs.getObject("duration_ms") == null ? null : rs.getLong("duration_ms"),
            rs.getObject("ser_latency_ms") == null ? null : rs.getLong("ser_latency_ms"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime()
    );

    public boolean audioExists(long audioId) {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audio_file WHERE id=? AND status <> 'DELETED'", Long.class, audioId);
        return n != null && n > 0;
    }

    public long insertPendingTask(long audioId, int maxAttempts, String traceId) {
        String sql = """
                INSERT INTO analysis_task (audio_file_id, status, attempt_count, max_attempts, trace_id, next_run_at, locked_at, locked_by)
                VALUES (?, 'PENDING', 0, ?, ?, NULL, NULL, NULL)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, audioId);
            ps.setInt(2, maxAttempts);
            ps.setString(3, traceId);
            return ps;
        }, keyHolder);

        Number key = Objects.requireNonNull(keyHolder.getKey(), "insert analysis_task: generated key is null");
        return key.longValue();
    }

    public Optional<AnalysisTask> findById(long taskId) {
        List<AnalysisTask> list = jdbcTemplate.query(
                "SELECT * FROM analysis_task WHERE id=?",
                TASK_ROW_MAPPER,
                taskId
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public long countTasks(String status) {
        return countTasks(status, null);
    }

    public long countTasks(String status, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM analysis_task WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendTaskFilters(status, keyword, sql, args);
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public long countTasksByUser(long userId, String status, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM analysis_task t
                JOIN audio_file af ON af.id=t.audio_file_id
                WHERE af.user_id=?
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        appendTaskFiltersWithAlias("t", status, keyword, sql, args);
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public List<AnalysisTask> findTaskPage(int offset,
                                           int size,
                                           String status,
                                           String keyword,
                                           String sortBy,
                                           String sortOrder) {
        StringBuilder sql = new StringBuilder("SELECT * FROM analysis_task WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendTaskFilters(status, keyword, sql, args);
        String orderBy = resolveTaskOrderBy(sortBy, sortOrder);
        sql.append(" ORDER BY ").append(orderBy).append(" LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), TASK_ROW_MAPPER, args.toArray());
    }

    public List<AnalysisTask> findTaskPageByUser(long userId,
                                                 int offset,
                                                 int size,
                                                 String status,
                                                 String keyword,
                                                 String sortBy,
                                                 String sortOrder) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.*
                FROM analysis_task t
                JOIN audio_file af ON af.id=t.audio_file_id
                WHERE af.user_id=?
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        appendTaskFiltersWithAlias("t", status, keyword, sql, args);
        String orderBy = resolveTaskOrderByWithAlias("t", sortBy, sortOrder);
        sql.append(" ORDER BY ").append(orderBy).append(" LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), TASK_ROW_MAPPER, args.toArray());
    }

    private void appendTaskFilters(String status, String keyword, StringBuilder sql, List<Object> args) {
        appendTaskFiltersWithAlias("", status, keyword, sql, args);
    }

    private void appendTaskFiltersWithAlias(String alias, String status, String keyword, StringBuilder sql, List<Object> args) {
        String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
        if (status != null && !status.isBlank()) {
            sql.append(" AND ").append(prefix).append("status=?");
            args.add(status.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            String normalized = keyword.trim();
            String like = "%" + normalized + "%";
            sql.append(" AND (CAST(").append(prefix).append("id AS CHAR) LIKE ? OR CAST(").append(prefix)
                    .append("audio_file_id AS CHAR) LIKE ? OR ").append(prefix).append("trace_id LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
        }
    }

    private String resolveTaskOrderBy(String sortBy, String sortOrder) {
        return resolveTaskOrderByWithAlias("", sortBy, sortOrder);
    }

    private String resolveTaskOrderByWithAlias(String alias, String sortBy, String sortOrder) {
        String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
        String column = switch (sortBy == null ? "" : sortBy.trim()) {
            case "updatedAt" -> prefix + "updated_at";
            case "status" -> prefix + "status";
            default -> prefix + "created_at";
        };
        String direction = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        return column + " " + direction;
    }

    public List<AnalysisTask> findRunnableCandidates(int batchSize) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM analysis_task
                WHERE status IN ('PENDING', 'RETRY_WAIT')
                  AND (next_run_at IS NULL OR next_run_at <= NOW())
                ORDER BY created_at ASC
                LIMIT ?
                """,
                TASK_ROW_MAPPER,
                batchSize
        );
    }

    public int claimTask(long taskId, String workerId) {
        return jdbcTemplate.update(
                """
                UPDATE analysis_task
                SET status='RUNNING', locked_by=?, locked_at=NOW(), started_at = COALESCE(started_at, NOW()), updated_at=NOW()
                WHERE id=?
                  AND status IN ('PENDING','RETRY_WAIT')
                  AND (next_run_at IS NULL OR next_run_at<=NOW())
                """,
                workerId,
                taskId
        );
    }

    public int markSuccess(long taskId, String workerId, long serLatencyMs) {
        return jdbcTemplate.update(
                """
                UPDATE analysis_task
                SET status='SUCCESS', next_run_at=NULL, error_message=NULL,
                    locked_by=NULL, locked_at=NULL, finished_at = NOW(),
                    duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) DIV 1000,
                    ser_latency_ms = ?,
                    updated_at=NOW()
                WHERE id=? AND status='RUNNING' AND locked_by=?
                """,
                serLatencyMs,
                taskId,
                workerId
        );
    }

    public int markRetryOrFailed(long taskId,
                                 String workerId,
                                 int maxAttempts,
                                 String errorMessage,
                                 int backoffSeconds) {
        return jdbcTemplate.update(
                """
                UPDATE analysis_task
                SET attempt_count = attempt_count + 1,
                    status = CASE WHEN attempt_count + 1 >= ? THEN 'FAILED' ELSE 'RETRY_WAIT' END,
                    max_attempts = ?,
                    next_run_at = CASE WHEN attempt_count + 1 >= ? THEN NULL ELSE DATE_ADD(NOW(), INTERVAL ? SECOND) END,
                    error_message = ?,
                    locked_by = NULL,
                    locked_at = NULL,
                    finished_at = CASE WHEN attempt_count + 1 >= ? THEN NOW() ELSE finished_at END,
                    duration_ms = CASE WHEN attempt_count + 1 >= ? THEN TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) DIV 1000 ELSE duration_ms END,
                    updated_at = NOW()
                WHERE id = ?
                  AND status='RUNNING'
                  AND locked_by = ?
                """,
                maxAttempts,
                maxAttempts,
                maxAttempts,
                backoffSeconds,
                errorMessage,
                maxAttempts,
                maxAttempts,
                taskId,
                workerId
        );
    }

    public Optional<String> findAudioStoragePath(long taskId) {
        List<String> values = jdbcTemplate.query(
                """
                SELECT af.storage_path
                FROM analysis_task t
                JOIN audio_file af ON af.id = t.audio_file_id
                WHERE t.id = ?
                """,
                (rs, rowNum) -> rs.getString("storage_path"),
                taskId
        );
        return values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.get(0));
    }

    public Optional<Long> findUserIdByTaskId(long taskId) {
        List<Long> values = jdbcTemplate.query(
                """
                SELECT af.user_id
                FROM analysis_task t
                JOIN audio_file af ON af.id = t.audio_file_id
                WHERE t.id = ?
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                taskId
        );
        return values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.get(0));
    }

    public long countActiveTasks() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_task WHERE status='RUNNING'", Long.class);
        return total == null ? 0 : total;
    }

    public long countInStatusSince(String status, LocalDateTime since) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_task WHERE status=? AND updated_at>=?", Long.class, status, since);
        return total == null ? 0 : total;
    }

    public Double avgDurationSinceSuccess(LocalDateTime since) {
        return jdbcTemplate.queryForObject("SELECT AVG(duration_ms) FROM analysis_task WHERE status='SUCCESS' AND finished_at>=?", Double.class, since);
    }

    public Double avgSerLatencySince(LocalDateTime since) {
        return jdbcTemplate.queryForObject(
                "SELECT AVG(ser_latency_ms) FROM analysis_task WHERE status='SUCCESS' AND ser_latency_ms IS NOT NULL AND updated_at>=?",
                Double.class,
                since
        );
    }

    public long countSerTimeoutSince(LocalDateTime since) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_task WHERE updated_at>=? AND error_message LIKE 'TIMEOUT:%'", Long.class, since);
        return total == null ? 0 : total;
    }

    public void markDeletedByAudioId(long audioId) {
        jdbcTemplate.update("UPDATE analysis_task SET status='DELETED', updated_at=NOW() WHERE audio_file_id=?", audioId);
    }
}

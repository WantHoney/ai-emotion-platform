package com.wuhao.aiemotion.repository;

import com.wuhao.aiemotion.domain.AnalysisSegment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class AnalysisSegmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalysisSegmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AnalysisSegment> SEGMENT_ROW_MAPPER = (rs, rowNum) -> new AnalysisSegment(
            rs.getLong("id"),
            rs.getLong("task_id"),
            rs.getInt("start_ms"),
            rs.getInt("end_ms"),
            rs.getString("emotion_code"),
            rs.getDouble("confidence"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime()
    );

    public List<AnalysisSegment> findByTaskIdOrderByStartMs(long taskId) {
        return jdbcTemplate.query(
                "SELECT * FROM analysis_segment WHERE task_id=? ORDER BY start_ms ASC",
                SEGMENT_ROW_MAPPER,
                taskId
        );
    }

    public long countSegmentsInRange(long taskId, long fromMs, long toMs) {
        Long total = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM analysis_segment
                WHERE task_id = ?
                  AND end_ms > ?
                  AND start_ms < ?
                """,
                Long.class,
                taskId,
                fromMs,
                toMs
        );
        return total == null ? 0L : total;
    }

    public List<AnalysisSegment> findSegmentsInRange(long taskId, long fromMs, long toMs, int limit, int offset) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM analysis_segment
                WHERE task_id = ?
                  AND end_ms > ?
                  AND start_ms < ?
                ORDER BY start_ms ASC
                LIMIT ? OFFSET ?
                """,
                SEGMENT_ROW_MAPPER,
                taskId,
                fromMs,
                toMs,
                limit,
                offset
        );
    }

    public void deleteByTaskId(long taskId) {
        jdbcTemplate.update("DELETE FROM analysis_segment WHERE task_id=?", taskId);
    }

    public void batchInsert(long taskId, List<AnalysisSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        List<Object[]> args = new ArrayList<>();
        for (AnalysisSegment segment : segments) {
            args.add(new Object[]{
                    taskId,
                    segment.startMs(),
                    segment.endMs(),
                    segment.emotionCode(),
                    segment.confidence()
            });
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO analysis_segment (task_id, start_ms, end_ms, emotion_code, confidence)
                VALUES (?, ?, ?, ?, ?)
                """,
                args
        );
    }

}

package com.wuhao.aiemotion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Repository
public class ReportMockRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReportMockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean analysisExists(long analysisId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audio_analysis WHERE id=?",
                Long.class,
                analysisId
        );
        return n != null && n > 0;
    }

    public boolean hasSegments(long analysisId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audio_segment WHERE analysis_id=?",
                Long.class,
                analysisId
        );
        return n != null && n > 0;
    }

    public int deleteSegmentsByAnalysisId(long analysisId) {
        return jdbcTemplate.update("DELETE FROM audio_segment WHERE analysis_id=?", analysisId);
    }

    public long insertSegment(long analysisId, long startMs, long endMs, String transcript) {
        jdbcTemplate.update(
                "INSERT INTO audio_segment (analysis_id, start_ms, end_ms, transcript) VALUES (?, ?, ?, ?)",
                analysisId, startMs, endMs, transcript
        );
        // 取最近插入的ID（单机开发环境足够用；严谨可用 KeyHolder）
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0 : id;
    }

    public long ensureEmotionLabel(String code, String nameZh, String scheme) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM emotion_label WHERE code=? AND scheme=? LIMIT 1",
                code, scheme
        );
        if (!rows.isEmpty()) {
            return ((Number) rows.get(0).get("id")).longValue();
        }

        jdbcTemplate.update(
                "INSERT INTO emotion_label (code, name_zh, scheme) VALUES (?, ?, ?)",
                code, nameZh, scheme
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0 : id;
    }

    public void upsertSegmentEmotion(long segmentId, long emotionId, double score) {
        // score DECIMAL(6,5) -> 用 BigDecimal 更稳
        BigDecimal bd = BigDecimal.valueOf(score);

        jdbcTemplate.update(
                "INSERT INTO segment_emotion (segment_id, emotion_id, score) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE score=VALUES(score)",
                segmentId, emotionId, bd
        );
    }
}

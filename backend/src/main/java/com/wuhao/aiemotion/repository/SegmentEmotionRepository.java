package com.wuhao.aiemotion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class SegmentEmotionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SegmentEmotionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询某次 analysis 的 segments，并附带每个 segment 的 emotions
     * 返回结构：每个 segment map 里带一个 key = "emotions" 的 List<Map<String,Object>>
     */
    public List<Map<String, Object>> findSegmentsWithEmotions(long analysisId) {

        // 1) 查 segments
        List<Map<String, Object>> segments = jdbcTemplate.queryForList(
                "SELECT id, start_ms, end_ms, transcript " +
                        "FROM audio_segment " +
                        "WHERE analysis_id=? " +
                        "ORDER BY start_ms ASC, id ASC",
                analysisId
        );

        if (segments.isEmpty()) {
            return segments;
        }

        // 2) 收集 segmentIds
        List<Long> segIds = new ArrayList<>();
        for (Map<String, Object> seg : segments) {
            segIds.add(((Number) seg.get("id")).longValue());
        }

        // 3) 查 emotions（IN (?, ?, ?)）
        String placeholders = String.join(",", Collections.nCopies(segIds.size(), "?"));

        String emoSql =
                "SELECT se.segment_id, " +
                        "       el.id AS emotion_id, " +
                        "       el.code, " +
                        "       el.name_zh, " +
                        "       el.scheme, " +
                        "       se.score " +
                        "FROM segment_emotion se " +
                        "JOIN emotion_label el ON el.id = se.emotion_id " +
                        "WHERE se.segment_id IN (" + placeholders + ") " +
                        "ORDER BY se.segment_id ASC, se.score DESC";

        List<Map<String, Object>> emotions = jdbcTemplate.queryForList(emoSql, segIds.toArray());

        // 4) 组装：segment_id -> emotions list
        Map<Long, List<Map<String, Object>>> emoMap = new HashMap<>();
        for (Map<String, Object> row : emotions) {
            Long sid = ((Number) row.get("segment_id")).longValue();
            emoMap.computeIfAbsent(sid, k -> new ArrayList<>()).add(row);
        }

        // 5) emotions 挂回 segments
        for (Map<String, Object> seg : segments) {
            Long sid = ((Number) seg.get("id")).longValue();
            seg.put("emotions", emoMap.getOrDefault(sid, List.of()));
        }

        return segments;
    }
}

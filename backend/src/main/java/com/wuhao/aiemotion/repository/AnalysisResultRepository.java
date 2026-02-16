package com.wuhao.aiemotion.repository;

import com.wuhao.aiemotion.domain.AnalysisResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AnalysisResultRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalysisResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AnalysisResult> RESULT_ROW_MAPPER = (rs, rowNum) -> new AnalysisResult(
            rs.getLong("id"),
            rs.getLong("task_id"),
            rs.getString("model_name"),
            rs.getString("overall_emotion_code"),
            rs.getObject("overall_confidence") == null ? null : rs.getDouble("overall_confidence"),
            rs.getObject("duration_ms") == null ? null : rs.getInt("duration_ms"),
            rs.getObject("sample_rate") == null ? null : rs.getInt("sample_rate"),
            rs.getString("raw_json"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime()
    );

    public Optional<AnalysisResult> findByTaskId(long taskId) {
        List<AnalysisResult> list = jdbcTemplate.query(
                "SELECT * FROM analysis_result WHERE task_id=?",
                RESULT_ROW_MAPPER,
                taskId
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void upsertByTaskId(long taskId,
                               String modelName,
                               String overallEmotionCode,
                               Double overallConfidence,
                               Integer durationMs,
                               Integer sampleRate,
                               String rawJson) {
        jdbcTemplate.update(
                """
                INSERT INTO analysis_result
                (task_id, model_name, overall_emotion_code, overall_confidence, duration_ms, sample_rate, raw_json)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE
                    model_name = VALUES(model_name),
                    overall_emotion_code = VALUES(overall_emotion_code),
                    overall_confidence = VALUES(overall_confidence),
                    duration_ms = VALUES(duration_ms),
                    sample_rate = VALUES(sample_rate),
                    raw_json = VALUES(raw_json)
                """,
                taskId,
                modelName,
                overallEmotionCode,
                overallConfidence,
                durationMs,
                sampleRate,
                rawJson
        );
    }

}

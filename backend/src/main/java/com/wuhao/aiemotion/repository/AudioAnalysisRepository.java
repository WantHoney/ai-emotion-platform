package com.wuhao.aiemotion.repository;

import com.wuhao.aiemotion.domain.AudioAnalysis;
import com.wuhao.aiemotion.dto.response.AudioAnalysisAdminListResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AudioAnalysisRepository {

    private final JdbcTemplate jdbcTemplate;

    public AudioAnalysisRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AudioAnalysis> AUDIO_ANALYSIS_ROW_MAPPER = (rs, rowNum) -> new AudioAnalysis(
            rs.getLong("id"),
            rs.getLong("audio_id"),
            rs.getString("model_name"),
            rs.getString("model_version"),
            rs.getString("status"),
            rs.getString("summary_json"),
            rs.getString("error_message"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime()
    );

    /**
     * ✅ 管理列表专用 RowMapper（带 audio 原始名）
     */
    private static final RowMapper<AudioAnalysisAdminListResponse.Item> ADMIN_ITEM_MAPPER = (rs, rowNum) ->
            new AudioAnalysisAdminListResponse.Item(
                    rs.getLong("id"),
                    rs.getLong("audio_id"),
                    rs.getString("audio_original_name"),
                    rs.getString("model_name"),
                    rs.getString("model_version"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime().toString(),
                    rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime().toString()
            );

    public boolean audioExists(long audioId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audio_file WHERE id=?",
                Long.class,
                audioId
        );
        return n != null && n > 0;
    }

    public long insert(long audioId, String modelName, String modelVersion) {
        String sql = """
                INSERT INTO audio_analysis (audio_id, model_name, model_version, status)
                VALUES (?, ?, ?, 'PENDING')
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, audioId);
            ps.setString(2, modelName);
            ps.setString(3, modelVersion);
            return ps;
        }, keyHolder);

        Number key = Objects.requireNonNull(keyHolder.getKey(), "insert audio_analysis: generated key is null");
        return key.longValue();
    }

    public int updateStatus(long analysisId, String status, String summaryJson, String errorMessage) {
        return jdbcTemplate.update(
                "UPDATE audio_analysis " +
                        "SET status=?, " +
                        "summary_json=CASE WHEN ? IS NULL THEN NULL ELSE CAST(? AS JSON) END, " +
                        "error_message=? " +
                        "WHERE id=?",
                status, summaryJson, summaryJson, errorMessage, analysisId
        );
    }

    public Optional<AudioAnalysis> findById(long analysisId) {
        List<AudioAnalysis> list = jdbcTemplate.query(
                "SELECT * FROM audio_analysis WHERE id=?",
                AUDIO_ANALYSIS_ROW_MAPPER,
                analysisId
        );
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }

    public Optional<AudioAnalysis> findLatestByAudioId(long audioId) {
        List<AudioAnalysis> list = jdbcTemplate.query(
                "SELECT * FROM audio_analysis WHERE audio_id=? ORDER BY created_at DESC, id DESC LIMIT 1",
                AUDIO_ANALYSIS_ROW_MAPPER,
                audioId
        );
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }

    public long countByAudioId(long audioId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audio_analysis WHERE audio_id=?",
                Long.class,
                audioId
        );
    }

    public List<AudioAnalysis> findPageByAudioId(long audioId, int offset, int size) {
        return jdbcTemplate.query(
                "SELECT * FROM audio_analysis WHERE audio_id=? " +
                        "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                AUDIO_ANALYSIS_ROW_MAPPER,
                audioId, size, offset
        );
    }

    // =========================
    // ✅ 管理列表：全局分页
    // =========================

    public long countAll(String status) {
        if (status == null || status.isBlank()) {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audio_analysis", Long.class);
        }
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audio_analysis WHERE status=?",
                Long.class,
                status
        );
    }

    /**
     * ✅ NEW: 管理列表分页（带 audio 原始名）
     */
    public List<AudioAnalysisAdminListResponse.Item> findAdminPage(int offset, int size, String status) {
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT aa.*,
                           af.original_name AS audio_original_name
                    FROM audio_analysis aa
                    JOIN audio_file af ON af.id = aa.audio_id
                    ORDER BY aa.created_at DESC, aa.id DESC
                    LIMIT ? OFFSET ?
                    """, ADMIN_ITEM_MAPPER, size, offset);
        }

        return jdbcTemplate.query("""
                SELECT aa.*,
                       af.original_name AS audio_original_name
                FROM audio_analysis aa
                JOIN audio_file af ON af.id = aa.audio_id
                WHERE aa.status=?
                ORDER BY aa.created_at DESC, aa.id DESC
                LIMIT ? OFFSET ?
                """, ADMIN_ITEM_MAPPER, status, size, offset);
    }

    // =========================
    // ✅ B4: 删除分析（物理删除）
    // =========================
    public int deleteById(long analysisId) {
        return jdbcTemplate.update("DELETE FROM audio_analysis WHERE id=?", analysisId);
    }
}

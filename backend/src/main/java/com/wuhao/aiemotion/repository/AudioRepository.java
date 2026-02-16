package com.wuhao.aiemotion.repository;

import com.wuhao.aiemotion.domain.AudioFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

@Repository
public class AudioRepository {

    private final JdbcTemplate jdbcTemplate;

    public AudioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AudioFile> AUDIO_ROW_MAPPER = (rs, rowNum) -> new AudioFile(
            rs.getLong("id"),
            (Long) rs.getObject("user_id"),
            rs.getString("original_name"),
            rs.getString("stored_name"),
            rs.getString("storage_path"),
            rs.getString("content_type"),
            (Long) rs.getObject("size_bytes"),
            rs.getString("sha256"),
            (Long) rs.getObject("duration_ms"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    public long insertAudio(Long userId, String originalName, String storedName, String storagePath, String contentType, Long sizeBytes, String sha256, Long durationMs) {
        String sql = """
                INSERT INTO audio_file
                (user_id, original_name, stored_name, storage_path, content_type, size_bytes, sha256, duration_ms, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'UPLOADED')
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, userId);
            ps.setString(2, originalName);
            ps.setString(3, storedName);
            ps.setString(4, storagePath);
            ps.setString(5, contentType);
            ps.setObject(6, sizeBytes);
            ps.setString(7, sha256);
            ps.setObject(8, durationMs);
            return ps;
        }, keyHolder);

        Number key = Objects.requireNonNull(keyHolder.getKey(), "insertAudio: generated key is null");
        return key.longValue();
    }

    public boolean existsById(long id) {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audio_file WHERE id=?", Long.class, id);
        return n != null && n > 0;
    }

    public java.util.Optional<AudioFile> findById(long id) {
        List<AudioFile> list = jdbcTemplate.query("SELECT * FROM audio_file WHERE id=?", AUDIO_ROW_MAPPER, id);
        return list.isEmpty() ? java.util.Optional.empty() : java.util.Optional.ofNullable(list.get(0));
    }

    public java.util.Optional<Long> findUserIdByAudioId(long audioId) {
        List<Long> rows = jdbcTemplate.query(
                "SELECT user_id FROM audio_file WHERE id=? LIMIT 1",
                (rs, rowNum) -> rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                audioId
        );
        return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.ofNullable(rows.get(0));
    }

    public int softDelete(long id) {
        return jdbcTemplate.update("UPDATE audio_file SET status='DELETED', updated_at=NOW() WHERE id=?", id);
    }

    public long countAll(boolean onlyUploaded) {
        if (onlyUploaded) {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audio_file WHERE status='UPLOADED'", Long.class);
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audio_file", Long.class);
    }

    public List<AudioFile> findPage(int offset, int size, boolean onlyUploaded) {
        if (onlyUploaded) {
            return jdbcTemplate.query("""
                    SELECT *
                    FROM audio_file
                    WHERE status='UPLOADED'
                    ORDER BY created_at DESC
                    LIMIT ? OFFSET ?
                    """, AUDIO_ROW_MAPPER, size, offset);
        }
        return jdbcTemplate.query("""
                SELECT *
                FROM audio_file
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, AUDIO_ROW_MAPPER, size, offset);
    }

    public List<AudioFile> findManagedPage(int offset, int size, String q) {
        if (q == null || q.isBlank()) {
            return jdbcTemplate.query("SELECT * FROM audio_file ORDER BY created_at DESC LIMIT ? OFFSET ?", AUDIO_ROW_MAPPER, size, offset);
        }
        String like = "%" + q + "%";
        return jdbcTemplate.query("SELECT * FROM audio_file WHERE original_name LIKE ? OR stored_name LIKE ? ORDER BY created_at DESC LIMIT ? OFFSET ?", AUDIO_ROW_MAPPER, like, like, size, offset);
    }

    public long countManaged(String q) {
        if (q == null || q.isBlank()) {
            Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audio_file", Long.class);
            return n == null ? 0 : n;
        }
        String like = "%" + q + "%";
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audio_file WHERE original_name LIKE ? OR stored_name LIKE ?", Long.class, like, like);
        return n == null ? 0 : n;
    }

    public long countByUser(long userId, boolean onlyUploaded) {
        if (onlyUploaded) {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audio_file WHERE user_id=? AND status='UPLOADED'", Long.class, userId);
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audio_file WHERE user_id=?", Long.class, userId);
    }

    public List<AudioFile> findPageByUser(long userId, int offset, int size, boolean onlyUploaded) {
        if (onlyUploaded) {
            return jdbcTemplate.query("""
                    SELECT *
                    FROM audio_file
                    WHERE user_id=? AND status='UPLOADED'
                    ORDER BY created_at DESC
                    LIMIT ? OFFSET ?
                    """, AUDIO_ROW_MAPPER, userId, size, offset);
        }
        return jdbcTemplate.query("""
                SELECT *
                FROM audio_file
                WHERE user_id=?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, AUDIO_ROW_MAPPER, userId, size, offset);
    }
}

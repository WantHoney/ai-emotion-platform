package com.wuhao.aiemotion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AudioUploadSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public AudioUploadSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AudioUploadSessionEntity> SESSION_ROW_MAPPER = (rs, rowNum) ->
            new AudioUploadSessionEntity(
                    rs.getLong("id"),
                    rs.getString("upload_id"),
                    rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                    rs.getString("original_name"),
                    rs.getString("content_type"),
                    rs.getObject("total_size_bytes") == null ? null : rs.getLong("total_size_bytes"),
                    rs.getInt("total_chunks"),
                    rs.getInt("received_chunks"),
                    rs.getString("status"),
                    rs.getObject("merged_audio_id") == null ? null : rs.getLong("merged_audio_id"),
                    rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toLocalDateTime(),
                    rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime()
            );

    public long createSession(String uploadId,
                              Long userId,
                              String originalName,
                              String contentType,
                              Long totalSizeBytes,
                              int totalChunks,
                              LocalDateTime expiresAt) {
        String sql = """
                INSERT INTO audio_upload_session
                (upload_id, user_id, original_name, content_type, total_size_bytes, total_chunks, status, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, 'INIT', ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, uploadId);
            ps.setObject(2, userId);
            ps.setString(3, originalName);
            ps.setString(4, contentType);
            ps.setObject(5, totalSizeBytes);
            ps.setInt(6, totalChunks);
            ps.setTimestamp(7, expiresAt == null ? null : Timestamp.valueOf(expiresAt));
            return ps;
        }, keyHolder);
        Number key = Objects.requireNonNull(keyHolder.getKey(), "createSession generated key is null");
        return key.longValue();
    }

    public Optional<AudioUploadSessionEntity> findByUploadId(String uploadId) {
        List<AudioUploadSessionEntity> rows = jdbcTemplate.query(
                "SELECT * FROM audio_upload_session WHERE upload_id=? LIMIT 1",
                SESSION_ROW_MAPPER,
                uploadId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public int upsertChunk(long uploadSessionId,
                           int chunkIndex,
                           long chunkSizeBytes,
                           String chunkSha256,
                           String storagePath) {
        return jdbcTemplate.update(
                """
                INSERT INTO audio_upload_chunk
                (upload_session_id, chunk_index, chunk_size_bytes, chunk_sha256, storage_path)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    chunk_size_bytes = VALUES(chunk_size_bytes),
                    chunk_sha256 = VALUES(chunk_sha256),
                    storage_path = VALUES(storage_path)
                """,
                uploadSessionId,
                chunkIndex,
                chunkSizeBytes,
                chunkSha256,
                storagePath
        );
    }

    public int countReceivedChunks(long uploadSessionId) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audio_upload_chunk WHERE upload_session_id=?",
                Integer.class,
                uploadSessionId
        );
        return total == null ? 0 : total;
    }

    public void updateProgress(long sessionId, int receivedChunks, String status) {
        jdbcTemplate.update(
                "UPDATE audio_upload_session SET received_chunks=?, status=?, updated_at=NOW() WHERE id=?",
                receivedChunks,
                status,
                sessionId
        );
    }

    public List<Map<String, Object>> listChunksBySession(long sessionId) {
        return jdbcTemplate.queryForList(
                """
                SELECT chunk_index, chunk_size_bytes, chunk_sha256, storage_path, created_at
                FROM audio_upload_chunk
                WHERE upload_session_id=?
                ORDER BY chunk_index ASC
                """,
                sessionId
        );
    }

    public void markMerged(long sessionId, long mergedAudioId) {
        jdbcTemplate.update(
                "UPDATE audio_upload_session SET status='MERGED', merged_audio_id=?, updated_at=NOW() WHERE id=?",
                mergedAudioId,
                sessionId
        );
    }

    public void markFailed(long sessionId) {
        jdbcTemplate.update(
                "UPDATE audio_upload_session SET status='FAILED', updated_at=NOW() WHERE id=?",
                sessionId
        );
    }

    public void markCanceled(long sessionId) {
        jdbcTemplate.update(
                "UPDATE audio_upload_session SET status='CANCELED', updated_at=NOW() WHERE id=?",
                sessionId
        );
    }

    public void deleteChunksBySession(long sessionId) {
        jdbcTemplate.update("DELETE FROM audio_upload_chunk WHERE upload_session_id=?", sessionId);
    }

    public record AudioUploadSessionEntity(
            long id,
            String uploadId,
            Long userId,
            String originalName,
            String contentType,
            Long totalSizeBytes,
            int totalChunks,
            int receivedChunks,
            String status,
            Long mergedAudioId,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}


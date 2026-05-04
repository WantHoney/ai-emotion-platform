package com.wuhao.aiemotion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AdminInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminInboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS admin_inbox_state (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  admin_user_id BIGINT NOT NULL,
                  message_id VARCHAR(255) NOT NULL,
                  source_type VARCHAR(64) NULL,
                  source_id VARCHAR(191) NULL,
                  is_read TINYINT(1) NOT NULL DEFAULT 0,
                  read_at DATETIME(3) NULL,
                  is_archived TINYINT(1) NOT NULL DEFAULT 0,
                  archived_at DATETIME(3) NULL,
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_admin_inbox_state_user_message (admin_user_id, message_id),
                  KEY idx_admin_inbox_state_user_archived (admin_user_id, is_archived, updated_at),
                  CONSTRAINT fk_admin_inbox_state_user FOREIGN KEY (admin_user_id) REFERENCES auth_user(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS admin_inbox_preference (
                  admin_user_id BIGINT NOT NULL,
                  warning_enabled TINYINT(1) NOT NULL DEFAULT 1,
                  system_enabled TINYINT(1) NOT NULL DEFAULT 1,
                  model_enabled TINYINT(1) NOT NULL DEFAULT 1,
                  schedule_enabled TINYINT(1) NOT NULL DEFAULT 1,
                  psy_center_enabled TINYINT(1) NOT NULL DEFAULT 1,
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (admin_user_id),
                  CONSTRAINT fk_admin_inbox_preference_user FOREIGN KEY (admin_user_id) REFERENCES auth_user(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
    }

    public InboxPreferenceRow getPreference(long adminUserId) {
        ensurePreferenceRow(adminUserId);
        return jdbcTemplate.queryForObject("""
                SELECT admin_user_id,
                       warning_enabled,
                       system_enabled,
                       model_enabled,
                       schedule_enabled,
                       psy_center_enabled
                FROM admin_inbox_preference
                WHERE admin_user_id = ?
                """, (rs, rowNum) -> new InboxPreferenceRow(
                rs.getLong("admin_user_id"),
                rs.getBoolean("warning_enabled"),
                rs.getBoolean("system_enabled"),
                rs.getBoolean("model_enabled"),
                rs.getBoolean("schedule_enabled"),
                rs.getBoolean("psy_center_enabled")
        ), adminUserId);
    }

    public InboxPreferenceRow updatePreference(long adminUserId,
                                               boolean warningEnabled,
                                               boolean systemEnabled,
                                               boolean modelEnabled,
                                               boolean scheduleEnabled,
                                               boolean psyCenterEnabled) {
        ensurePreferenceRow(adminUserId);
        jdbcTemplate.update("""
                UPDATE admin_inbox_preference
                SET warning_enabled = ?,
                    system_enabled = ?,
                    model_enabled = ?,
                    schedule_enabled = ?,
                    psy_center_enabled = ?,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE admin_user_id = ?
                """, warningEnabled, systemEnabled, modelEnabled, scheduleEnabled, psyCenterEnabled, adminUserId);
        return getPreference(adminUserId);
    }

    public Map<String, InboxStateRow> findStates(long adminUserId, Collection<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = new ArrayList<>(messageIds);
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(adminUserId);
        args.addAll(ids);

        List<InboxStateRow> rows = jdbcTemplate.query("""
                SELECT admin_user_id,
                       message_id,
                       source_type,
                       source_id,
                       is_read,
                       read_at,
                       is_archived,
                       archived_at
                FROM admin_inbox_state
                WHERE admin_user_id = ?
                  AND message_id IN (""" + placeholders + ")",
                (rs, rowNum) -> new InboxStateRow(
                        rs.getLong("admin_user_id"),
                        rs.getString("message_id"),
                        rs.getString("source_type"),
                        rs.getString("source_id"),
                        rs.getBoolean("is_read"),
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toLocalDateTime(),
                        rs.getBoolean("is_archived"),
                        rs.getTimestamp("archived_at") == null ? null : rs.getTimestamp("archived_at").toLocalDateTime()
                ),
                args.toArray()
        );

        Map<String, InboxStateRow> stateByMessageId = new LinkedHashMap<>();
        for (InboxStateRow row : rows) {
            stateByMessageId.put(row.messageId(), row);
        }
        return stateByMessageId;
    }

    public void upsertStates(long adminUserId, List<InboxStateMutation> mutations) {
        if (mutations == null || mutations.isEmpty()) {
            return;
        }
        for (InboxStateMutation mutation : mutations) {
            jdbcTemplate.update("""
                    INSERT INTO admin_inbox_state
                    (admin_user_id, message_id, source_type, source_id, is_read, read_at, is_archived, archived_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      source_type = VALUES(source_type),
                      source_id = VALUES(source_id),
                      is_read = VALUES(is_read),
                      read_at = VALUES(read_at),
                      is_archived = VALUES(is_archived),
                      archived_at = VALUES(archived_at),
                      updated_at = CURRENT_TIMESTAMP(3)
                    """,
                    adminUserId,
                    mutation.messageId(),
                    mutation.sourceType(),
                    mutation.sourceId(),
                    mutation.read(),
                    toTimestamp(mutation.readAt()),
                    mutation.archived(),
                    toTimestamp(mutation.archivedAt())
            );
        }
    }

    private void ensurePreferenceRow(long adminUserId) {
        jdbcTemplate.update("""
                INSERT INTO admin_inbox_preference (admin_user_id)
                VALUES (?)
                ON DUPLICATE KEY UPDATE admin_user_id = VALUES(admin_user_id)
                """, adminUserId);
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    public record InboxPreferenceRow(long adminUserId,
                                     boolean warningEnabled,
                                     boolean systemEnabled,
                                     boolean modelEnabled,
                                     boolean scheduleEnabled,
                                     boolean psyCenterEnabled) {
    }

    public record InboxStateRow(long adminUserId,
                                String messageId,
                                String sourceType,
                                String sourceId,
                                boolean read,
                                LocalDateTime readAt,
                                boolean archived,
                                LocalDateTime archivedAt) {
    }

    public record InboxStateMutation(String messageId,
                                     String sourceType,
                                     String sourceId,
                                     boolean read,
                                     LocalDateTime readAt,
                                     boolean archived,
                                     LocalDateTime archivedAt) {
    }
}

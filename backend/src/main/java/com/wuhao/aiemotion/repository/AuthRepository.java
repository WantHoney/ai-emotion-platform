package com.wuhao.aiemotion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<DbUser> DB_USER_ROW_MAPPER = (rs, rowNum) -> new DbUser(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("status"),
            rs.getString("role_code")
    );

    private static final RowMapper<DbSession> DB_SESSION_ROW_MAPPER = (rs, rowNum) -> new DbSession(
            rs.getLong("user_id"),
            rs.getString("access_token"),
            rs.getString("refresh_token"),
            rs.getTimestamp("access_expire_at").toInstant(),
            rs.getTimestamp("refresh_expire_at").toInstant()
    );

    public void ensureSessionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS auth_session (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  access_token VARCHAR(128) NOT NULL,
                  refresh_token VARCHAR(128) NOT NULL,
                  access_expire_at DATETIME(3) NOT NULL,
                  refresh_expire_at DATETIME(3) NOT NULL,
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_auth_session_access_token (access_token),
                  UNIQUE KEY uk_auth_session_refresh_token (refresh_token),
                  KEY idx_auth_session_user_id (user_id),
                  KEY idx_auth_session_access_expire (access_expire_at),
                  KEY idx_auth_session_refresh_expire (refresh_expire_at),
                  CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
    }

    public void ensureRole(String roleCode, String roleName) {
        jdbcTemplate.update("""
                INSERT INTO auth_role (code, name)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """, roleCode, roleName);
    }

    public Optional<DbUser> findUserByUsername(String username) {
        List<DbUser> users = jdbcTemplate.query("""
                SELECT u.id, u.username, u.password_hash, u.status,
                       COALESCE(r.code, 'USER') AS role_code
                FROM auth_user u
                LEFT JOIN auth_user_role ur ON ur.user_id = u.id
                LEFT JOIN auth_role r ON r.id = ur.role_id
                WHERE u.username = ?
                ORDER BY ur.id ASC
                LIMIT 1
                """, DB_USER_ROW_MAPPER, username);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public Optional<DbUser> findUserWithRoleByUsername(String username) {
        return findUserByUsername(username);
    }

    public Optional<DbUser> findUserWithRoleById(long userId) {
        List<DbUser> users = jdbcTemplate.query("""
                SELECT u.id, u.username, u.password_hash, u.status,
                       COALESCE(r.code, 'USER') AS role_code
                FROM auth_user u
                LEFT JOIN auth_user_role ur ON ur.user_id = u.id
                LEFT JOIN auth_role r ON r.id = ur.role_id
                WHERE u.id = ?
                ORDER BY ur.id ASC
                LIMIT 1
                """, DB_USER_ROW_MAPPER, userId);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public long insertUser(String username, String passwordHash, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO auth_user (username, password_hash, status)
                    VALUES (?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, status);
            return ps;
        }, keyHolder);
        Number key = Objects.requireNonNull(keyHolder.getKey(), "insertUser: generated key is null");
        return key.longValue();
    }

    public void bindRole(long userId, String roleCode) {
        jdbcTemplate.update("""
                INSERT INTO auth_user_role (user_id, role_id)
                SELECT ?, id
                FROM auth_role
                WHERE code = ?
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
                """, userId, roleCode);
    }

    public void updateLastLoginAt(long userId) {
        jdbcTemplate.update("UPDATE auth_user SET last_login_at = NOW(3), updated_at = NOW(3) WHERE id = ?", userId);
    }

    public void insertSession(long userId, String accessToken, String refreshToken, Instant accessExpireAt, Instant refreshExpireAt) {
        jdbcTemplate.update("""
                INSERT INTO auth_session (user_id, access_token, refresh_token, access_expire_at, refresh_expire_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, accessToken, refreshToken, Timestamp.from(accessExpireAt), Timestamp.from(refreshExpireAt));
    }

    public Optional<DbSession> findSessionByAccessToken(String accessToken) {
        List<DbSession> sessions = jdbcTemplate.query("""
                SELECT user_id, access_token, refresh_token, access_expire_at, refresh_expire_at
                FROM auth_session
                WHERE access_token = ?
                LIMIT 1
                """, DB_SESSION_ROW_MAPPER, accessToken);
        return sessions.isEmpty() ? Optional.empty() : Optional.of(sessions.get(0));
    }

    public Optional<DbSession> findSessionByRefreshToken(String refreshToken) {
        List<DbSession> sessions = jdbcTemplate.query("""
                SELECT user_id, access_token, refresh_token, access_expire_at, refresh_expire_at
                FROM auth_session
                WHERE refresh_token = ?
                LIMIT 1
                """, DB_SESSION_ROW_MAPPER, refreshToken);
        return sessions.isEmpty() ? Optional.empty() : Optional.of(sessions.get(0));
    }

    public void deleteSessionByAccessToken(String accessToken) {
        jdbcTemplate.update("DELETE FROM auth_session WHERE access_token = ?", accessToken);
    }

    public void deleteSessionByRefreshToken(String refreshToken) {
        jdbcTemplate.update("DELETE FROM auth_session WHERE refresh_token = ?", refreshToken);
    }

    public long countUserRegisterSequence(long userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auth_user u
                JOIN auth_user target ON target.id = ?
                WHERE EXISTS (
                    SELECT 1
                    FROM auth_user_role ur
                    JOIN auth_role r ON r.id = ur.role_id
                    WHERE ur.user_id = u.id
                      AND r.code = 'USER'
                )
                  AND (
                    u.created_at < target.created_at
                    OR (u.created_at = target.created_at AND u.id <= target.id)
                  )
                """,
                Long.class,
                userId
        );
        return count == null ? 0L : count;
    }

    public record DbUser(long id, String username, String passwordHash, String status, String roleCode) {
    }

    public record DbSession(long userId, String accessToken, String refreshToken, Instant accessExpireAt, Instant refreshExpireAt) {
    }
}

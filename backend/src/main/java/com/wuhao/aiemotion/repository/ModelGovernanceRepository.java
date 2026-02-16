package com.wuhao.aiemotion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class ModelGovernanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelGovernanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listModels(String modelType, String env, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM model_registry WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (modelType != null && !modelType.isBlank()) {
            sql.append(" AND model_type=?");
            args.add(modelType.trim().toUpperCase());
        }
        if (env != null && !env.isBlank()) {
            sql.append(" AND env=?");
            args.add(env.trim().toLowerCase());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            args.add(status.trim().toUpperCase());
        }
        sql.append(" ORDER BY updated_at DESC, id DESC");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public long createModel(String modelCode,
                            String modelName,
                            String modelType,
                            String provider,
                            String version,
                            String env,
                            String status,
                            String metricsJson,
                            String configJson,
                            Long operatorId) {
        String sql = """
                INSERT INTO model_registry
                (model_code, model_name, model_type, provider, version, env, status, metrics_json, config_json, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, modelCode);
            ps.setString(2, modelName);
            ps.setString(3, modelType);
            ps.setString(4, provider);
            ps.setString(5, version);
            ps.setString(6, env);
            ps.setString(7, status);
            ps.setString(8, metricsJson);
            ps.setString(9, configJson);
            ps.setObject(10, operatorId);
            return ps;
        }, keyHolder);

        Number key = Objects.requireNonNull(keyHolder.getKey(), "createModel generated key is null");
        return key.longValue();
    }

    public Optional<Map<String, Object>> findModelById(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM model_registry WHERE id=? LIMIT 1", id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<Long> findOnlineModelId(String modelType, String env) {
        List<Long> rows = jdbcTemplate.query(
                "SELECT id FROM model_registry WHERE model_type=? AND env=? AND status='ONLINE' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                modelType,
                env
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public int markOfflineByTypeEnv(String modelType, String env, long excludeId) {
        return jdbcTemplate.update(
                "UPDATE model_registry SET status='OFFLINE', updated_at=NOW() WHERE model_type=? AND env=? AND status='ONLINE' AND id<>?",
                modelType,
                env,
                excludeId
        );
    }

    public int markOnline(long modelId) {
        return jdbcTemplate.update(
                "UPDATE model_registry SET status='ONLINE', published_at=COALESCE(published_at, NOW()), updated_at=NOW() WHERE id=?",
                modelId
        );
    }

    public void insertSwitchLog(String modelType, String env, Long fromModelId, long toModelId, String reason, Long operatorId) {
        jdbcTemplate.update(
                """
                INSERT INTO model_switch_log
                (model_type, env, from_model_id, to_model_id, switch_reason, switched_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                modelType,
                env,
                fromModelId,
                toModelId,
                reason,
                operatorId
        );
    }

    public List<Map<String, Object>> listSwitchLogs(String modelType, String env, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM model_switch_log WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (modelType != null && !modelType.isBlank()) {
            sql.append(" AND model_type=?");
            args.add(modelType.trim().toUpperCase());
        }
        if (env != null && !env.isBlank()) {
            sql.append(" AND env=?");
            args.add(env.trim().toLowerCase());
        }
        sql.append(" ORDER BY switched_at DESC, id DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 200)));
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }
}

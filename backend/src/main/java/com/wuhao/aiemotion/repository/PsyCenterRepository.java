package com.wuhao.aiemotion.repository;

import com.wuhao.aiemotion.domain.PsyCenter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PsyCenterRepository {

    private final JdbcTemplate jdbcTemplate;

    public PsyCenterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<PsyCenter> ROW_MAPPER = (rs, rowNum) -> new PsyCenter(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("city_code"),
            rs.getString("city_name"),
            rs.getString("district"),
            rs.getString("address"),
            rs.getString("phone"),
            rs.getBigDecimal("latitude"),
            rs.getBigDecimal("longitude"),
            rs.getString("source_name"),
            rs.getString("source_url"),
            rs.getString("source_level"),
            rs.getBoolean("is_recommended"),
            rs.getBoolean("is_enabled"),
            rs.getString("seed_key"),
            rs.getString("data_source"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    public List<PsyCenter> findByCityCode(String cityCode, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM psy_centers
                WHERE is_active = 1
                  AND is_enabled = 1
                  AND city_code = ?
                ORDER BY is_recommended DESC, id DESC
                LIMIT ?
                """, ROW_MAPPER, cityCode, limit);
    }

    public List<PsyCenter> findNearby(double latitude, double longitude, double radiusKm, int limit) {
        return jdbcTemplate.query("""
                SELECT *
                FROM psy_centers
                WHERE is_active = 1
                  AND is_enabled = 1
                  AND latitude IS NOT NULL
                  AND longitude IS NOT NULL
                  AND (6371 * acos(
                    cos(radians(?)) * cos(radians(latitude)) * cos(radians(longitude) - radians(?))
                    + sin(radians(?)) * sin(radians(latitude))
                  )) <= ?
                ORDER BY is_recommended DESC, id DESC
                LIMIT ?
                """, ROW_MAPPER, latitude, longitude, latitude, radiusKm, limit);
    }

    public List<PsyCenter> findAllForAdmin() {
        return jdbcTemplate.query("SELECT * FROM psy_centers ORDER BY id DESC", ROW_MAPPER);
    }

    public void create(PsyCenter center) {
        jdbcTemplate.update("""
                INSERT INTO psy_centers
                (name, city_code, city_name, district, address, phone, latitude, longitude, source_name, source_url,
                 source_level, is_recommended, is_enabled, seed_key, data_source, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, center.name(), center.cityCode(), center.cityName(), center.district(), center.address(),
                center.phone(), center.latitude(), center.longitude(), center.sourceName(), center.sourceUrl(),
                center.sourceLevel(), center.recommended(), center.enabled(), center.seedKey(), center.dataSource(),
                center.isActive());
    }

    public int update(long id, PsyCenter center) {
        return jdbcTemplate.update("""
                UPDATE psy_centers
                SET name=?, city_code=?, city_name=?, district=?, address=?, phone=?, latitude=?, longitude=?,
                    source_name=?, source_url=?, source_level=?, is_recommended=?, is_enabled=?, is_active=?,
                    updated_at=NOW()
                WHERE id=?
                """, center.name(), center.cityCode(), center.cityName(), center.district(), center.address(), center.phone(),
                center.latitude(), center.longitude(), center.sourceName(), center.sourceUrl(), center.sourceLevel(),
                center.recommended(), center.enabled(), center.isActive(), id);
    }

    public int delete(long id) {
        return jdbcTemplate.update("""
                UPDATE psy_centers
                SET is_active=0, updated_at=NOW()
                WHERE id=?
                """, id);
    }

    public boolean existsSeedKey(String seedKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM psy_centers WHERE seed_key=?",
                Integer.class,
                seedKey
        );
        return count != null && count > 0;
    }
}

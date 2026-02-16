package com.wuhao.aiemotion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CoreReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public CoreReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findLatestReportJsonByAnalysisId(long analysisId) {
        List<String> list = jdbcTemplate.query(
                "SELECT CAST(report_json AS CHAR) AS report_json " +
                        "FROM core_report WHERE analysis_id=? " +
                        "ORDER BY created_at DESC, id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString("report_json"),
                analysisId
        );
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }

    /**
     * ✅ 覆盖写：每个 analysis_id 只保留一条报告
     * 依赖 UNIQUE KEY uk_core_report_analysis (analysis_id)
     */
    public void upsert(long analysisId, String title, String reportJson) {
        String sql =
                "INSERT INTO core_report (analysis_id, title, report_json) " +
                        "VALUES (?, ?, CAST(? AS JSON)) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "title=VALUES(title), " +
                        "report_json=CAST(? AS JSON), " +
                        "created_at=CURRENT_TIMESTAMP(3)";

        jdbcTemplate.update(sql,
                analysisId,
                (title == null || title.isBlank()) ? "Emotion Report" : title,
                reportJson,
                reportJson
        );
    }
}

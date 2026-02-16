package com.wuhao.aiemotion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class CmsRepository {

    private final JdbcTemplate jdbcTemplate;

    public CmsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> list(String tableName) {
        return jdbcTemplate.queryForList("SELECT * FROM " + tableName + " ORDER BY id DESC");
    }

    public void createBanner(String title, String imageUrl, String linkUrl, Integer sortOrder, boolean recommended, boolean enabled,
                             LocalDateTime startsAt, LocalDateTime endsAt) {
        jdbcTemplate.update("""
                INSERT INTO banners(title, image_url, link_url, sort_order, is_recommended, is_enabled, starts_at, ends_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, title, imageUrl, linkUrl, sortOrder, recommended, enabled, startsAt, endsAt);
    }

    public int updateBanner(long id, String title, String imageUrl, String linkUrl, Integer sortOrder, boolean recommended, boolean enabled,
                            LocalDateTime startsAt, LocalDateTime endsAt) {
        return jdbcTemplate.update("""
                UPDATE banners
                SET title=?, image_url=?, link_url=?, sort_order=?, is_recommended=?, is_enabled=?, starts_at=?, ends_at=?, updated_at=NOW()
                WHERE id=?
                """, title, imageUrl, linkUrl, sortOrder, recommended, enabled, startsAt, endsAt, id);
    }

    public int deleteById(String tableName, long id) {
        return jdbcTemplate.update("DELETE FROM " + tableName + " WHERE id=?", id);
    }

    public void createQuote(String content, String author, Integer sortOrder, boolean recommended, boolean enabled) {
        jdbcTemplate.update("""
                INSERT INTO quotes(content, author, sort_order, is_recommended, is_enabled)
                VALUES (?, ?, ?, ?, ?)
                """, content, author, sortOrder, recommended, enabled);
    }

    public int updateQuote(long id, String content, String author, Integer sortOrder, boolean recommended, boolean enabled) {
        return jdbcTemplate.update("""
                UPDATE quotes
                SET content=?, author=?, sort_order=?, is_recommended=?, is_enabled=?, updated_at=NOW()
                WHERE id=?
                """, content, author, sortOrder, recommended, enabled, id);
    }

    public void createArticle(String title, String coverImageUrl, String summary, String contentUrl, Integer sortOrder,
                              boolean recommended, boolean enabled, LocalDateTime publishedAt) {
        jdbcTemplate.update("""
                INSERT INTO articles(title, cover_image_url, summary, content_url, sort_order, is_recommended, is_enabled, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, title, coverImageUrl, summary, contentUrl, sortOrder, recommended, enabled, publishedAt);
    }

    public int updateArticle(long id, String title, String coverImageUrl, String summary, String contentUrl, Integer sortOrder,
                             boolean recommended, boolean enabled, LocalDateTime publishedAt) {
        return jdbcTemplate.update("""
                UPDATE articles
                SET title=?, cover_image_url=?, summary=?, content_url=?, sort_order=?, is_recommended=?, is_enabled=?, published_at=?, updated_at=NOW()
                WHERE id=?
                """, title, coverImageUrl, summary, contentUrl, sortOrder, recommended, enabled, publishedAt, id);
    }

    public void createBook(String title, String author, String coverImageUrl, String description, String purchaseUrl, Integer sortOrder,
                           boolean recommended, boolean enabled) {
        jdbcTemplate.update("""
                INSERT INTO books(title, author, cover_image_url, description, purchase_url, sort_order, is_recommended, is_enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, title, author, coverImageUrl, description, purchaseUrl, sortOrder, recommended, enabled);
    }

    public int updateBook(long id, String title, String author, String coverImageUrl, String description, String purchaseUrl, Integer sortOrder,
                          boolean recommended, boolean enabled) {
        return jdbcTemplate.update("""
                UPDATE books
                SET title=?, author=?, cover_image_url=?, description=?, purchase_url=?, sort_order=?, is_recommended=?, is_enabled=?, updated_at=NOW()
                WHERE id=?
                """, title, author, coverImageUrl, description, purchaseUrl, sortOrder, recommended, enabled, id);
    }

    public void saveContentClick(String contentType, Long contentId) {
        jdbcTemplate.update("""
                INSERT INTO content_events(event_type, content_type, content_id, event_date)
                VALUES ('CLICK', ?, ?, ?)
                """, contentType, contentId, LocalDate.now());
    }

    public long totalUploads() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audio_file", Long.class);
        return count == null ? 0L : count;
    }

    public long totalReports() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report_resource WHERE deleted_at IS NULL", Long.class);
        return count == null ? 0L : count;
    }

    public List<Map<String, Object>> clickByContentType() {
        return jdbcTemplate.queryForList("""
                SELECT content_type, COUNT(*) AS click_count
                FROM content_events
                WHERE event_type='CLICK'
                GROUP BY content_type
                ORDER BY click_count DESC
                """);
    }
}

package com.wuhao.aiemotion.repository;

import com.wuhao.aiemotion.domain.Article;
import com.wuhao.aiemotion.domain.Book;
import com.wuhao.aiemotion.domain.Quote;
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

    public List<Map<String, Object>> listBanners() {
        return jdbcTemplate.queryForList("""
                SELECT id,
                       title,
                       image_url AS imageUrl,
                       link_url AS linkUrl,
                       sort_order AS sortOrder,
                       is_recommended AS recommended,
                       is_enabled AS enabled,
                       starts_at AS startsAt,
                       ends_at AS endsAt,
                       created_at AS createdAt,
                       updated_at AS updatedAt
                FROM banners
                ORDER BY id DESC
                """);
    }

    public List<Map<String, Object>> listQuotes() {
        return jdbcTemplate.queryForList("""
                SELECT id,
                       content,
                       author,
                       sort_order AS sortOrder,
                       is_recommended AS recommended,
                       is_enabled AS enabled,
                       seed_key AS seedKey,
                       data_source AS dataSource,
                       is_active AS isActive,
                       created_at AS createdAt,
                       updated_at AS updatedAt
                FROM quotes
                ORDER BY id DESC
                """);
    }

    public List<Map<String, Object>> listArticles() {
        return jdbcTemplate.queryForList("""
                SELECT id,
                       title,
                       cover_image_url AS coverImageUrl,
                       summary,
                       recommend_reason AS recommendReason,
                       fit_for AS fitFor,
                       highlights,
                       reading_minutes AS readingMinutes,
                       category,
                       source_name AS sourceName,
                       COALESCE(source_url, content_url) AS sourceUrl,
                       COALESCE(source_url, content_url) AS contentUrl,
                       is_external AS isExternal,
                       difficulty_tag AS difficultyTag,
                       sort_order AS sortOrder,
                       is_recommended AS recommended,
                       is_enabled AS enabled,
                       seed_key AS seedKey,
                       data_source AS dataSource,
                       is_active AS isActive,
                       published_at AS publishedAt,
                       created_at AS createdAt,
                       updated_at AS updatedAt
                FROM articles
                ORDER BY id DESC
                """);
    }

    public List<Map<String, Object>> listBooks() {
        return jdbcTemplate.queryForList("""
                SELECT id,
                       title,
                       author,
                       cover_image_url AS coverImageUrl,
                       description,
                       category,
                       recommend_reason AS recommendReason,
                       fit_for AS fitFor,
                       highlights,
                       purchase_url AS purchaseUrl,
                       sort_order AS sortOrder,
                       is_recommended AS recommended,
                       is_enabled AS enabled,
                       seed_key AS seedKey,
                       data_source AS dataSource,
                       is_active AS isActive,
                       created_at AS createdAt,
                       updated_at AS updatedAt
                FROM books
                ORDER BY id DESC
                """);
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

    public int deleteBannerById(long id) {
        return jdbcTemplate.update("DELETE FROM banners WHERE id=?", id);
    }

    public void createQuote(String content, String author, Integer sortOrder, boolean recommended, boolean enabled, boolean isActive) {
        jdbcTemplate.update("""
                INSERT INTO quotes(content, author, sort_order, is_recommended, is_enabled, seed_key, data_source, is_active)
                VALUES (?, ?, ?, ?, ?, NULL, 'manual', ?)
                """, content, author, sortOrder, recommended, enabled, isActive);
    }

    public int updateQuote(long id, String content, String author, Integer sortOrder, boolean recommended, boolean enabled, boolean isActive) {
        return jdbcTemplate.update("""
                UPDATE quotes
                SET content=?, author=?, sort_order=?, is_recommended=?, is_enabled=?, is_active=?, updated_at=NOW()
                WHERE id=?
                """, content, author, sortOrder, recommended, enabled, isActive, id);
    }

    public int softDeleteQuote(long id) {
        return jdbcTemplate.update("""
                UPDATE quotes
                SET is_active=0, updated_at=NOW()
                WHERE id=?
                """, id);
    }

    public void createArticle(String title, String coverImageUrl, String summary, String recommendReason, String fitFor,
                              String highlights, Integer readingMinutes, String category, String sourceName,
                              String sourceUrl, boolean isExternal, String difficultyTag, Integer sortOrder,
                              boolean recommended, boolean enabled, boolean isActive, LocalDateTime publishedAt) {
        jdbcTemplate.update("""
                INSERT INTO articles(title, cover_image_url, summary, recommend_reason, fit_for, highlights, reading_minutes,
                                     category, source_name, source_url, content_url, is_external, difficulty_tag,
                                     sort_order, is_recommended, is_enabled, seed_key, data_source, is_active, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'manual', ?, ?)
                """, title, coverImageUrl, summary, recommendReason, fitFor, highlights, readingMinutes, category,
                sourceName, sourceUrl, sourceUrl, isExternal, difficultyTag, sortOrder, recommended, enabled, isActive, publishedAt);
    }

    public int updateArticle(long id, String title, String coverImageUrl, String summary, String recommendReason,
                             String fitFor, String highlights, Integer readingMinutes, String category,
                             String sourceName, String sourceUrl, boolean isExternal, String difficultyTag,
                             Integer sortOrder, boolean recommended, boolean enabled, boolean isActive,
                             LocalDateTime publishedAt) {
        return jdbcTemplate.update("""
                UPDATE articles
                SET title=?, cover_image_url=?, summary=?, recommend_reason=?, fit_for=?, highlights=?, reading_minutes=?,
                    category=?, source_name=?, source_url=?, content_url=?, is_external=?, difficulty_tag=?, sort_order=?,
                    is_recommended=?, is_enabled=?, is_active=?, published_at=?, updated_at=NOW()
                WHERE id=?
                """, title, coverImageUrl, summary, recommendReason, fitFor, highlights, readingMinutes, category,
                sourceName, sourceUrl, sourceUrl, isExternal, difficultyTag, sortOrder, recommended, enabled,
                isActive, publishedAt, id);
    }

    public int softDeleteArticle(long id) {
        return jdbcTemplate.update("""
                UPDATE articles
                SET is_active=0, updated_at=NOW()
                WHERE id=?
                """, id);
    }

    public void createBook(String title, String author, String coverImageUrl, String description, String category,
                           String recommendReason, String fitFor, String highlights, String purchaseUrl, Integer sortOrder,
                           boolean recommended, boolean enabled, boolean isActive) {
        jdbcTemplate.update("""
                INSERT INTO books(title, author, cover_image_url, description, category, recommend_reason, fit_for, highlights,
                                  purchase_url, sort_order, is_recommended, is_enabled, seed_key, data_source, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'manual', ?)
                """, title, author, coverImageUrl, description, category, recommendReason, fitFor, highlights,
                purchaseUrl, sortOrder, recommended, enabled, isActive);
    }

    public int updateBook(long id, String title, String author, String coverImageUrl, String description, String category,
                          String recommendReason, String fitFor, String highlights, String purchaseUrl, Integer sortOrder,
                          boolean recommended, boolean enabled, boolean isActive) {
        return jdbcTemplate.update("""
                UPDATE books
                SET title=?, author=?, cover_image_url=?, description=?, category=?, recommend_reason=?, fit_for=?, highlights=?,
                    purchase_url=?, sort_order=?, is_recommended=?, is_enabled=?, is_active=?, updated_at=NOW()
                WHERE id=?
                """, title, author, coverImageUrl, description, category, recommendReason, fitFor, highlights,
                purchaseUrl, sortOrder, recommended, enabled, isActive, id);
    }

    public int softDeleteBook(long id) {
        return jdbcTemplate.update("""
                UPDATE books
                SET is_active=0, updated_at=NOW()
                WHERE id=?
                """, id);
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

    public boolean quoteSeedExists(String seedKey) {
        return seedKeyExists("quotes", seedKey);
    }

    public boolean articleSeedExists(String seedKey) {
        return seedKeyExists("articles", seedKey);
    }

    public boolean bookSeedExists(String seedKey) {
        return seedKeyExists("books", seedKey);
    }

    public void insertSeedQuote(Quote quote) {
        jdbcTemplate.update("""
                INSERT INTO quotes(content, author, sort_order, is_recommended, is_enabled, seed_key, data_source, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, quote.content(), quote.author(), quote.sortOrder(), quote.recommended(), quote.enabled(),
                quote.seedKey(), quote.dataSource(), quote.isActive());
    }

    public void insertSeedArticle(Article article) {
        jdbcTemplate.update("""
                INSERT INTO articles(title, cover_image_url, summary, recommend_reason, fit_for, highlights, reading_minutes,
                                     category, source_name, source_url, content_url, is_external, difficulty_tag,
                                     sort_order, is_recommended, is_enabled, seed_key, data_source, is_active, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, article.title(), article.coverImageUrl(), article.summary(), article.recommendReason(), article.fitFor(),
                article.highlights(), article.readingMinutes(), article.category(), article.sourceName(), article.sourceUrl(),
                article.sourceUrl(), article.isExternal(), article.difficultyTag(), article.sortOrder(), article.recommended(),
                article.enabled(), article.seedKey(), article.dataSource(), article.isActive(), article.publishedAt());
    }

    public void insertSeedBook(Book book) {
        jdbcTemplate.update("""
                INSERT INTO books(title, author, cover_image_url, description, category, recommend_reason, fit_for, highlights,
                                  purchase_url, sort_order, is_recommended, is_enabled, seed_key, data_source, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, book.title(), book.author(), book.coverImageUrl(), book.description(), book.category(),
                book.recommendReason(), book.fitFor(), book.highlights(), book.purchaseUrl(), book.sortOrder(),
                book.recommended(), book.enabled(), book.seedKey(), book.dataSource(), book.isActive());
    }

    private boolean seedKeyExists(String tableName, String seedKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE seed_key=?",
                Integer.class,
                seedKey
        );
        return count != null && count > 0;
    }
}

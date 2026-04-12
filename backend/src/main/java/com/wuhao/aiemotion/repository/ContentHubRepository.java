package com.wuhao.aiemotion.repository;

import com.wuhao.aiemotion.domain.Article;
import com.wuhao.aiemotion.domain.Book;
import com.wuhao.aiemotion.domain.DailySchedule;
import com.wuhao.aiemotion.domain.DailyScheduleItem;
import com.wuhao.aiemotion.domain.Quote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

@Repository
public class ContentHubRepository {

    private final JdbcTemplate jdbcTemplate;

    public ContentHubRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Quote> QUOTE_ROW_MAPPER = (rs, rowNum) -> new Quote(
            rs.getLong("id"),
            rs.getString("content"),
            rs.getString("author"),
            rs.getInt("sort_order"),
            rs.getBoolean("is_recommended"),
            rs.getBoolean("is_enabled"),
            rs.getString("seed_key"),
            rs.getString("data_source"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private static final RowMapper<Article> ARTICLE_ROW_MAPPER = (rs, rowNum) -> new Article(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("cover_image_url"),
            rs.getString("summary"),
            rs.getString("recommend_reason"),
            rs.getString("fit_for"),
            rs.getString("highlights"),
            rs.getObject("reading_minutes") == null ? null : rs.getInt("reading_minutes"),
            rs.getString("category"),
            rs.getString("source_name"),
            resolveSourceUrl(rs.getString("source_url"), rs.getString("content_url")),
            resolveSourceUrl(rs.getString("source_url"), rs.getString("content_url")),
            rs.getObject("is_external") == null ? null : rs.getBoolean("is_external"),
            rs.getString("difficulty_tag"),
            rs.getInt("sort_order"),
            rs.getBoolean("is_recommended"),
            rs.getBoolean("is_enabled"),
            rs.getString("seed_key"),
            rs.getString("data_source"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toLocalDateTime(),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private static final RowMapper<Book> BOOK_ROW_MAPPER = (rs, rowNum) -> new Book(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("author"),
            rs.getString("cover_image_url"),
            rs.getString("description"),
            rs.getString("category"),
            rs.getString("recommend_reason"),
            rs.getString("fit_for"),
            rs.getString("highlights"),
            rs.getString("purchase_url"),
            rs.getInt("sort_order"),
            rs.getBoolean("is_recommended"),
            rs.getBoolean("is_enabled"),
            rs.getString("seed_key"),
            rs.getString("data_source"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private static final RowMapper<DailySchedule> SCHEDULE_ROW_MAPPER = (rs, rowNum) -> new DailySchedule(
            rs.getLong("id"),
            rs.getDate("schedule_date").toLocalDate(),
            rs.getString("theme_key"),
            rs.getString("theme_title"),
            rs.getString("theme_subtitle"),
            rs.getLong("quote_id"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private static final RowMapper<DailyScheduleItem> SCHEDULE_ITEM_ROW_MAPPER = (rs, rowNum) -> new DailyScheduleItem(
            rs.getLong("id"),
            rs.getLong("schedule_id"),
            rs.getString("content_type"),
            rs.getLong("content_id"),
            rs.getString("slot_role"),
            rs.getInt("sort_order"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    public Optional<DailySchedule> findActiveScheduleByDate(LocalDate date) {
        List<DailySchedule> rows = jdbcTemplate.query("""
                SELECT *
                FROM content_daily_schedule
                WHERE schedule_date = ?
                  AND status = 'ACTIVE'
                LIMIT 1
                """, SCHEDULE_ROW_MAPPER, Date.valueOf(date));
        return rows.stream().findFirst();
    }

    public List<DailySchedule> listSchedules(LocalDate date) {
        if (date != null) {
            return jdbcTemplate.query("""
                    SELECT *
                    FROM content_daily_schedule
                    WHERE schedule_date = ?
                    ORDER BY schedule_date DESC, id DESC
                    """, SCHEDULE_ROW_MAPPER, Date.valueOf(date));
        }
        return jdbcTemplate.query("""
                SELECT *
                FROM content_daily_schedule
                ORDER BY schedule_date DESC, id DESC
                LIMIT 180
                """, SCHEDULE_ROW_MAPPER);
    }

    public boolean scheduleDateExists(LocalDate date, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM content_daily_schedule WHERE schedule_date = ?";
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(date));
        if (excludeId != null) {
            sql += " AND id <> ?";
            args.add(excludeId);
        }
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args.toArray());
        return count != null && count > 0;
    }

    public List<DailyScheduleItem> listScheduleItems(long scheduleId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM content_daily_item
                WHERE schedule_id = ?
                ORDER BY CASE slot_role WHEN 'FEATURED' THEN 0 ELSE 1 END, sort_order ASC, id ASC
                """, SCHEDULE_ITEM_ROW_MAPPER, scheduleId);
    }

    public Map<Long, List<DailyScheduleItem>> listScheduleItems(Collection<Long> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>(scheduleIds);
        String placeholders = placeholders(ids.size());
        List<DailyScheduleItem> items = jdbcTemplate.query("""
                SELECT *
                FROM content_daily_item
                WHERE schedule_id IN (""" + placeholders + """
                )
                ORDER BY schedule_id ASC, CASE slot_role WHEN 'FEATURED' THEN 0 ELSE 1 END, sort_order ASC, id ASC
                """, SCHEDULE_ITEM_ROW_MAPPER, ids.toArray());
        Map<Long, List<DailyScheduleItem>> grouped = new LinkedHashMap<>();
        for (DailyScheduleItem item : items) {
            grouped.computeIfAbsent(item.scheduleId(), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    public long createSchedule(LocalDate scheduleDate, String themeKey, String themeTitle, String themeSubtitle,
                               Long quoteId, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO content_daily_schedule(schedule_date, theme_key, theme_title, theme_subtitle, quote_id, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setDate(1, Date.valueOf(scheduleDate));
            ps.setString(2, themeKey);
            ps.setString(3, themeTitle);
            ps.setString(4, themeSubtitle);
            ps.setLong(5, quoteId);
            ps.setString(6, status);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to create content daily schedule");
        }
        return key.longValue();
    }

    public int updateSchedule(long id, LocalDate scheduleDate, String themeKey, String themeTitle, String themeSubtitle,
                              Long quoteId, String status) {
        return jdbcTemplate.update("""
                UPDATE content_daily_schedule
                SET schedule_date = ?, theme_key = ?, theme_title = ?, theme_subtitle = ?, quote_id = ?, status = ?, updated_at = NOW()
                WHERE id = ?
                """, Date.valueOf(scheduleDate), themeKey, themeTitle, themeSubtitle, quoteId, status, id);
    }

    public int deleteSchedule(long id) {
        return jdbcTemplate.update("DELETE FROM content_daily_schedule WHERE id = ?", id);
    }

    public void replaceScheduleItems(long scheduleId, List<ScheduleItemMutation> items) {
        jdbcTemplate.update("DELETE FROM content_daily_item WHERE schedule_id = ?", scheduleId);
        if (items == null || items.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO content_daily_item(schedule_id, content_type, content_id, slot_role, sort_order)
                VALUES (?, ?, ?, ?, ?)
                """, items, items.size(), (ps, item) -> {
            ps.setLong(1, scheduleId);
            ps.setString(2, item.contentType());
            ps.setLong(3, item.contentId());
            ps.setString(4, item.slotRole());
            ps.setInt(5, item.sortOrder());
        });
    }

    public boolean scheduleUsesOnlySeedContent(long scheduleId) {
        Integer nonSeedQuoteCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM content_daily_schedule s
                LEFT JOIN quotes q ON q.id = s.quote_id
                WHERE s.id = ?
                  AND (q.id IS NULL OR q.data_source <> 'seed')
                """, Integer.class, scheduleId);
        if (nonSeedQuoteCount != null && nonSeedQuoteCount > 0) {
            return false;
        }

        Integer nonSeedItemCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM content_daily_item i
                LEFT JOIN articles a ON i.content_type = 'ARTICLE' AND a.id = i.content_id
                LEFT JOIN books b ON i.content_type = 'BOOK' AND b.id = i.content_id
                WHERE i.schedule_id = ?
                  AND (
                    (i.content_type = 'ARTICLE' AND (a.id IS NULL OR a.data_source <> 'seed'))
                    OR
                    (i.content_type = 'BOOK' AND (b.id IS NULL OR b.data_source <> 'seed'))
                  )
                """, Integer.class, scheduleId);
        return nonSeedItemCount == null || nonSeedItemCount == 0;
    }

    public boolean existsActiveQuote(long quoteId) {
        return exists("""
                SELECT COUNT(*)
                FROM quotes
                WHERE id = ?
                  AND is_active = 1
                  AND is_enabled = 1
                """, quoteId);
    }

    public boolean existsActiveArticle(long articleId) {
        return exists("""
                SELECT COUNT(*)
                FROM articles
                WHERE id = ?
                  AND is_active = 1
                  AND is_enabled = 1
                """, articleId);
    }

    public boolean existsActiveBook(long bookId) {
        return exists("""
                SELECT COUNT(*)
                FROM books
                WHERE id = ?
                  AND is_active = 1
                  AND is_enabled = 1
                """, bookId);
    }

    public Optional<Quote> findActiveQuoteById(Long quoteId) {
        if (quoteId == null) {
            return Optional.empty();
        }
        List<Quote> rows = jdbcTemplate.query("""
                SELECT *
                FROM quotes
                WHERE id = ?
                  AND is_active = 1
                  AND is_enabled = 1
                LIMIT 1
                """, QUOTE_ROW_MAPPER, quoteId);
        return rows.stream().findFirst();
    }

    public Optional<Article> findActiveArticleById(long articleId) {
        List<Article> rows = jdbcTemplate.query("""
                SELECT *
                FROM articles
                WHERE id = ?
                  AND is_active = 1
                  AND is_enabled = 1
                LIMIT 1
                """, ARTICLE_ROW_MAPPER, articleId);
        return rows.stream().findFirst();
    }

    public Optional<Book> findActiveBookById(long bookId) {
        List<Book> rows = jdbcTemplate.query("""
                SELECT *
                FROM books
                WHERE id = ?
                  AND is_active = 1
                  AND is_enabled = 1
                LIMIT 1
                """, BOOK_ROW_MAPPER, bookId);
        return rows.stream().findFirst();
    }

    public List<Article> findActiveArticlesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(ids.size());
        return jdbcTemplate.query("""
                SELECT *
                FROM articles
                WHERE id IN (""" + placeholders + """
                )
                  AND is_active = 1
                  AND is_enabled = 1
                """, ARTICLE_ROW_MAPPER, ids.toArray());
    }

    public List<Book> findActiveBooksByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(ids.size());
        return jdbcTemplate.query("""
                SELECT *
                FROM books
                WHERE id IN (""" + placeholders + """
                )
                  AND is_active = 1
                  AND is_enabled = 1
                """, BOOK_ROW_MAPPER, ids.toArray());
    }

    public List<Article> findActiveArticlesByCategory(String category, List<Long> excludeIds, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM articles
                WHERE is_active = 1
                  AND is_enabled = 1
                """);
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            args.add(category.trim());
        }
        appendExcludeIds(sql, args, "id", excludeIds);
        sql.append(" ORDER BY is_recommended DESC, sort_order ASC, published_at DESC, id DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), ARTICLE_ROW_MAPPER, args.toArray());
    }

    public List<Book> findActiveBooksByCategory(String category, List<Long> excludeIds, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM books
                WHERE is_active = 1
                  AND is_enabled = 1
                """);
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            args.add(category.trim());
        }
        appendExcludeIds(sql, args, "id", excludeIds);
        sql.append(" ORDER BY is_recommended DESC, sort_order ASC, id DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), BOOK_ROW_MAPPER, args.toArray());
    }

    public List<LocalDate> listArchiveDates(int limit) {
        return jdbcTemplate.query("""
                SELECT schedule_date
                FROM content_daily_schedule
                WHERE status = 'ACTIVE'
                ORDER BY schedule_date DESC
                LIMIT ?
                """, (rs, rowNum) -> rs.getDate("schedule_date").toLocalDate(), limit);
    }

    public List<Map<String, Object>> listRecentHistory(long userId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT 'ARTICLE' AS contentType,
                           h.content_id AS contentId,
                           a.title AS title,
                           a.cover_image_url AS coverImageUrl,
                           a.summary AS description,
                           a.category AS category,
                           a.source_name AS subtitle,
                           h.last_viewed_at AS lastViewedAt,
                           h.last_outbound_at AS lastOutboundAt,
                           h.view_count AS viewCount
                    FROM user_content_history h
                    JOIN articles a ON a.id = h.content_id
                    WHERE h.user_id = ?
                      AND h.content_type = 'ARTICLE'
                      AND a.is_active = 1
                      AND a.is_enabled = 1
                    UNION ALL
                    SELECT 'BOOK' AS contentType,
                           h.content_id AS contentId,
                           b.title AS title,
                           b.cover_image_url AS coverImageUrl,
                           b.description AS description,
                           b.category AS category,
                           b.author AS subtitle,
                           h.last_viewed_at AS lastViewedAt,
                           h.last_outbound_at AS lastOutboundAt,
                           h.view_count AS viewCount
                    FROM user_content_history h
                    JOIN books b ON b.id = h.content_id
                    WHERE h.user_id = ?
                      AND h.content_type = 'BOOK'
                      AND b.is_active = 1
                      AND b.is_enabled = 1
                ) history_rows
                ORDER BY COALESCE(lastViewedAt, lastOutboundAt) DESC
                LIMIT ?
                """, userId, userId, limit);
    }

    public void upsertViewHistory(long userId, String contentType, long contentId, LocalDateTime viewedAt) {
        jdbcTemplate.update("""
                INSERT INTO user_content_history(user_id, content_type, content_id, last_viewed_at, view_count)
                VALUES (?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                    last_viewed_at = VALUES(last_viewed_at),
                    view_count = view_count + 1,
                    updated_at = NOW()
                """, userId, contentType, contentId, Timestamp.valueOf(viewedAt));
    }

    public void upsertOutboundHistory(long userId, String contentType, long contentId, LocalDateTime outboundAt) {
        jdbcTemplate.update("""
                INSERT INTO user_content_history(user_id, content_type, content_id, last_outbound_at, view_count)
                VALUES (?, ?, ?, ?, 0)
                ON DUPLICATE KEY UPDATE
                    last_outbound_at = VALUES(last_outbound_at),
                    updated_at = NOW()
                """, userId, contentType, contentId, Timestamp.valueOf(outboundAt));
    }

    public Optional<Long> findQuoteIdBySeedKey(String seedKey) {
        if (seedKey == null || seedKey.isBlank()) {
            return Optional.empty();
        }
        List<Long> rows = jdbcTemplate.query("""
                SELECT id
                FROM quotes
                WHERE seed_key = ?
                  AND is_active = 1
                  AND is_enabled = 1
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), seedKey.trim());
        return rows.stream().findFirst();
    }

    public Optional<Long> findArticleIdBySeedKey(String seedKey) {
        if (seedKey == null || seedKey.isBlank()) {
            return Optional.empty();
        }
        List<Long> rows = jdbcTemplate.query("""
                SELECT id
                FROM articles
                WHERE seed_key = ?
                  AND is_active = 1
                  AND is_enabled = 1
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), seedKey.trim());
        return rows.stream().findFirst();
    }

    public Optional<Long> findBookIdBySeedKey(String seedKey) {
        if (seedKey == null || seedKey.isBlank()) {
            return Optional.empty();
        }
        List<Long> rows = jdbcTemplate.query("""
                SELECT id
                FROM books
                WHERE seed_key = ?
                  AND is_active = 1
                  AND is_enabled = 1
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), seedKey.trim());
        return rows.stream().findFirst();
    }

    private boolean exists(String sql, long id) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    private static String resolveSourceUrl(String sourceUrl, String contentUrl) {
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            return sourceUrl;
        }
        return contentUrl;
    }

    private static String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private static void appendExcludeIds(StringBuilder sql, List<Object> args, String column, List<Long> excludeIds) {
        if (excludeIds == null || excludeIds.isEmpty()) {
            return;
        }
        StringJoiner joiner = new StringJoiner(",", " AND " + column + " NOT IN (", ")");
        for (Long excludeId : excludeIds) {
            joiner.add("?");
            args.add(excludeId);
        }
        sql.append(joiner);
    }

    public record ScheduleItemMutation(String contentType, Long contentId, String slotRole, Integer sortOrder) {}
}

package com.wuhao.aiemotion.repository;

import com.wuhao.aiemotion.domain.Article;
import com.wuhao.aiemotion.domain.Banner;
import com.wuhao.aiemotion.domain.Book;
import com.wuhao.aiemotion.domain.Quote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class HomeContentRepository {

    private final JdbcTemplate jdbcTemplate;

    public HomeContentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Banner> BANNER_ROW_MAPPER = (rs, rowNum) -> new Banner(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("image_url"),
            rs.getString("link_url"),
            rs.getInt("sort_order"),
            rs.getBoolean("is_recommended"),
            rs.getBoolean("is_enabled"),
            rs.getTimestamp("starts_at") == null ? null : rs.getTimestamp("starts_at").toLocalDateTime(),
            rs.getTimestamp("ends_at") == null ? null : rs.getTimestamp("ends_at").toLocalDateTime(),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private static final RowMapper<Quote> QUOTE_ROW_MAPPER = (rs, rowNum) -> new Quote(
            rs.getLong("id"),
            rs.getString("content"),
            rs.getString("author"),
            rs.getInt("sort_order"),
            rs.getBoolean("is_recommended"),
            rs.getBoolean("is_enabled"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private static final RowMapper<Article> ARTICLE_ROW_MAPPER = (rs, rowNum) -> new Article(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("cover_image_url"),
            rs.getString("summary"),
            rs.getString("content_url"),
            rs.getInt("sort_order"),
            rs.getBoolean("is_recommended"),
            rs.getBoolean("is_enabled"),
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
            rs.getString("purchase_url"),
            rs.getInt("sort_order"),
            rs.getBoolean("is_recommended"),
            rs.getBoolean("is_enabled"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    public List<Banner> homeBanners(int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM banners
                WHERE is_enabled = 1
                  AND (starts_at IS NULL OR starts_at <= NOW())
                  AND (ends_at IS NULL OR ends_at >= NOW())
                ORDER BY is_recommended DESC, sort_order ASC, id DESC
                LIMIT ?
                """, BANNER_ROW_MAPPER, limit);
    }

    public List<Quote> homeQuotes(int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM quotes
                WHERE is_enabled = 1
                ORDER BY is_recommended DESC, sort_order ASC, id DESC
                LIMIT ?
                """, QUOTE_ROW_MAPPER, limit);
    }

    public List<Article> homeArticles(int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM articles
                WHERE is_enabled = 1
                ORDER BY is_recommended DESC, sort_order ASC, published_at DESC
                LIMIT ?
                """, ARTICLE_ROW_MAPPER, limit);
    }

    public List<Book> homeBooks(int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM books
                WHERE is_enabled = 1
                ORDER BY is_recommended DESC, sort_order ASC, id DESC
                LIMIT ?
                """, BOOK_ROW_MAPPER, limit);
    }
}

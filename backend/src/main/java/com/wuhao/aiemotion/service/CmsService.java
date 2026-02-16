package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.repository.CmsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class CmsService {

    private final CmsRepository cmsRepository;

    public CmsService(CmsRepository cmsRepository) {
        this.cmsRepository = cmsRepository;
    }

    public List<Map<String, Object>> listBanners() { return cmsRepository.list("banners"); }
    public List<Map<String, Object>> listQuotes() { return cmsRepository.list("quotes"); }
    public List<Map<String, Object>> listArticles() { return cmsRepository.list("articles"); }
    public List<Map<String, Object>> listBooks() { return cmsRepository.list("books"); }

    public void createBanner(String title, String imageUrl, String linkUrl, Integer sortOrder, boolean recommended, boolean enabled,
                             LocalDateTime startsAt, LocalDateTime endsAt) {
        cmsRepository.createBanner(title, imageUrl, linkUrl, normalizeSort(sortOrder), recommended, enabled, startsAt, endsAt);
    }

    public void updateBanner(long id, String title, String imageUrl, String linkUrl, Integer sortOrder, boolean recommended, boolean enabled,
                             LocalDateTime startsAt, LocalDateTime endsAt) {
        if (cmsRepository.updateBanner(id, title, imageUrl, linkUrl, normalizeSort(sortOrder), recommended, enabled, startsAt, endsAt) == 0) {
            throw new IllegalArgumentException("Banner 不存在: " + id);
        }
    }

    public void createQuote(String content, String author, Integer sortOrder, boolean recommended, boolean enabled) {
        cmsRepository.createQuote(content, author, normalizeSort(sortOrder), recommended, enabled);
    }

    public void updateQuote(long id, String content, String author, Integer sortOrder, boolean recommended, boolean enabled) {
        if (cmsRepository.updateQuote(id, content, author, normalizeSort(sortOrder), recommended, enabled) == 0) {
            throw new IllegalArgumentException("Quote 不存在: " + id);
        }
    }

    public void createArticle(String title, String coverImageUrl, String summary, String contentUrl, Integer sortOrder,
                              boolean recommended, boolean enabled, LocalDateTime publishedAt) {
        cmsRepository.createArticle(title, coverImageUrl, summary, contentUrl, normalizeSort(sortOrder), recommended, enabled, publishedAt);
    }

    public void updateArticle(long id, String title, String coverImageUrl, String summary, String contentUrl, Integer sortOrder,
                              boolean recommended, boolean enabled, LocalDateTime publishedAt) {
        if (cmsRepository.updateArticle(id, title, coverImageUrl, summary, contentUrl, normalizeSort(sortOrder), recommended, enabled, publishedAt) == 0) {
            throw new IllegalArgumentException("Article 不存在: " + id);
        }
    }

    public void createBook(String title, String author, String coverImageUrl, String description, String purchaseUrl, Integer sortOrder,
                           boolean recommended, boolean enabled) {
        cmsRepository.createBook(title, author, coverImageUrl, description, purchaseUrl, normalizeSort(sortOrder), recommended, enabled);
    }

    public void updateBook(long id, String title, String author, String coverImageUrl, String description, String purchaseUrl, Integer sortOrder,
                           boolean recommended, boolean enabled) {
        if (cmsRepository.updateBook(id, title, author, coverImageUrl, description, purchaseUrl, normalizeSort(sortOrder), recommended, enabled) == 0) {
            throw new IllegalArgumentException("Book 不存在: " + id);
        }
    }

    public void deleteBanner(long id) { deleteById("banners", id, "Banner"); }
    public void deleteQuote(long id) { deleteById("quotes", id, "Quote"); }
    public void deleteArticle(long id) { deleteById("articles", id, "Article"); }
    public void deleteBook(long id) { deleteById("books", id, "Book"); }

    public void recordClick(String contentType, Long contentId) {
        cmsRepository.saveContentClick(contentType, contentId);
    }

    public Map<String, Object> lightDashboard() {
        return Map.of(
                "uploadCount", cmsRepository.totalUploads(),
                "reportCount", cmsRepository.totalReports(),
                "contentClickStats", cmsRepository.clickByContentType()
        );
    }

    private Integer normalizeSort(Integer sortOrder) {
        return sortOrder == null ? 100 : sortOrder;
    }

    private void deleteById(String table, long id, String label) {
        if (cmsRepository.deleteById(table, id) == 0) {
            throw new IllegalArgumentException(label + " 不存在: " + id);
        }
    }
}

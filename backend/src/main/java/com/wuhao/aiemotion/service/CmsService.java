package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.repository.CmsRepository;
import com.wuhao.aiemotion.repository.ContentHubRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CmsService {

    private final CmsRepository cmsRepository;
    private final ContentHubRepository contentHubRepository;

    public CmsService(CmsRepository cmsRepository, ContentHubRepository contentHubRepository) {
        this.cmsRepository = cmsRepository;
        this.contentHubRepository = contentHubRepository;
    }

    public List<Map<String, Object>> listBanners() { return cmsRepository.listBanners(); }
    public List<Map<String, Object>> listQuotes() { return cmsRepository.listQuotes(); }
    public List<Map<String, Object>> listArticles() { return cmsRepository.listArticles(); }
    public List<Map<String, Object>> listBooks() { return cmsRepository.listBooks(); }

    public void createBanner(String title, String imageUrl, String linkUrl, Integer sortOrder, boolean recommended, boolean enabled,
                             LocalDateTime startsAt, LocalDateTime endsAt) {
        cmsRepository.createBanner(title, imageUrl, linkUrl, normalizeSort(sortOrder), recommended, enabled, startsAt, endsAt);
    }

    public void updateBanner(long id, String title, String imageUrl, String linkUrl, Integer sortOrder, boolean recommended, boolean enabled,
                             LocalDateTime startsAt, LocalDateTime endsAt) {
        if (cmsRepository.updateBanner(id, title, imageUrl, linkUrl, normalizeSort(sortOrder), recommended, enabled, startsAt, endsAt) == 0) {
            throw new IllegalArgumentException("Banner not found: " + id);
        }
    }

    public void createQuote(String content, String author, Integer sortOrder, boolean recommended, boolean enabled, boolean isActive) {
        cmsRepository.createQuote(content, author, normalizeSort(sortOrder), recommended, enabled, isActive);
    }

    public void updateQuote(long id, String content, String author, Integer sortOrder, boolean recommended, boolean enabled, boolean isActive) {
        if (cmsRepository.updateQuote(id, content, author, normalizeSort(sortOrder), recommended, enabled, isActive) == 0) {
            throw new IllegalArgumentException("Quote not found: " + id);
        }
    }

    public void createArticle(String title, String coverImageUrl, String summary, String recommendReason, String fitFor,
                              String highlights, Integer readingMinutes, String category, String sourceName,
                              String sourceUrl, boolean isExternal, String difficultyTag, Integer sortOrder,
                              boolean recommended, boolean enabled, boolean isActive, LocalDateTime publishedAt) {
        cmsRepository.createArticle(title, coverImageUrl, summary, recommendReason, fitFor, highlights, readingMinutes,
                category, sourceName, sourceUrl, isExternal, difficultyTag, normalizeSort(sortOrder),
                recommended, enabled, isActive, publishedAt);
    }

    public void updateArticle(long id, String title, String coverImageUrl, String summary, String recommendReason,
                              String fitFor, String highlights, Integer readingMinutes, String category,
                              String sourceName, String sourceUrl, boolean isExternal, String difficultyTag,
                              Integer sortOrder, boolean recommended, boolean enabled, boolean isActive,
                              LocalDateTime publishedAt) {
        if (cmsRepository.updateArticle(id, title, coverImageUrl, summary, recommendReason, fitFor, highlights,
                readingMinutes, category, sourceName, sourceUrl, isExternal, difficultyTag,
                normalizeSort(sortOrder), recommended, enabled, isActive, publishedAt) == 0) {
            throw new IllegalArgumentException("Article not found: " + id);
        }
    }

    public void createBook(String title, String author, String coverImageUrl, String description, String category,
                           String recommendReason, String fitFor, String highlights, String purchaseUrl, Integer sortOrder,
                           boolean recommended, boolean enabled, boolean isActive) {
        cmsRepository.createBook(title, author, coverImageUrl, description, category, recommendReason, fitFor, highlights,
                purchaseUrl, normalizeSort(sortOrder), recommended, enabled, isActive);
    }

    public void updateBook(long id, String title, String author, String coverImageUrl, String description, String category,
                           String recommendReason, String fitFor, String highlights, String purchaseUrl, Integer sortOrder,
                           boolean recommended, boolean enabled, boolean isActive) {
        if (cmsRepository.updateBook(id, title, author, coverImageUrl, description, category, recommendReason, fitFor,
                highlights, purchaseUrl, normalizeSort(sortOrder), recommended, enabled, isActive) == 0) {
            throw new IllegalArgumentException("Book not found: " + id);
        }
    }

    public void deleteBanner(long id) {
        if (cmsRepository.deleteBannerById(id) == 0) {
            throw new IllegalArgumentException("Banner not found: " + id);
        }
    }

    public void deleteQuote(long id) {
        if (cmsRepository.softDeleteQuote(id) == 0) {
            throw new IllegalArgumentException("Quote not found: " + id);
        }
    }

    public void deleteArticle(long id) {
        if (cmsRepository.softDeleteArticle(id) == 0) {
            throw new IllegalArgumentException("Article not found: " + id);
        }
    }

    public void deleteBook(long id) {
        if (cmsRepository.softDeleteBook(id) == 0) {
            throw new IllegalArgumentException("Book not found: " + id);
        }
    }

    public void recordClick(String contentType, Long contentId) {
        cmsRepository.saveContentClick(contentType, contentId);
    }

    public List<Map<String, Object>> listSchedules(LocalDate date) {
        List<com.wuhao.aiemotion.domain.DailySchedule> schedules = contentHubRepository.listSchedules(date);
        if (schedules.isEmpty()) {
            return List.of();
        }
        Map<Long, List<com.wuhao.aiemotion.domain.DailyScheduleItem>> itemsBySchedule =
                contentHubRepository.listScheduleItems(
                        schedules.stream().map(com.wuhao.aiemotion.domain.DailySchedule::id).filter(Objects::nonNull).toList()
                );
        Map<Long, Map<String, Object>> quoteById = cmsRepository.listQuotes().stream()
                .collect(Collectors.toMap(
                        row -> toLong(row.get("id")),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, Map<String, Object>> articleById = cmsRepository.listArticles().stream()
                .collect(Collectors.toMap(
                        row -> toLong(row.get("id")),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, Map<String, Object>> bookById = cmsRepository.listBooks().stream()
                .collect(Collectors.toMap(
                        row -> toLong(row.get("id")),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<Map<String, Object>> response = new ArrayList<>();
        for (com.wuhao.aiemotion.domain.DailySchedule schedule : schedules) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", schedule.id());
            row.put("scheduleDate", schedule.scheduleDate());
            row.put("themeKey", schedule.themeKey());
            row.put("themeTitle", schedule.themeTitle());
            row.put("themeSubtitle", schedule.themeSubtitle());
            row.put("quoteId", schedule.quoteId());
            Map<String, Object> quote = quoteById.get(schedule.quoteId());
            row.put("quoteContent", quote == null ? null : quote.get("content"));
            row.put("status", schedule.status());
            row.put("createdAt", schedule.createdAt());
            row.put("updatedAt", schedule.updatedAt());
            row.put("items", itemsBySchedule.getOrDefault(schedule.id(), List.of()).stream()
                    .map(item -> toScheduleItemMap(item, articleById, bookById))
                    .toList());
            response.add(row);
        }
        return response;
    }

    public void createSchedule(LocalDate scheduleDate, String themeKey, String themeTitle, String themeSubtitle,
                               Long quoteId, String status, List<ScheduleItemInput> items) {
        validateSchedule(scheduleDate, themeKey, themeTitle, quoteId, status, items, null);
        long scheduleId = contentHubRepository.createSchedule(scheduleDate, themeKey.trim(), themeTitle.trim(),
                normalizeBlank(themeSubtitle), quoteId, normalizeScheduleStatus(status));
        contentHubRepository.replaceScheduleItems(scheduleId, normalizeScheduleItems(items));
    }

    public void updateSchedule(long id, LocalDate scheduleDate, String themeKey, String themeTitle, String themeSubtitle,
                               Long quoteId, String status, List<ScheduleItemInput> items) {
        validateSchedule(scheduleDate, themeKey, themeTitle, quoteId, status, items, id);
        if (contentHubRepository.updateSchedule(id, scheduleDate, themeKey.trim(), themeTitle.trim(),
                normalizeBlank(themeSubtitle), quoteId, normalizeScheduleStatus(status)) == 0) {
            throw new IllegalArgumentException("Schedule not found: " + id);
        }
        contentHubRepository.replaceScheduleItems(id, normalizeScheduleItems(items));
    }

    public void deleteSchedule(long id) {
        if (contentHubRepository.deleteSchedule(id) == 0) {
            throw new IllegalArgumentException("Schedule not found: " + id);
        }
    }

    public Map<String, Object> lightDashboard() {
        List<Map<String, Object>> clickStats = cmsRepository.clickByContentType();
        long clickCount = clickStats.stream()
                .map(it -> it.get("click_count"))
                .filter(java.util.Objects::nonNull)
                .mapToLong(value -> {
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                    try {
                        return Long.parseLong(String.valueOf(value));
                    } catch (NumberFormatException ignore) {
                        return 0L;
                    }
                })
                .sum();
        return Map.of(
                "uploadCount", cmsRepository.totalUploads(),
                "reportCount", cmsRepository.totalReports(),
                "contentClickCount", clickCount,
                "contentClickStats", clickStats
        );
    }

    private Integer normalizeSort(Integer sortOrder) {
        return sortOrder == null ? 100 : sortOrder;
    }

    private void validateSchedule(LocalDate scheduleDate, String themeKey, String themeTitle, Long quoteId,
                                  String status, List<ScheduleItemInput> items, Long scheduleId) {
        if (scheduleDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduleDate is required");
        }
        if (themeKey == null || themeKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "themeKey is required");
        }
        if (themeTitle == null || themeTitle.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "themeTitle is required");
        }
        if (quoteId == null || !contentHubRepository.existsActiveQuote(quoteId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quoteId is invalid");
        }
        normalizeScheduleStatus(status);
        if (contentHubRepository.scheduleDateExists(scheduleDate, scheduleId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "scheduleDate already exists");
        }
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "schedule items are required");
        }

        int featuredArticles = 0;
        int featuredBooks = 0;
        int articleCount = 0;
        int bookCount = 0;
        for (ScheduleItemInput item : items) {
            String contentType = normalizeContentType(item.contentType());
            String slotRole = normalizeSlotRole(item.slotRole());
            if (item.contentId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "schedule item contentId is required");
            }
            if ("ARTICLE".equals(contentType)) {
                articleCount++;
                if (!contentHubRepository.existsActiveArticle(item.contentId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "article item is invalid");
                }
                if ("FEATURED".equals(slotRole)) {
                    featuredArticles++;
                }
            } else {
                bookCount++;
                if (!contentHubRepository.existsActiveBook(item.contentId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "book item is invalid");
                }
                if ("FEATURED".equals(slotRole)) {
                    featuredBooks++;
                }
            }
        }
        if (articleCount == 0 || bookCount == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "schedule must include at least one article and one book");
        }
        if (featuredArticles != 1 || featuredBooks != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "schedule must include exactly one featured article and one featured book");
        }
    }

    private List<ContentHubRepository.ScheduleItemMutation> normalizeScheduleItems(List<ScheduleItemInput> items) {
        return items.stream()
                .map(item -> new ContentHubRepository.ScheduleItemMutation(
                        normalizeContentType(item.contentType()),
                        item.contentId(),
                        normalizeSlotRole(item.slotRole()),
                        normalizeSort(item.sortOrder())
                ))
                .toList();
    }

    private static Map<String, Object> toScheduleItemMap(com.wuhao.aiemotion.domain.DailyScheduleItem item,
                                                         Map<Long, Map<String, Object>> articleById,
                                                         Map<Long, Map<String, Object>> bookById) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.id());
        row.put("scheduleId", item.scheduleId());
        row.put("contentType", item.contentType());
        row.put("contentId", item.contentId());
        row.put("slotRole", item.slotRole());
        row.put("sortOrder", item.sortOrder());
        Map<String, Object> content = "ARTICLE".equals(item.contentType()) ? articleById.get(item.contentId()) : bookById.get(item.contentId());
        row.put("contentTitle", content == null ? null : content.get("title"));
        row.put("contentCategory", content == null ? null : content.get("category"));
        return row;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeContentType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!Set.of("ARTICLE", "BOOK").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported content type: " + value);
        }
        return normalized;
    }

    private static String normalizeSlotRole(String value) {
        String normalized = value == null || value.isBlank() ? "SECONDARY" : value.trim().toUpperCase();
        if (!Set.of("FEATURED", "SECONDARY").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported slot role: " + value);
        }
        return normalized;
    }

    private static String normalizeScheduleStatus(String value) {
        String normalized = value == null || value.isBlank() ? "ACTIVE" : value.trim().toUpperCase();
        if (!Set.of("ACTIVE", "DRAFT").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported schedule status: " + value);
        }
        return normalized;
    }

    public record ScheduleItemInput(String contentType, Long contentId, String slotRole, Integer sortOrder) {}
}

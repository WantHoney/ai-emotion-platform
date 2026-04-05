package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.domain.Article;
import com.wuhao.aiemotion.domain.Book;
import com.wuhao.aiemotion.domain.DailySchedule;
import com.wuhao.aiemotion.domain.DailyScheduleItem;
import com.wuhao.aiemotion.domain.Quote;
import com.wuhao.aiemotion.repository.CmsRepository;
import com.wuhao.aiemotion.repository.ContentHubRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContentHubService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> CATEGORY_KEYS = Set.of(
            "stress",
            "sleep",
            "anxiety",
            "emotion",
            "help-seeking",
            "communication"
    );

    private final ContentHubRepository contentHubRepository;
    private final CmsRepository cmsRepository;

    public ContentHubService(ContentHubRepository contentHubRepository, CmsRepository cmsRepository) {
        this.contentHubRepository = contentHubRepository;
        this.cmsRepository = cmsRepository;
    }

    public Map<String, Object> homePayload() {
        LocalDate today = LocalDate.now(APP_ZONE);
        DailyBundle bundle = loadDailyBundle(today);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("todayDate", today);
        payload.put("todayTheme", mapTheme(bundle.schedule()));
        payload.put("todayQuote", mapQuote(bundle.quote()));
        payload.put("todayFeaturedArticle", mapArticle(bundle.featuredArticle()));
        payload.put("todayFeaturedBook", mapBook(bundle.featuredBook()));
        payload.put("todayArticles", bundle.articles().stream().limit(2).map(this::mapArticle).toList());
        payload.put("todayBooks", bundle.books().stream().limit(1).map(this::mapBook).toList());
        payload.put("recommendedArticles", bundle.articles().stream().map(this::mapArticle).toList());
        payload.put("recommendedBooks", bundle.books().stream().map(this::mapBook).toList());
        return payload;
    }

    public Map<String, Object> hub(LocalDate date, String category, Long userId) {
        LocalDate selectedDate = date == null ? LocalDate.now(APP_ZONE) : date;
        DailyBundle bundle = loadDailyBundle(selectedDate);
        String selectedCategory = normalizeCategory(category, bundle.schedule());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("selectedDate", selectedDate);
        payload.put("todayDate", LocalDate.now(APP_ZONE));
        payload.put("dailyPackage", mapDailyPackage(bundle));
        payload.put("categoryHighlights", mapCategoryHighlights(bundle, selectedCategory));
        payload.put("archiveDates", contentHubRepository.listArchiveDates(90));
        payload.put("recentHistory", userId == null ? List.of() : contentHubRepository.listRecentHistory(userId, 6));
        return payload;
    }

    public Map<String, Object> previewDailyPackage(LocalDate date) {
        DailyBundle bundle = loadDailyBundle(date == null ? LocalDate.now(APP_ZONE) : date);
        return mapDailyPackage(bundle);
    }

    public Map<String, Object> articleDetail(long articleId) {
        Article article = contentHubRepository.findActiveArticleById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "article not found"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("article", mapArticle(article));
        payload.put("relatedArticles", contentHubRepository.findActiveArticlesByCategory(
                article.category(),
                List.of(article.id()),
                3
        ).stream().map(this::mapArticle).toList());
        payload.put("relatedBooks", contentHubRepository.findActiveBooksByCategory(article.category(), List.of(), 3)
                .stream()
                .map(this::mapBook)
                .toList());
        return payload;
    }

    public Map<String, Object> bookDetail(long bookId) {
        Book book = contentHubRepository.findActiveBookById(bookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "book not found"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("book", mapBook(book));
        payload.put("relatedBooks", contentHubRepository.findActiveBooksByCategory(book.category(), List.of(book.id()), 3)
                .stream()
                .map(this::mapBook)
                .toList());
        payload.put("relatedArticles", contentHubRepository.findActiveArticlesByCategory(book.category(), List.of(), 3)
                .stream()
                .map(this::mapArticle)
                .toList());
        return payload;
    }

    public void recordHistory(long userId, String action, String contentType, long contentId) {
        String normalizedAction = normalizeAction(action);
        String normalizedType = normalizeContentType(contentType);
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        if ("VIEW".equals(normalizedAction)) {
            contentHubRepository.upsertViewHistory(userId, normalizedType, contentId, now);
            return;
        }
        contentHubRepository.upsertOutboundHistory(userId, normalizedType, contentId, now);
        cmsRepository.saveContentClick(normalizedType, contentId);
    }

    private DailyBundle loadDailyBundle(LocalDate date) {
        Optional<DailySchedule> scheduleOptional = contentHubRepository.findActiveScheduleByDate(date);
        if (scheduleOptional.isEmpty()) {
            return new DailyBundle(null, null, null, null, List.of(), List.of());
        }

        DailySchedule schedule = scheduleOptional.get();
        Quote quote = contentHubRepository.findActiveQuoteById(schedule.quoteId()).orElse(null);
        List<DailyScheduleItem> scheduleItems = contentHubRepository.listScheduleItems(schedule.id());

        List<Long> articleIds = scheduleItems.stream()
                .filter(item -> "ARTICLE".equals(item.contentType()))
                .map(DailyScheduleItem::contentId)
                .distinct()
                .toList();
        List<Long> bookIds = scheduleItems.stream()
                .filter(item -> "BOOK".equals(item.contentType()))
                .map(DailyScheduleItem::contentId)
                .distinct()
                .toList();

        List<Article> articles = sortArticlesByIds(contentHubRepository.findActiveArticlesByIds(articleIds), articleIds);
        List<Book> books = sortBooksByIds(contentHubRepository.findActiveBooksByIds(bookIds), bookIds);

        Article featuredArticle = resolveFeaturedArticle(scheduleItems, articles);
        Book featuredBook = resolveFeaturedBook(scheduleItems, books);

        List<Article> completedArticles = topUpArticles(schedule.themeKey(), articles, 3);
        List<Book> completedBooks = topUpBooks(schedule.themeKey(), books, 2);

        if (featuredArticle == null && !completedArticles.isEmpty()) {
            featuredArticle = completedArticles.get(0);
        }
        if (featuredBook == null && !completedBooks.isEmpty()) {
            featuredBook = completedBooks.get(0);
        }

        return new DailyBundle(schedule, quote, featuredArticle, featuredBook, completedArticles, completedBooks);
    }

    private Map<String, Object> mapDailyPackage(DailyBundle bundle) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hasSchedule", bundle.schedule() != null);
        payload.put("theme", mapTheme(bundle.schedule()));
        payload.put("quote", mapQuote(bundle.quote()));
        payload.put("featuredArticle", mapArticle(bundle.featuredArticle()));
        payload.put("featuredBook", mapBook(bundle.featuredBook()));
        payload.put("articles", bundle.articles().stream().map(this::mapArticle).toList());
        payload.put("books", bundle.books().stream().map(this::mapBook).toList());
        return payload;
    }

    private Map<String, Object> mapCategoryHighlights(DailyBundle bundle, String category) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("selectedCategory", category);
        if (bundle.schedule() == null) {
            payload.put("articles", List.of());
            payload.put("books", List.of());
            return payload;
        }

        List<Article> baseArticles = bundle.articles().stream()
                .filter(article -> category.equals(article.category()))
                .toList();
        List<Book> baseBooks = bundle.books().stream()
                .filter(book -> category.equals(book.category()))
                .toList();

        List<Article> articles = topUpArticles(category, baseArticles, 4);
        List<Book> books = topUpBooks(category, baseBooks, 4);
        payload.put("articles", articles.stream().map(this::mapArticle).toList());
        payload.put("books", books.stream().map(this::mapBook).toList());
        return payload;
    }

    private List<Article> topUpArticles(String category, List<Article> base, int targetSize) {
        List<Article> result = new ArrayList<>(base);
        List<Long> excludeIds = result.stream().map(Article::id).filter(Objects::nonNull).toList();
        if (result.size() < targetSize) {
            result.addAll(contentHubRepository.findActiveArticlesByCategory(category, excludeIds, targetSize - result.size()));
        }
        if (result.size() < targetSize) {
            List<Long> nextExcludeIds = result.stream().map(Article::id).filter(Objects::nonNull).toList();
            result.addAll(contentHubRepository.findActiveArticlesByCategory(null, nextExcludeIds, targetSize - result.size()));
        }
        return deduplicateArticles(result, targetSize);
    }

    private List<Book> topUpBooks(String category, List<Book> base, int targetSize) {
        List<Book> result = new ArrayList<>(base);
        List<Long> excludeIds = result.stream().map(Book::id).filter(Objects::nonNull).toList();
        if (result.size() < targetSize) {
            result.addAll(contentHubRepository.findActiveBooksByCategory(category, excludeIds, targetSize - result.size()));
        }
        if (result.size() < targetSize) {
            List<Long> nextExcludeIds = result.stream().map(Book::id).filter(Objects::nonNull).toList();
            result.addAll(contentHubRepository.findActiveBooksByCategory(null, nextExcludeIds, targetSize - result.size()));
        }
        return deduplicateBooks(result, targetSize);
    }

    private static List<Article> sortArticlesByIds(List<Article> rows, List<Long> orderedIds) {
        Map<Long, Article> map = rows.stream()
                .filter(article -> article.id() != null)
                .collect(Collectors.toMap(Article::id, article -> article, (left, right) -> left, LinkedHashMap::new));
        List<Article> ordered = new ArrayList<>();
        for (Long orderedId : orderedIds) {
            Article article = map.get(orderedId);
            if (article != null) {
                ordered.add(article);
            }
        }
        return ordered;
    }

    private static List<Book> sortBooksByIds(List<Book> rows, List<Long> orderedIds) {
        Map<Long, Book> map = rows.stream()
                .filter(book -> book.id() != null)
                .collect(Collectors.toMap(Book::id, book -> book, (left, right) -> left, LinkedHashMap::new));
        List<Book> ordered = new ArrayList<>();
        for (Long orderedId : orderedIds) {
            Book book = map.get(orderedId);
            if (book != null) {
                ordered.add(book);
            }
        }
        return ordered;
    }

    private static Article resolveFeaturedArticle(List<DailyScheduleItem> items, List<Article> articles) {
        Long featuredId = items.stream()
                .filter(item -> "ARTICLE".equals(item.contentType()) && "FEATURED".equals(item.slotRole()))
                .map(DailyScheduleItem::contentId)
                .findFirst()
                .orElse(null);
        if (featuredId == null) {
            return articles.isEmpty() ? null : articles.get(0);
        }
        return articles.stream().filter(article -> featuredId.equals(article.id())).findFirst().orElse(null);
    }

    private static Book resolveFeaturedBook(List<DailyScheduleItem> items, List<Book> books) {
        Long featuredId = items.stream()
                .filter(item -> "BOOK".equals(item.contentType()) && "FEATURED".equals(item.slotRole()))
                .map(DailyScheduleItem::contentId)
                .findFirst()
                .orElse(null);
        if (featuredId == null) {
            return books.isEmpty() ? null : books.get(0);
        }
        return books.stream().filter(book -> featuredId.equals(book.id())).findFirst().orElse(null);
    }

    private static List<Article> deduplicateArticles(List<Article> rows, int maxSize) {
        LinkedHashSet<Long> seenIds = new LinkedHashSet<>();
        List<Article> result = new ArrayList<>();
        for (Article row : rows) {
            if (row == null || row.id() == null || !seenIds.add(row.id())) {
                continue;
            }
            result.add(row);
            if (result.size() >= maxSize) {
                break;
            }
        }
        return result;
    }

    private static List<Book> deduplicateBooks(List<Book> rows, int maxSize) {
        LinkedHashSet<Long> seenIds = new LinkedHashSet<>();
        List<Book> result = new ArrayList<>();
        for (Book row : rows) {
            if (row == null || row.id() == null || !seenIds.add(row.id())) {
                continue;
            }
            result.add(row);
            if (result.size() >= maxSize) {
                break;
            }
        }
        return result;
    }

    private static String normalizeCategory(String category, DailySchedule schedule) {
        String fallback = schedule != null && CATEGORY_KEYS.contains(schedule.themeKey()) ? schedule.themeKey() : "stress";
        if (category == null || category.isBlank()) {
            return fallback;
        }
        String normalized = category.trim();
        return CATEGORY_KEYS.contains(normalized) ? normalized : fallback;
    }

    private static String normalizeAction(String action) {
        String normalized = action == null ? "" : action.trim().toUpperCase();
        if (!Set.of("VIEW", "OUTBOUND").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported history action: " + action);
        }
        return normalized;
    }

    private static String normalizeContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toUpperCase();
        if (!Set.of("ARTICLE", "BOOK").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported content type: " + contentType);
        }
        return normalized;
    }

    private Map<String, Object> mapTheme(DailySchedule schedule) {
        if (schedule == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scheduleDate", schedule.scheduleDate());
        payload.put("themeKey", schedule.themeKey());
        payload.put("themeTitle", schedule.themeTitle());
        payload.put("themeSubtitle", schedule.themeSubtitle());
        payload.put("status", schedule.status());
        return payload;
    }

    private Map<String, Object> mapQuote(Quote quote) {
        if (quote == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", quote.id());
        payload.put("content", quote.content());
        payload.put("author", quote.author());
        payload.put("sortOrder", quote.sortOrder());
        payload.put("recommended", quote.recommended());
        payload.put("enabled", quote.enabled());
        payload.put("createdAt", quote.createdAt());
        payload.put("updatedAt", quote.updatedAt());
        return payload;
    }

    private Map<String, Object> mapArticle(Article article) {
        if (article == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", article.id());
        payload.put("title", article.title());
        payload.put("coverImageUrl", article.coverImageUrl());
        payload.put("summary", article.summary());
        payload.put("recommendReason", article.recommendReason());
        payload.put("fitFor", article.fitFor());
        payload.put("highlights", article.highlights());
        payload.put("readingMinutes", article.readingMinutes());
        payload.put("category", article.category());
        payload.put("sourceName", article.sourceName());
        payload.put("sourceUrl", article.sourceUrl());
        payload.put("contentUrl", article.contentUrl());
        payload.put("isExternal", article.isExternal());
        payload.put("difficultyTag", article.difficultyTag());
        payload.put("sortOrder", article.sortOrder());
        payload.put("recommended", article.recommended());
        payload.put("enabled", article.enabled());
        payload.put("publishedAt", article.publishedAt());
        payload.put("createdAt", article.createdAt());
        payload.put("updatedAt", article.updatedAt());
        return payload;
    }

    private Map<String, Object> mapBook(Book book) {
        if (book == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", book.id());
        payload.put("title", book.title());
        payload.put("author", book.author());
        payload.put("coverImageUrl", book.coverImageUrl());
        payload.put("description", book.description());
        payload.put("category", book.category());
        payload.put("recommendReason", book.recommendReason());
        payload.put("fitFor", book.fitFor());
        payload.put("highlights", book.highlights());
        payload.put("purchaseUrl", book.purchaseUrl());
        payload.put("sortOrder", book.sortOrder());
        payload.put("recommended", book.recommended());
        payload.put("enabled", book.enabled());
        payload.put("createdAt", book.createdAt());
        payload.put("updatedAt", book.updatedAt());
        return payload;
    }

    private record DailyBundle(
            DailySchedule schedule,
            Quote quote,
            Article featuredArticle,
            Book featuredBook,
            List<Article> articles,
            List<Book> books
    ) {}
}

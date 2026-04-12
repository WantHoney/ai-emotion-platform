package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.domain.Article;
import com.wuhao.aiemotion.domain.Book;
import com.wuhao.aiemotion.domain.PsyCenter;
import com.wuhao.aiemotion.domain.Quote;
import com.wuhao.aiemotion.repository.CmsRepository;
import com.wuhao.aiemotion.repository.ContentHubRepository;
import com.wuhao.aiemotion.repository.PsyCenterRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CmsSeedService {

    private static final Logger log = LoggerFactory.getLogger(CmsSeedService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate DAILY_SCHEDULE_ANCHOR = LocalDate.of(2026, 1, 1);
    private static final int DAILY_SCHEDULE_WINDOW_DAYS = 45;
    private static final List<DailyThemeSeed> DAILY_THEMES = List.of(
            new DailyThemeSeed(
                    "stress",
                    "先把自己稳住",
                    "当压力先压上来的时候，今天先做稳定和降噪，不急着立刻解决全部问题。",
                    List.of("seed_article_stress_001", "seed_article_stress_002", "seed_article_stress_003"),
                    List.of("seed_book_firstaid_001", "seed_book_body_001", "seed_book_road_001", "seed_book_flow_001")
            ),
            new DailyThemeSeed(
                    "sleep",
                    "把睡眠还给身体",
                    "今天先照顾节律、光线和休息环境，让身体有机会慢慢重新进入恢复状态。",
                    List.of("seed_article_sleep_001", "seed_article_sleep_002", "seed_article_sleep_003"),
                    List.of("seed_book_sleep_why_001", "seed_book_sleep_stanford_001")
            ),
            new DailyThemeSeed(
                    "anxiety",
                    "先识别焦虑信号",
                    "不是急着否定自己，而是先看清焦虑怎么出现、怎么影响身体和注意力。",
                    List.of("seed_article_anxiety_001", "seed_article_anxiety_002", "seed_article_anxiety_003"),
                    List.of("seed_book_inferiority_001", "seed_book_burns_001", "seed_book_meaning_001", "seed_book_sensitive_001")
            ),
            new DailyThemeSeed(
                    "emotion",
                    "给情绪一个容器",
                    "把难受从一团雾里拆出来，看见它、命名它，处理就会更有抓手。",
                    List.of("seed_article_emotion_001", "seed_article_emotion_002", "seed_article_emotion_003"),
                    List.of("seed_book_toad_001", "seed_book_family_001", "seed_book_not_forgive_001")
            ),
            new DailyThemeSeed(
                    "help-seeking",
                    "求助本身就是能力",
                    "当一个人扛着已经很吃力，今天给自己一个更明确的求助入口和解释空间。",
                    List.of("seed_article_help_001", "seed_article_help_002", "seed_article_help_003"),
                    List.of("seed_book_talk_001")
            ),
            new DailyThemeSeed(
                    "communication",
                    "把支持重新连回来",
                    "关系里的连接、表达和边界，都可以被重新练习，今天从开口和理解开始。",
                    List.of("seed_article_communication_001", "seed_article_communication_002", "seed_article_communication_003"),
                    List.of("seed_book_nvc_001", "seed_book_courage_001", "seed_book_crucial_001", "seed_book_emotional_blackmail_001", "seed_book_communication_method_001", "seed_book_relationship_001")
            )
    );

    private final ObjectMapper objectMapper;
    private final CmsRepository cmsRepository;
    private final PsyCenterRepository psyCenterRepository;
    private final ContentHubRepository contentHubRepository;

    public CmsSeedService(
            ObjectMapper objectMapper,
            CmsRepository cmsRepository,
            PsyCenterRepository psyCenterRepository,
            ContentHubRepository contentHubRepository
    ) {
        this.objectMapper = objectMapper;
        this.cmsRepository = cmsRepository;
        this.psyCenterRepository = psyCenterRepository;
        this.contentHubRepository = contentHubRepository;
    }

    @PostConstruct
    public void seedDefaults() {
        List<String> quoteRotation = seedQuotes();
        seedArticles();
        seedBooks();
        seedPsyCenters();
        seedDailySchedules(quoteRotation);
    }

    private List<String> seedQuotes() {
        List<String> rotation = new ArrayList<>();
        for (QuoteSeed seed : readList("seeds/quotes.json", new TypeReference<List<QuoteSeed>>() {})) {
            try {
                Quote quote = new Quote(
                        null,
                        seed.content(),
                        blankToNull(seed.author()),
                        defaultInt(seed.sortOrder(), 100),
                        defaultBool(seed.recommended(), false),
                        defaultBool(seed.enabled(), true),
                        seed.seedKey(),
                        "seed",
                        defaultBool(seed.isActive(), true),
                        null,
                        null
                );
                if (cmsRepository.quoteSeedExists(seed.seedKey())) {
                    cmsRepository.updateSeedQuote(quote);
                } else {
                    cmsRepository.insertSeedQuote(quote);
                }
                if (Boolean.TRUE.equals(quote.enabled()) && Boolean.TRUE.equals(quote.isActive())) {
                    rotation.add(quote.seedKey());
                }
            } catch (Exception ex) {
                log.warn("skip quote seed {}", seed.seedKey(), ex);
            }
        }
        return rotation;
    }

    private void seedArticles() {
        for (ArticleSeed seed : readList("seeds/articles.json", new TypeReference<List<ArticleSeed>>() {})) {
            try {
                String sourceUrl = blankToNull(seed.sourceUrl());
                Article article = new Article(
                        null,
                        seed.title(),
                        blankToNull(seed.coverImageUrl()),
                        blankToNull(seed.summary()),
                        blankToNull(seed.recommendReason()),
                        blankToNull(seed.fitFor()),
                        blankToNull(seed.highlights()),
                        seed.readingMinutes(),
                        blankToNull(seed.category()),
                        blankToNull(seed.sourceName()),
                        sourceUrl,
                        sourceUrl,
                        defaultBool(seed.isExternal(), true),
                        blankToNull(seed.difficultyTag()),
                        defaultInt(seed.sortOrder(), 100),
                        defaultBool(seed.recommended(), false),
                        defaultBool(seed.enabled(), true),
                        seed.seedKey(),
                        "seed",
                        defaultBool(seed.isActive(), true),
                        parseDateTime(seed.publishedAt()),
                        null,
                        null
                );
                if (cmsRepository.articleSeedExists(seed.seedKey())) {
                    cmsRepository.updateSeedArticle(article);
                    continue;
                }
                cmsRepository.insertSeedArticle(article);
            } catch (Exception ex) {
                log.warn("skip article seed {}", seed.seedKey(), ex);
            }
        }
    }

    private void seedBooks() {
        for (BookSeed seed : readList("seeds/books.json", new TypeReference<List<BookSeed>>() {})) {
            try {
                Book book = new Book(
                        null,
                        seed.title(),
                        blankToNull(seed.author()),
                        blankToNull(seed.coverImageUrl()),
                        blankToNull(seed.description()),
                        blankToNull(seed.category()),
                        blankToNull(seed.recommendReason()),
                        blankToNull(seed.fitFor()),
                        blankToNull(seed.highlights()),
                        blankToNull(seed.purchaseUrl()),
                        defaultInt(seed.sortOrder(), 100),
                        defaultBool(seed.recommended(), false),
                        defaultBool(seed.enabled(), true),
                        seed.seedKey(),
                        "seed",
                        defaultBool(seed.isActive(), true),
                        null,
                        null
                );
                if (cmsRepository.bookSeedExists(seed.seedKey())) {
                    cmsRepository.updateSeedBook(book);
                    continue;
                }
                cmsRepository.insertSeedBook(book);
            } catch (Exception ex) {
                log.warn("skip book seed {}", seed.seedKey(), ex);
            }
        }
    }

    private void seedPsyCenters() {
        for (PsyCenterSeed seed : readList("seeds/psy_centers.json", new TypeReference<List<PsyCenterSeed>>() {})) {
            try {
                if (psyCenterRepository.existsSeedKey(seed.seedKey())) {
                    continue;
                }
                psyCenterRepository.create(new PsyCenter(
                        null,
                        seed.name(),
                        seed.cityCode(),
                        seed.cityName(),
                        seed.district(),
                        seed.address(),
                        seed.phone(),
                        parseDecimal(seed.latitude()),
                        parseDecimal(seed.longitude()),
                        seed.sourceName(),
                        seed.sourceUrl(),
                        seed.sourceLevel(),
                        defaultBool(seed.recommended(), false),
                        defaultBool(seed.enabled(), true),
                        seed.seedKey(),
                        "seed",
                        defaultBool(seed.isActive(), true),
                        null,
                        null
                ));
            } catch (Exception ex) {
                log.warn("skip psy center seed {}", seed.seedKey(), ex);
            }
        }
    }

    private void seedDailySchedules(List<String> quoteRotation) {
        if (quoteRotation == null || quoteRotation.isEmpty()) {
            log.warn("skip daily schedule seeding because quote rotation is empty");
            return;
        }
        LocalDate today = LocalDate.now(APP_ZONE);
        for (int offset = -DAILY_SCHEDULE_WINDOW_DAYS; offset <= DAILY_SCHEDULE_WINDOW_DAYS; offset++) {
            LocalDate scheduleDate = today.plusDays(offset);
            long dayOffset = ChronoUnit.DAYS.between(DAILY_SCHEDULE_ANCHOR, scheduleDate);
            int themeIndex = Math.floorMod(dayOffset, DAILY_THEMES.size());
            int quoteIndex = Math.floorMod(dayOffset, quoteRotation.size());
            long rotationIndex = Math.floorDiv(dayOffset, DAILY_THEMES.size());

            DailyThemeSeed theme = DAILY_THEMES.get(themeIndex);
            String quoteSeedKey = quoteRotation.get(quoteIndex);
            Optional<Long> quoteId = contentHubRepository.findQuoteIdBySeedKey(quoteSeedKey);
            if (quoteId.isEmpty()) {
                log.warn("skip schedule {} because quote seed {} is missing", scheduleDate, quoteSeedKey);
                continue;
            }

            List<ContentHubRepository.ScheduleItemMutation> items = buildScheduleItems(theme, rotationIndex);
            if (items.isEmpty()) {
                log.warn("skip schedule {} because schedule items cannot be resolved", scheduleDate);
                continue;
            }

            Optional<com.wuhao.aiemotion.domain.DailySchedule> existingSchedule = contentHubRepository.listSchedules(scheduleDate)
                    .stream()
                    .findFirst();
            if (existingSchedule.isPresent()) {
                com.wuhao.aiemotion.domain.DailySchedule schedule = existingSchedule.get();
                if (!contentHubRepository.scheduleUsesOnlySeedContent(schedule.id())) {
                    continue;
                }
                contentHubRepository.updateSchedule(
                        schedule.id(),
                        scheduleDate,
                        theme.themeKey(),
                        theme.themeTitle(),
                        theme.themeSubtitle(),
                        quoteId.get(),
                        defaultStatus(schedule.status())
                );
                contentHubRepository.replaceScheduleItems(schedule.id(), items);
                continue;
            }

            long scheduleId = contentHubRepository.createSchedule(
                    scheduleDate,
                    theme.themeKey(),
                    theme.themeTitle(),
                    theme.themeSubtitle(),
                    quoteId.get(),
                    "ACTIVE"
            );
            contentHubRepository.replaceScheduleItems(scheduleId, items);
        }
    }

    private List<ContentHubRepository.ScheduleItemMutation> buildScheduleItems(DailyThemeSeed theme, long rotationIndex) {
        List<ContentHubRepository.ScheduleItemMutation> items = new ArrayList<>();
        int sortOrder = 10;

        List<String> rotatedArticleSeedKeys = rotateSeedKeys(theme.articleSeedKeys(), rotationIndex);
        List<String> rotatedBookSeedKeys = rotateSeedKeys(theme.bookSeedKeys(), rotationIndex);
        if (rotatedArticleSeedKeys.isEmpty() || rotatedBookSeedKeys.isEmpty()) {
            return List.of();
        }

        Long featuredArticleId = resolveSeedArticleId(rotatedArticleSeedKeys.get(0));
        Long featuredBookId = resolveSeedBookId(rotatedBookSeedKeys.get(0));
        if (featuredArticleId == null || featuredBookId == null) {
            return List.of();
        }

        items.add(new ContentHubRepository.ScheduleItemMutation("ARTICLE", featuredArticleId, "FEATURED", sortOrder));
        sortOrder += 10;

        for (int index = 1; index < rotatedArticleSeedKeys.size(); index++) {
            Long articleId = resolveSeedArticleId(rotatedArticleSeedKeys.get(index));
            if (articleId != null && !articleId.equals(featuredArticleId)) {
                items.add(new ContentHubRepository.ScheduleItemMutation("ARTICLE", articleId, "SECONDARY", sortOrder));
                sortOrder += 10;
            }
        }

        items.add(new ContentHubRepository.ScheduleItemMutation("BOOK", featuredBookId, "FEATURED", sortOrder));
        sortOrder += 10;

        for (int index = 1; index < rotatedBookSeedKeys.size(); index++) {
            Long bookId = resolveSeedBookId(rotatedBookSeedKeys.get(index));
            if (bookId != null && !bookId.equals(featuredBookId)) {
                items.add(new ContentHubRepository.ScheduleItemMutation("BOOK", bookId, "SECONDARY", sortOrder));
                sortOrder += 10;
            }
        }

        return items;
    }

    private Long resolveSeedArticleId(String seedKey) {
        return contentHubRepository.findArticleIdBySeedKey(seedKey).orElse(null);
    }

    private Long resolveSeedBookId(String seedKey) {
        return contentHubRepository.findBookIdBySeedKey(seedKey).orElse(null);
    }

    private static List<String> rotateSeedKeys(List<String> seedKeys, long rotationIndex) {
        if (seedKeys == null || seedKeys.isEmpty()) {
            return List.of();
        }
        if (seedKeys.size() == 1) {
            return List.copyOf(seedKeys);
        }
        int shift = Math.floorMod(rotationIndex, seedKeys.size());
        if (shift == 0) {
            return List.copyOf(seedKeys);
        }
        List<String> rotated = new ArrayList<>(seedKeys.size());
        rotated.addAll(seedKeys.subList(shift, seedKeys.size()));
        rotated.addAll(seedKeys.subList(0, shift));
        return rotated;
    }

    private <T> List<T> readList(String path, TypeReference<List<T>> typeReference) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(inputStream, typeReference);
        } catch (IOException ex) {
            log.warn("skip loading seed resource {}", path, ex);
            return List.of();
        }
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.trim());
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean defaultBool(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String defaultStatus(String value) {
        if (value == null || value.isBlank()) {
            return "ACTIVE";
        }
        return value.trim();
    }

    private record QuoteSeed(
            String seedKey,
            String content,
            String author,
            Integer sortOrder,
            Boolean recommended,
            Boolean enabled,
            Boolean isActive
    ) {}

    private record ArticleSeed(
            String seedKey,
            String title,
            String coverImageUrl,
            String summary,
            String recommendReason,
            String fitFor,
            String highlights,
            Integer readingMinutes,
            String category,
            String sourceName,
            String sourceUrl,
            Boolean isExternal,
            String difficultyTag,
            Integer sortOrder,
            Boolean recommended,
            Boolean enabled,
            Boolean isActive,
            String publishedAt
    ) {}

    private record BookSeed(
            String seedKey,
            String title,
            String author,
            String coverImageUrl,
            String description,
            String category,
            String recommendReason,
            String fitFor,
            String highlights,
            String purchaseUrl,
            Integer sortOrder,
            Boolean recommended,
            Boolean enabled,
            Boolean isActive
    ) {}

    private record PsyCenterSeed(
            String seedKey,
            String name,
            String cityCode,
            String cityName,
            String district,
            String address,
            String phone,
            String latitude,
            String longitude,
            String sourceName,
            String sourceUrl,
            String sourceLevel,
            Boolean recommended,
            Boolean enabled,
            Boolean isActive
    ) {}

    private record DailyThemeSeed(
            String themeKey,
            String themeTitle,
            String themeSubtitle,
            List<String> articleSeedKeys,
            List<String> bookSeedKeys
    ) {}
}

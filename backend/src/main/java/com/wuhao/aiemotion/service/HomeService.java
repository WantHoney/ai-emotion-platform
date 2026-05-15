package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.repository.HomeContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HomeService {

    private static final Logger log = LoggerFactory.getLogger(HomeService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private final HomeContentRepository homeContentRepository;
    private final ContentHubService contentHubService;

    public HomeService(HomeContentRepository homeContentRepository, ContentHubService contentHubService) {
        this.homeContentRepository = homeContentRepository;
        this.contentHubService = contentHubService;
    }

    public Map<String, Object> home() {
        try {
            Map<String, Object> payload = new HashMap<>(contentHubService.homePayload());
            payload.put("banners", homeContentRepository.homeBanners(5));
            payload.put("selfHelpEntries", defaultSelfHelpEntries());
            return payload;
        } catch (Exception e) {
            log.warn("home content query failed, fallback to default payload", e);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("banners", List.of());
            fallback.put("todayDate", LocalDate.now(APP_ZONE));
            fallback.put("todayTheme", Map.of(
                    "themeKey", "stress",
                    "themeTitle", "今天先稳住自己的节奏",
                    "themeSubtitle", "从一句话、一篇文章和一本书开始。"
            ));
            fallback.put("todayQuote", Map.of("id", 0, "content", "慢一点也没关系，重要的是继续向前。", "author", "系统推荐"));
            fallback.put("todayFeaturedArticle", null);
            fallback.put("todayFeaturedBook", null);
            fallback.put("todayArticles", List.of());
            fallback.put("todayBooks", List.of());
            fallback.put("recommendedArticles", List.of());
            fallback.put("recommendedBooks", List.of());
            fallback.put("selfHelpEntries", defaultSelfHelpEntries());
            return fallback;
        }
    }

    private List<Map<String, Object>> defaultSelfHelpEntries() {
        return List.of(
                Map.of("key", "audio_upload", "title", "音频上传", "path", "/upload"),
                Map.of("key", "report_center", "title", "报告中心", "path", "/reports"),
                Map.of("key", "psy_centers", "title", "心理中心", "path", "/psy-centers")
        );
    }
}

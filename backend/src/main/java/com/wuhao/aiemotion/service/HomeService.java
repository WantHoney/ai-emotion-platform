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
            log.warn("home content query failed, fallback to placeholder payload", e);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("banners", List.of());
            fallback.put("todayDate", LocalDate.now(APP_ZONE));
            fallback.put("todayTheme", Map.of(
                    "themeKey", "stress",
                    "themeTitle", "Today, start by steadying yourself.",
                    "themeSubtitle", "Begin with one quote, one article, and one book."
            ));
            fallback.put("todayQuote", Map.of("id", 0, "content", "Keep moving forward.", "author", "AI Emotion"));
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
                Map.of("key", "audio_upload", "title", "Audio Upload", "path", "/upload"),
                Map.of("key", "report_center", "title", "Report Center", "path", "/reports"),
                Map.of("key", "psy_centers", "title", "Psy Centers", "path", "/psy-centers")
        );
    }
}

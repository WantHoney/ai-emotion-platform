package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.domain.Quote;
import com.wuhao.aiemotion.repository.HomeContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HomeService {

    private static final Logger log = LoggerFactory.getLogger(HomeService.class);

    private final HomeContentRepository homeContentRepository;

    public HomeService(HomeContentRepository homeContentRepository) {
        this.homeContentRepository = homeContentRepository;
    }

    public Map<String, Object> home() {
        try {
            List<Quote> quotes = homeContentRepository.homeQuotes(1);
            Map<String, Object> payload = new HashMap<>();
            payload.put("banners", homeContentRepository.homeBanners(5));
            payload.put("todayQuote", quotes.isEmpty() ? null : quotes.get(0));
            payload.put("recommendedArticles", homeContentRepository.homeArticles(6));
            payload.put("recommendedBooks", homeContentRepository.homeBooks(6));
            payload.put("selfHelpEntries", defaultSelfHelpEntries());
            return payload;
        } catch (Exception e) {
            log.warn("home content query failed, fallback to placeholder payload", e);
            return Map.of(
                    "banners", List.of(),
                    "todayQuote", Map.of("id", 0, "content", "Keep moving forward.", "author", "AI Emotion"),
                    "recommendedArticles", List.of(),
                    "recommendedBooks", List.of(),
                    "selfHelpEntries", defaultSelfHelpEntries()
            );
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

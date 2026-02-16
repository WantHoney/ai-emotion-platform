package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.domain.Quote;
import com.wuhao.aiemotion.repository.HomeContentRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HomeService {

    private final HomeContentRepository homeContentRepository;

    public HomeService(HomeContentRepository homeContentRepository) {
        this.homeContentRepository = homeContentRepository;
    }

    public Map<String, Object> home() {
        List<Quote> quotes = homeContentRepository.homeQuotes(1);
        Map<String, Object> payload = new HashMap<>();
        payload.put("banners", homeContentRepository.homeBanners(5));
        payload.put("todayQuote", quotes.isEmpty() ? null : quotes.get(0));
        payload.put("recommendedArticles", homeContentRepository.homeArticles(6));
        payload.put("recommendedBooks", homeContentRepository.homeBooks(6));
        payload.put("selfHelpEntries", List.of(
                Map.of("key", "breathing", "title", "呼吸放松练习", "path", "/practice/breathing"),
                Map.of("key", "journaling", "title", "情绪日记", "path", "/practice/journaling")
        ));
        return payload;
    }
}

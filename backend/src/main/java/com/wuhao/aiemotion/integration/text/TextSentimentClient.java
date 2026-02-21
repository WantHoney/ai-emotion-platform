package com.wuhao.aiemotion.integration.text;

import com.wuhao.aiemotion.integration.ser.SerProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TextSentimentClient {

    private final RestTemplateBuilder restTemplateBuilder;
    private final SerProperties serProperties;

    public TextSentimentClient(RestTemplateBuilder restTemplateBuilder, SerProperties serProperties) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.serProperties = serProperties;
    }

    public TextSentimentResponse score(String text, String language, long timeoutMs) {
        String url = serProperties.getBaseUrl() + "/text/sentiment";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text == null ? "" : text);
        body.put("language", language);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();

        ResponseEntity<TextSentimentResponse> response =
                restTemplate.postForEntity(url, req, TextSentimentResponse.class);
        TextSentimentResponse payload = response.getBody();
        if (payload == null) {
            throw new IllegalStateException("Text sentiment service returned empty body");
        }
        return payload;
    }
}

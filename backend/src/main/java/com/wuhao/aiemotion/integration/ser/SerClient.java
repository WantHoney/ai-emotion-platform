package com.wuhao.aiemotion.integration.ser;

import com.wuhao.aiemotion.exception.SerServiceUnavailableException;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.time.Duration;

@Component
public class SerClient {

    private final RestTemplateBuilder restTemplateBuilder;
    private final SerProperties properties;

    public SerClient(RestTemplateBuilder restTemplateBuilder, SerProperties properties) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.properties = properties;
    }

    public SerAnalyzeResponse analyze(Path audioPath) {
        String url = properties.getBaseUrl() + "/ser/analyze";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(audioPath));
        body.add("segment_ms", String.valueOf(properties.getSegmentMs()));
        body.add("overlap_ms", String.valueOf(properties.getOverlapMs()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = buildRestTemplate(properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
        try {
            ResponseEntity<SerAnalyzeResponse> response = restTemplate.postForEntity(url, req, SerAnalyzeResponse.class);
            SerAnalyzeResponse payload = response.getBody();
            if (payload == null) {
                throw new SerClientException("parse_error", "SER service returned empty body");
            }
            return payload;
        } catch (ResourceAccessException e) {
            if (isTimeoutException(e)) {
                throw new SerClientException("timeout", "SER timeout", e);
            }
            throw new SerServiceUnavailableException("SER service unavailable", e);
        } catch (HttpClientErrorException e) {
            throw new SerClientException("4xx", e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (HttpServerErrorException e) {
            throw new SerClientException("5xx", e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new SerClientException("parse_error", "SER response parse error", e);
        }
    }

    public boolean probeHealth() {
        RestTemplate rt = buildRestTemplate(properties.getHealthTimeoutMs(), properties.getHealthTimeoutMs());
        return callGet(rt, properties.getBaseUrl() + "/health");
    }

    public boolean warmup() {
        RestTemplate restTemplate = buildRestTemplate(properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
        if (callGet(restTemplate, properties.getBaseUrl() + "/warmup")) return true;
        return callGet(restTemplate, properties.getBaseUrl() + "/health");
    }

    private RestTemplate buildRestTemplate(long connectMs, long readMs) {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectMs))
                .setReadTimeout(Duration.ofMillis(readMs))
                .build();
    }

    private boolean callGet(RestTemplate restTemplate, String url) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpStatusCodeException e) {
            return false;
        } catch (ResourceAccessException e) {
            return false;
        }
    }

    private boolean isTimeoutException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

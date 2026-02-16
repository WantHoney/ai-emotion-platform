package com.wuhao.aiemotion.integration.asr;

import com.wuhao.aiemotion.integration.ser.SerProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.time.Duration;

@Component
public class AsrClient {

    private final RestTemplateBuilder restTemplateBuilder;
    private final SerProperties properties;

    public AsrClient(RestTemplateBuilder restTemplateBuilder, SerProperties properties) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.properties = properties;
    }

    public AsrTranscribeResponse transcribe(Path audioPath, long timeoutMs) {
        String url = properties.getBaseUrl() + "/asr/transcribe";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(audioPath));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
        ResponseEntity<AsrTranscribeResponse> response = restTemplate.postForEntity(url, req, AsrTranscribeResponse.class);
        AsrTranscribeResponse payload = response.getBody();
        if (payload == null) {
            throw new IllegalStateException("ASR service returned empty body");
        }
        return payload;
    }
}


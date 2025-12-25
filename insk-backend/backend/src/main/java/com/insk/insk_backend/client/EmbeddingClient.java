package com.insk.insk_backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Component
public class EmbeddingClient {

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String EMBEDDING_URL = "https://api.openai.com/v1/embeddings";

    public List<Double> embed(String text) {
        try {
            // 텍스트 길이 제한 (약 8000 토큰에 해당하는 문자 수, 안전하게 6000자로 제한)
            String truncatedText = text;
            if (text != null && text.length() > 6000) {
                truncatedText = text.substring(0, 6000);
                log.warn("⚠️ Embedding 텍스트가 너무 길어서 잘랐습니다. 원본 길이: {}, 잘린 길이: {}", text.length(), truncatedText.length());
            }

            EmbeddingRequest requestBody = new EmbeddingRequest("text-embedding-3-small", truncatedText);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<EmbeddingResponse> response =
                    restTemplate.postForEntity(EMBEDDING_URL, entity, EmbeddingResponse.class);

            return response.getBody().getData().get(0).getEmbedding();
        } catch (Exception e) {
            log.error("❗ Embedding 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingRequest {
        private final String model;
        private final String input;

        public EmbeddingRequest(String model, String input) {
            this.model = model;
            this.input = input;
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingResponse {
        private List<EmbeddingData> data;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingData {
        private List<Double> embedding;
    }
}

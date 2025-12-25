package com.insk.insk_backend.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insk.insk_backend.dto.KeywordRecommendDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class KeywordAiClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    public KeywordAiClient(@Value("${openai.api.key}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1/chat/completions")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<KeywordRecommendDto.Candidate> callLlm(String systemPrompt, String userPrompt) {

        Map<String, Object> body = Map.of(
                "model", "gpt-4o-mini",  // 모델명 수정: gpt-4.1-mini -> gpt-4o-mini
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String raw = webClient.post()
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.error(new RuntimeException("OpenAI 호출 실패", e)))
                .block();

        try {
            Map<String, Object> root = objectMapper.readValue(raw, Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("LLM 응답 choices 비어 있음");
            }

            Map<String, Object> first = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) first.get("message");

            String content = (String) message.get("content");

            KeywordRecommendDto.LlmResult parsed =
                    objectMapper.readValue(content, KeywordRecommendDto.LlmResult.class);

            return parsed.getRecommended();

        } catch (Exception e) {
            throw new RuntimeException("LLM 응답 파싱 실패: " + e.getMessage(), e);
        }
    }
}

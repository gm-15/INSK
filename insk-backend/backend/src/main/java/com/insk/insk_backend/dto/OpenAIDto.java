package com.insk.insk_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * OpenAI (GPT) API의 요청/응답을 매핑하기 위한 DTO
 */
public class OpenAIDto {

    // --- [ ⭐️⭐️⭐️ 1. 신규 추가 (Request DTOs) ⭐️⭐️⭐️ ] ---

    /**
     * OpenAI API에 보낼 요청 본문 (Request Body)
     */
    @Getter
    public static class ChatRequest {
        private String model;
        private List<Message> messages;
        @JsonProperty("response_format")
        private Format responseFormat;

        public ChatRequest(String model, String systemPrompt, String userContent, boolean jsonMode) {
            this.model = model;
            this.messages = List.of(
                    new Message("system", systemPrompt),
                    new Message("user", userContent)
            );
            if (jsonMode) {
                this.responseFormat = new Format("json_object");
            }
        }
    }

    @Getter
    public static class Message {
        private String role;
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Getter
    public static class Format {
        private String type;

        public Format(String type) {
            this.type = type;
        }
    }


    // --- [ ⭐️⭐️⭐️ 2. 기존 코드 (Response DTO) ⭐️⭐️⭐️ ] ---

    /**
     * NewsPipelineService에서 사용할 최종 분석 결과 응답 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalysisResponse {
        private String summary;
        private String insight;
        private String categoryMajor; // 대분류 (e.g., "Telco")
        private List<String> tags; // 키워드 태그 리스트

        public String getTagsJson() {
            if (this.tags == null || this.tags.isEmpty()) {
                return "[]";
            }
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.writeValueAsString(this.tags);
            } catch (JsonProcessingException e) {
                return "[]";
            }
        }
    }
}
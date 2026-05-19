package com.insk.insk_backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insk.insk_backend.dto.OpenAIDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class OpenAIClient {

    @Value("${openai.api.key}")
    private String apiKey;

    // v4 비용 사다리 — 모델 외부화 (멘토 피드백 #2, #8)
    // analysis: 본문 분석 (요약·인사이트·카테고리·태그). 품질 중요한 경우 gpt-4o.
    // simple: 번역·간단 작업. 비용 절감 (기본 gpt-4o-mini).
    @Value("${openai.model.analysis:gpt-4o}")
    private String analysisModel;

    @Value("${openai.model.simple:gpt-4o-mini}")
    private String simpleModel;

    private final String API_URL = "https://api.openai.com/v1/chat/completions";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 🧠 기사 분석용 시스템 프롬프트
    private final String SYSTEM_PROMPT = """
            당신은 SKT 전략기획 담당자입니다.
            제공되는 뉴스 기사 본문을 분석하여, 반드시 아래의 JSON 형식으로만 답변해야 합니다.
            {
              "summary": "기사를 5줄 이내로 요약",
              "insight": "이 뉴스가 SKT에 미칠 영향과 기회 요인을 한 문장으로 분석",
              "categoryMajor": "반드시 다음 4개 중 하나만 선택: 'Telco', 'LLM', 'INFRA', 'AI Ecosystem'. 다른 값은 절대 사용하지 마세요.",
              "tags": ["핵심 키워드 1", "핵심 키워드 2", "핵심 키워드 3"]
            }
            
            중요: categoryMajor는 반드시 정확히 다음 중 하나여야 합니다:
            - "Telco" (통신 관련)
            - "LLM" (대규모 언어 모델 관련)
            - "INFRA" (인프라 관련)
            - "AI Ecosystem" (AI 생태계 관련)
            
            다른 값(예: "Service", "기타", "Other" 등)은 절대 사용하지 마세요.
            """;

    // ----------------------------------------------------
    // ✅ 1. 한국어 키워드를 영어 키워드로 번역하는 메서드
    // ----------------------------------------------------
    public String translateKeyword(String keywordKo) {
        // 번역용 프롬프트 (영어만 깔끔하게 나오도록 강제)
        String userPrompt = """
                아래 한국어 키워드를 자연스러운 영어 검색 키워드로 번역해줘.
                - 출력 형식: 번역된 영어만 출력 (따옴표, 설명, 불필요한 텍스트 금지)
                - 예시 입력: "삼성전자" → 예시 출력: Samsung Electronics
                - 예시 입력: "인공지능 반도체" → 예시 출력: AI semiconductor

                한국어 키워드: %s
                """.formatted(keywordKo);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // 번역은 JSON 모드 필요 X → jsonMode=false, systemPrompt=null
            // simple 작업이라 gpt-4o-mini로 비용 절감
            OpenAIDto.ChatRequest req = new OpenAIDto.ChatRequest(
                    simpleModel,
                    null,          // system message 없음
                    userPrompt,    // user message
                    false          // JSON 모드 아님 (그냥 텍스트)
            );

            HttpEntity<String> entity =
                    new HttpEntity<>(objectMapper.writeValueAsString(req), headers);

            String response =
                    restTemplate.postForObject(API_URL, entity, String.class);

            JsonNode root = objectMapper.readTree(response);
            String content =
                    root.path("choices").path(0).path("message").path("content").asText();

            return content.trim(); // 양쪽 공백 제거 후 반환

        } catch (Exception e) {
            log.error("❗ 번역 실패, 원본 키워드로 fallback: {}", keywordKo, e);
            // 문제가 생기면 그냥 원래 한국어 키워드를 그대로 써도 되게 fallback
            return keywordKo;
        }
    }

    // ----------------------------------------------------
    // ✅ 2. 기사 분석용 메서드 (기존대로 유지)
    // ----------------------------------------------------
    public OpenAIDto.AnalysisResponse analyzeArticle(String articleBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // analysis 모델 외부화 — application.properties에서 gpt-4o / gpt-4o-mini 전환
        OpenAIDto.ChatRequest requestBody = new OpenAIDto.ChatRequest(
                analysisModel,
                SYSTEM_PROMPT,     // 시스템 프롬프트
                articleBody,       // 기사 본문
                true               // JSON 모드 활성화 (모델이 JSON으로만 답하게)
        );

        try {
            HttpEntity<String> entity =
                    new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            String response =
                    restTemplate.postForObject(API_URL, entity, String.class);

            JsonNode root = objectMapper.readTree(response);
            String jsonContent =
                    root.path("choices").path(0).path("message").path("content").asText();

            // content 안에 있는 JSON 문자열을 AnalysisResponse DTO로 변환
            return objectMapper.readValue(jsonContent, OpenAIDto.AnalysisResponse.class);

        } catch (Exception e) {
            log.error("OpenAI API 분석 실패: {}", e.getMessage());
            return null;
        }
    }
}

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
    private final RestTemplate restTemplate;   // 멘토 #5: 타임아웃 설정된 외부 API 전용 RestTemplate 주입
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAIClient(RestTemplate externalApiRestTemplate) {
        this.restTemplate = externalApiRestTemplate;
    }

    // 🧠 기사 분석용 시스템 프롬프트 (v4 taxonomy 재설계 — 2026-05-22)
    // AI Ecosystem → AI Business 변경 + LLM 정의 강화 (fallback bucket 오염 방지)
    private final String SYSTEM_PROMPT = """
            당신은 SKT 전략기획 담당자입니다.
            제공되는 뉴스 기사 본문을 분석하여, 반드시 아래의 JSON 형식으로만 답변해야 합니다.
            {
              "summary": "기사를 5줄 이내로 요약",
              "insight": "이 뉴스가 SKT에 미칠 영향과 기회 요인을 한 문장으로 분석",
              "categoryMajor": "반드시 다음 4개 중 하나만 선택: 'Telco', 'LLM', 'INFRA', 'AI Business'. 다른 값은 절대 사용하지 마세요.",
              "tags": ["핵심 키워드 1", "핵심 키워드 2", "핵심 키워드 3"]
            }

            카테고리 정의 (반드시 이 기준으로 분류):

            - "LLM" (foundation model · 대규모 언어모델 기술 자체)
                예: GPT-4·GPT-5·Claude·Gemini·Llama 모델 출시·기술,
                    파인튜닝, RLHF, AI 에이전트, 임베딩, 멀티모달 LLM,
                    프롬프트 엔지니어링, 오픈소스 LLM, vector DB·RAG 기술

            - "INFRA" (AI 하드웨어·인프라)
                예: NVIDIA H100/H200, GPU·TPU·NPU, HBM·메모리, AI 반도체,
                    데이터센터, inference 인프라, 온디바이스 AI 칩,
                    삼성/SK하이닉스 하드웨어

            - "Telco" (통신사·네트워크)
                예: SKT/KT/LG U+ 사업·전략, 5G·6G 네트워크,
                    통신 AI, 모바일 인프라

            - "AI Business" (AI 산업·정책·투자·시장·기업 전략 — 비기술 관점)
                예: AI 회사 매출·IPO·M&A, 정부 정책·규제·법안,
                    AI 일자리·저작권·윤리, AI 시장 규모·투자 동향,
                    글로벌 AI 허브 경쟁, AI 스타트업 투자

            분류 우선순위:
            1. 특정 모델 기술·출시 기사면 → "LLM"
               (예: "OpenAI, GPT-5 출시" → LLM,
                    "Anthropic, Claude 신규 기능 추가" → LLM)
            2. 하드웨어·반도체·데이터센터 → "INFRA"
            3. 통신사·네트워크 → "Telco"
            4. 위에 안 속하는 산업·정책·투자 기사 → "AI Business"
               (예: "OpenAI 2분기 매출" → AI Business,
                    "한국 정부 AI 정책 발표" → AI Business)

            절대 다른 값(예: "Service", "AI Ecosystem", "기타", "Other" 등)을 사용하지 마세요.
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
        // 기존 호출부 호환용: 실패 시 null 반환.
        try {
            return analyzeArticle(articleBody, analysisModel);
        } catch (OpenAiAnalysisException e) {
            log.error("OpenAI API 분석 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 모델을 지정해 기사 본문을 분석한다. 실패 시 {@link OpenAiAnalysisException}을 던져
     * 상위에서 재시도(@Retryable)·폴백(@Recover)으로 다룰 수 있게 한다.
     */
    public OpenAIDto.AnalysisResponse analyzeArticle(String articleBody, String model) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        OpenAIDto.ChatRequest requestBody = new OpenAIDto.ChatRequest(
                model,
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

            return objectMapper.readValue(jsonContent, OpenAIDto.AnalysisResponse.class);

        } catch (Exception e) {
            // null로 삼키지 않고 예외로 노출 → 재시도·폴백 가능
            throw new OpenAiAnalysisException("OpenAI 분석 실패 (model=" + model + "): " + e.getMessage(), e);
        }
    }
}

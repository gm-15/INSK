package com.insk.insk_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleAnalysis;
import com.insk.insk_backend.domain.ArticleEmbedding;
import com.insk.insk_backend.domain.Keyword;
import com.insk.insk_backend.domain.User;
import com.insk.insk_backend.dto.OpenAIDto;
import com.insk.insk_backend.repository.ArticleAnalysisRepository;
import com.insk.insk_backend.repository.ArticleEmbeddingRepository;
import com.insk.insk_backend.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 멘토 피드백 #3: 외부 호출과 DB 트랜잭션 분리.
 *
 * <p>스크래핑·OpenAI 호출(수 초)은 호출부(NewsPipelineService)에서 트랜잭션 없이 끝내고,
 * 그 결과만 받아 기사 1건의 DB 쓰기(기사+임베딩+분석)를 <b>짧은 트랜잭션</b>으로 처리한다.
 * 느린 외부 I/O 동안 DB 커넥션을 점유하지 않아 커넥션 풀 고갈(pool exhaustion)을 방지하고,
 * 기사 단위 원자성(기사·임베딩·분석을 한 트랜잭션)도 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticlePersistenceService {

    private final ArticleRepository articleRepository;
    private final ArticleEmbeddingRepository embeddingRepository;
    private final ArticleAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 분석 성공 기사: 기사+임베딩+분석을 한 트랜잭션으로 저장 (외부 호출은 이미 끝난 상태). */
    @Transactional
    public void persistAnalyzed(Article article, String embeddingJson,
                                OpenAIDto.AnalysisResponse ar, Keyword keyword, User user) {
        article.markAnalysisCompleted();
        articleRepository.save(article);
        if (embeddingJson != null) {
            embeddingRepository.save(ArticleEmbedding.builder()
                    .article(article)
                    .embeddingJson(embeddingJson)
                    .build());
        }
        saveAnalysis(article, ar, keyword, user);
    }

    /** 분석 최종 실패 기사: 유실 대신 ANALYSIS_FAILED로 보존 (DLQ). */
    @Transactional
    public void persistFailed(Article article) {
        article.markAnalysisFailed();
        articleRepository.save(article);
    }

    /**
     * DLQ 재처리 실패: retry_count를 올리고 한도 초과 시 DEAD로 격리한다.
     * @return DEAD로 격리됐으면 true
     */
    @Transactional
    public boolean persistReprocessFailure(Article article, int maxAttempts) {
        article.incrementRetryCount();
        boolean dead = article.getRetryCount() >= maxAttempts;
        if (dead) {
            article.markDead();
        }
        articleRepository.save(article);
        return dead;
    }

    private void saveAnalysis(Article article, OpenAIDto.AnalysisResponse ar, Keyword keyword, User user) {
        String category = validateCategory(ar.getCategoryMajor());

        // 키워드 정보를 tags에 포함 (기존 tags JSON에 keyword 필드 추가)
        String tagsJson = ar.getTagsJson();
        if (keyword != null) {
            try {
                Map<String, Object> tagsMap = new HashMap<>();
                if (tagsJson != null && !tagsJson.isEmpty()) {
                    try {
                        tagsMap = objectMapper.readValue(tagsJson, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        // JSON 파싱 실패 시 빈 맵 사용
                    }
                }
                tagsMap.put("searchKeyword", keyword.getKeyword());
                tagsMap.put("keywordId", keyword.getId());
                tagsJson = objectMapper.writeValueAsString(tagsMap);
            } catch (Exception e) {
                log.warn("키워드 정보를 tags에 추가하는데 실패했습니다.", e);
            }
        }

        analysisRepository.save(
                ArticleAnalysis.builder()
                        .article(article)
                        .summary(ar.getSummary())
                        .insight(ar.getInsight())
                        .category(category)
                        .tags(tagsJson)
                        .user(user)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    /**
     * 카테고리를 검증하고 허용된 값으로 변환.
     * v4 taxonomy 재설계(2026-05-22): AI Ecosystem → AI Business. 허용: Telco, LLM, INFRA, AI Business.
     */
    private String validateCategory(String category) {
        if (category == null || category.isBlank()) {
            return "AI Business"; // 기본값 — 산업·정책 일반 뉴스 비중이 큼
        }
        String normalized = category.trim();
        if (normalized.equals("Telco") || normalized.equals("LLM") ||
            normalized.equals("INFRA") || normalized.equals("AI Business")) {
            return normalized;
        }
        String lower = normalized.toLowerCase();
        if (lower.equals("telco")) return "Telco";
        if (lower.equals("llm")) return "LLM";
        if (lower.equals("infra")) return "INFRA";
        if (lower.contains("ai business") || lower.contains("ai-business")) return "AI Business";
        if (lower.contains("ai ecosystem") || lower.contains("ai-ecosystem")) return "AI Business";
        log.warn("⚠️ 잘못된 카테고리 감지: '{}', 기본값 'AI Business'로 변경", category);
        return "AI Business";
    }
}

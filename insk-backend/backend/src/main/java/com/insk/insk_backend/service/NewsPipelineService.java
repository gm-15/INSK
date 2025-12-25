package com.insk.insk_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insk.insk_backend.client.AITimesClient;
import com.insk.insk_backend.client.EmbeddingClient;
import com.insk.insk_backend.client.NaverNewsClient;
import com.insk.insk_backend.client.OpenAIClient;
import com.insk.insk_backend.client.TheGuruClient;
import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleAnalysis;
import com.insk.insk_backend.domain.ArticleEmbedding;
import com.insk.insk_backend.domain.Keyword;
import com.insk.insk_backend.domain.User;
import com.insk.insk_backend.dto.AITimesDto;
import com.insk.insk_backend.dto.NaverNewsDto;
import com.insk.insk_backend.dto.OpenAIDto;
import com.insk.insk_backend.dto.TheGuruDto;
import com.insk.insk_backend.repository.ArticleAnalysisRepository;
import com.insk.insk_backend.repository.ArticleEmbeddingRepository;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.KeywordRepository;
import com.insk.insk_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsPipelineService {

    private final KeywordRepository keywordRepository;
    private final ArticleRepository articleRepository;
    private final ArticleAnalysisRepository analysisRepository;
    private final ArticleEmbeddingRepository embeddingRepository;
    private final UserRepository userRepository;

    private final NaverNewsClient naverNewsClient;
    private final AITimesClient aiTimesClient;
    private final TheGuruClient theGuruClient;

    private final EmbeddingClient embeddingClient;
    private final OpenAIClient openAIClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final double DUP_THRESHOLD = 0.88;

    @Transactional
    @Scheduled(cron = "0 0 8 * * *")
    public void runPipeline() {
        // 스케줄러는 사용자 정보 없이 실행 (전체 사용자 대상)
        runPipelineSync(null);
    }

    /**
     * 비동기로 파이프라인 실행 (타임아웃 방지)
     */
    @Async("taskExecutor")
    @Transactional
    public void runPipelineAsync(String userEmail) {
        runPipelineSync(userEmail);
    }

    @Transactional
    public void runPipelineSync(String userEmail) {
        try {
            log.info("🚀 뉴스 파이프라인 실행 시작 (사용자: {})", userEmail);
            // 현재 사용자의 승인된 키워드만 사용
            List<Keyword> keywords;
            if (userEmail != null && !userEmail.isBlank()) {
                keywords = keywordRepository.findByUser_EmailAndApprovedTrue(userEmail);
            } else {
                // 사용자 정보가 없으면 전체 승인된 키워드 사용 (하위 호환성)
                keywords = keywordRepository.findByApprovedTrue();
            }
            
            if (keywords.isEmpty()) {
                log.warn("⚠️ 승인된 키워드 없음 (사용자: {})", userEmail);
                return;
            }
            log.info("📝 사용할 키워드 수: {} (사용자: {})", keywords.size(), userEmail);

        // -----------------------
        // 네이버 뉴스
        // -----------------------
        for (Keyword k : keywords) {
            log.info("🔍 키워드로 검색 중: {} (사용자: {})", k.getKeyword(), userEmail);
            List<NaverNewsDto> list = naverNewsClient.searchNews(k.getKeyword(), 10);
            processNaver(list, k, userEmail);
        }

        // -----------------------
        // AITimes RSS
        // -----------------------
        List<AITimesDto> aiList = aiTimesClient.fetchNews(10);
        processAITimes(aiList, userEmail);

        // -----------------------
        // TheGuru RSS
        // -----------------------
        List<TheGuruDto> guruList = theGuruClient.fetchNews(10);
        processTheGuru(guruList, userEmail);

        log.info("🎉 Pipeline 완료");
        } catch (Exception e) {
            log.error("❌ 뉴스 파이프라인 실행 중 오류 발생", e);
            throw new RuntimeException("뉴스 파이프라인 실행 실패: " + e.getMessage(), e);
        }
    }

    private void processNaver(List<NaverNewsDto> list, Keyword keyword, String userEmail) {
        User user = null;
        if (userEmail != null && !userEmail.isBlank()) {
            user = userRepository.findByEmail(userEmail).orElse(null);
        }
        
        for (NaverNewsDto item : list) {

            String url = item.getOriginalUrl();
            if (articleRepository.existsByOriginalUrl(url)) continue;

            String body = naverNewsClient.scrapeArticleBody(url);
            if (body == null || body.isBlank()) continue;
            if (isDuplicate(body)) continue;

            OpenAIDto.AnalysisResponse ar = openAIClient.analyzeArticle(body);
            if (ar == null) continue;

            // HTML 태그 제거 (예: <b></b>, <strong></strong> 등)
            String cleanTitle = removeHtmlTags(item.getTitle());

            Article a = Article.builder()
                    .title(cleanTitle)
                    .originalUrl(url)
                    .publishedAt(item.getPubDate())
                    .createdAt(LocalDateTime.now())
                    .source("Naver")
                    .country("KR")
                    .language("ko")
                    .build();

            articleRepository.save(a);

            saveEmbedding(a, body);
            saveAnalysis(a, ar, keyword, user);
        }
    }

    private void processAITimes(List<AITimesDto> list, String userEmail) {
        User user = null;
        if (userEmail != null && !userEmail.isBlank()) {
            user = userRepository.findByEmail(userEmail).orElse(null);
        }
        
        for (AITimesDto item : list) {

            String url = item.getOriginalUrl();
            if (articleRepository.existsByOriginalUrl(url)) continue;

            String body = (item.getSummary() == null ? "" : item.getSummary());
            if (body.length() < 10)
                body = item.getTitle() + " " + body;

            if (body.isBlank()) continue;
            if (isDuplicate(body)) continue;

            OpenAIDto.AnalysisResponse ar = openAIClient.analyzeArticle(body);
            if (ar == null) continue;

            Article a = Article.builder()
                    .title(item.getTitle())
                    .originalUrl(url)
                    .publishedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .source("AITimes")
                    .country("KR")
                    .language("ko")
                    .build();

            articleRepository.save(a);

            saveEmbedding(a, body);
            saveAnalysis(a, ar, null, user);
        }
    }

    private void processTheGuru(List<TheGuruDto> list, String userEmail) {
        User user = null;
        if (userEmail != null && !userEmail.isBlank()) {
            user = userRepository.findByEmail(userEmail).orElse(null);
        }
        
        for (TheGuruDto item : list) {

            String url = item.getOriginalUrl();
            if (articleRepository.existsByOriginalUrl(url)) continue;

            String body = (item.getSummary() == null ? "" : item.getSummary());
            if (body.length() < 10)
                body = item.getTitle() + " " + body;

            if (body.isBlank()) continue;
            if (isDuplicate(body)) continue;

            OpenAIDto.AnalysisResponse ar = openAIClient.analyzeArticle(body);
            if (ar == null) continue;

            Article a = Article.builder()
                    .title(item.getTitle().replaceAll("<[^>]+>", ""))
                    .originalUrl(url)
                    .publishedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .source("TheGuru")
                    .country("KR")
                    .language("ko")
                    .build();

            articleRepository.save(a);

            saveEmbedding(a, body);
            saveAnalysis(a, ar, null, user);
        }
    }

    private boolean isDuplicate(String body) {
        List<ArticleEmbedding> list = embeddingRepository.findAll();
        if (list.isEmpty()) return false;

        List<Double> newEmb = embeddingClient.embed(body);
        if (newEmb == null) return false;

        for (ArticleEmbedding emb : list) {
            try {
                List<Double> oldEmb =
                        objectMapper.readValue(emb.getEmbeddingJson(), new TypeReference<>() {});
                double sim = cosine(newEmb, oldEmb);
                if (sim >= DUP_THRESHOLD) return true;
            } catch (Exception ignored) {}
        }

        return false;
    }

    private double cosine(List<Double> a, List<Double> b) {
        double dot = 0, magA = 0, magB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            magA += a.get(i) * a.get(i);
            magB += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(magA) * Math.sqrt(magB));
    }

    private void saveEmbedding(Article article, String body) {
        try {
            List<Double> emb = embeddingClient.embed(body);
            String json = objectMapper.writeValueAsString(emb);

            embeddingRepository.save(
                    ArticleEmbedding.builder()
                            .article(article)
                            .embeddingJson(json)
                            .build()
            );

        } catch (Exception e) {
            log.error("Embedding 저장 실패", e);
        }
    }

    private void saveAnalysis(Article article, OpenAIDto.AnalysisResponse ar, Keyword keyword, User user) {
        // 카테고리 검증: 허용된 4개 카테고리만 사용
        String category = validateCategory(ar.getCategoryMajor());
        
        // 키워드 정보를 tags에 포함 (기존 tags JSON에 keyword 필드 추가)
        String tagsJson = ar.getTagsJson();
        if (keyword != null) {
            try {
                // 기존 tags JSON에 keyword 정보 추가
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
     * 카테고리를 검증하고 허용된 값으로 변환
     * 허용된 카테고리: Telco, LLM, INFRA, AI Ecosystem
     */
    private String validateCategory(String category) {
        if (category == null || category.isBlank()) {
            return "INFRA"; // 기본값
        }
        
        String normalized = category.trim();
        
        // 정확히 일치하는 경우
        if (normalized.equals("Telco") || normalized.equals("LLM") || 
            normalized.equals("INFRA") || normalized.equals("AI Ecosystem")) {
            return normalized;
        }
        
        // 대소문자 무시하고 비교
        String lower = normalized.toLowerCase();
        if (lower.equals("telco")) return "Telco";
        if (lower.equals("llm")) return "LLM";
        if (lower.equals("infra")) return "INFRA";
        if (lower.contains("ai ecosystem") || lower.contains("ai-ecosystem")) return "AI Ecosystem";
        
        // Service, 기타 등의 잘못된 카테고리는 기본값으로 변경
        log.warn("⚠️ 잘못된 카테고리 감지: '{}', 기본값 'INFRA'로 변경", category);
        return "INFRA";
    }
    
    /**
     * HTML 태그 제거 (예: <b></b>, <strong></strong>, <em></em> 등)
     */
    private String removeHtmlTags(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        // HTML 태그 제거 및 HTML 엔티티 디코딩
        return text
                .replaceAll("<[^>]+>", "") // HTML 태그 제거
                .replaceAll("&nbsp;", " ") // &nbsp; → 공백
                .replaceAll("&amp;", "&")  // &amp; → &
                .replaceAll("&lt;", "<")   // &lt; → <
                .replaceAll("&gt;", ">")   // &gt; → >
                .replaceAll("&quot;", "\"") // &quot; → "
                .replaceAll("&#39;", "'")   // &#39; → '
                .trim();
    }
}



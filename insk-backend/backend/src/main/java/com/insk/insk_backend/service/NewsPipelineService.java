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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // v4 비용 사다리 — 제목 Jaccard 중복 체크 (멘토 피드백 #1, #8)
    // 완벽 dedup 아닌 cheap heuristic 1차 필터. retrieval corpus 다양성 보존 우선.
    @Value("${pipeline.title-jaccard-threshold:0.6}")
    private double titleJaccardThreshold;

    @Value("${pipeline.dedup-window-days:7}")
    private int dedupWindowDays;

    // 본문 임베딩 dedup 임계치는 제거됨 (cheap heuristic으로 대체).
    // 운영 데이터로 ROC tuning 후 application.yml로 외부화 예정.

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
            if (articleRepository.existsByOriginalUrl(url)) continue;   // [1] URL 매칭 ($0)

            // HTML 태그 제거 (예: <b></b>, <strong></strong> 등)
            String cleanTitle = removeHtmlTags(item.getTitle());

            // [2] 제목 Jaccard ($0) — 스크래핑·LLM 호출 전 cheap heuristic
            if (isDuplicateByTitle(cleanTitle)) continue;

            String body = naverNewsClient.scrapeArticleBody(url);       // 스크래핑
            if (body == null || body.isBlank()) continue;

            OpenAIDto.AnalysisResponse ar = openAIClient.analyzeArticle(body); // [3] LLM 분석
            if (ar == null) continue;

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
            if (articleRepository.existsByOriginalUrl(url)) continue;   // [1] URL 매칭

            // [2] 제목 Jaccard — LLM 호출 전 cheap heuristic
            if (isDuplicateByTitle(item.getTitle())) continue;

            String body = (item.getSummary() == null ? "" : item.getSummary());
            if (body.length() < 10)
                body = item.getTitle() + " " + body;

            if (body.isBlank()) continue;

            OpenAIDto.AnalysisResponse ar = openAIClient.analyzeArticle(body); // [3] LLM
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
            if (articleRepository.existsByOriginalUrl(url)) continue;   // [1] URL 매칭

            // [2] 제목 Jaccard — LLM 호출 전 cheap heuristic
            if (isDuplicateByTitle(item.getTitle())) continue;

            String body = (item.getSummary() == null ? "" : item.getSummary());
            if (body.length() < 10)
                body = item.getTitle() + " " + body;

            if (body.isBlank()) continue;

            OpenAIDto.AnalysisResponse ar = openAIClient.analyzeArticle(body); // [3] LLM
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

    // ========================================================================
    // v4 비용 사다리 — 제목 Jaccard 중복 체크 (LLM 호출 전 cheap heuristic)
    //
    // 변경 이력:
    //   v3: 본문 전체를 OpenAI 임베딩 → DB 전체 임베딩(findAll, OOM 위험)과 코사인
    //       비교 → 비용·메모리 양쪽 위험 (멘토 피드백 #1)
    //   v4: 제목만 normalize 후 Jaccard 유사도. 최근 N일 윈도우 조회. API 비용 0.
    //
    // 한계 (의도적):
    //   - 완벽 dedup 아님. 같은 사건 다른 매체 보도는 일부 통과 가능 (false negative)
    //   - 다른 사건이지만 제목 유사어 많은 케이스는 거를 수 있음 (false positive)
    //   - 학교 프로젝트 retrieval corpus 다양성 보존이 우선이라 보수적 임계치 채택
    //   - 정밀 dedup은 향후 embedding ANN reranking 단계로 별도 처리 예정
    // ========================================================================

    /**
     * 제목 정규화 — Jaccard 토큰화 전 노이즈 제거.
     * lowercase + HTML 태그 + 특수문자 + 다중 공백 정리.
     */
    private String normalizeForJaccard(String title) {
        if (title == null) return "";
        return title
                .toLowerCase()
                .replaceAll("<[^>]+>", "")           // HTML 태그
                .replaceAll("[\\p{Punct}]", " ")     // 특수문자 → 공백
                .replaceAll("\\s+", " ")             // 다중 공백
                .trim();
    }

    private Set<String> tokenize(String normalized) {
        if (normalized == null || normalized.isBlank()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(normalized.split(" ")));
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    /**
     * 제목 Jaccard 기반 중복 판정.
     * 최근 N일치 제목만 조회 (findAll 금지, OOM 회피).
     */
    private boolean isDuplicateByTitle(String newTitle) {
        if (newTitle == null || newTitle.isBlank()) return false;

        LocalDateTime since = LocalDateTime.now().minusDays(dedupWindowDays);
        List<String> recentTitles = articleRepository.findTitlesPublishedAfter(since);
        if (recentTitles.isEmpty()) return false;

        Set<String> newTokens = tokenize(normalizeForJaccard(newTitle));
        if (newTokens.isEmpty()) return false;

        for (String existing : recentTitles) {
            Set<String> existingTokens = tokenize(normalizeForJaccard(existing));
            double sim = jaccardSimilarity(newTokens, existingTokens);
            if (sim >= titleJaccardThreshold) {
                log.info("🔍 제목 중복 감지 (window={}d, threshold={}, sim={}): '{}' ~ '{}'",
                        dedupWindowDays, titleJaccardThreshold, String.format("%.2f", sim),
                        newTitle, existing);
                return true;
            }
        }
        return false;
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



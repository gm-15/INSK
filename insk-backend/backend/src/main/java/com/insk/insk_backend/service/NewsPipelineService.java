package com.insk.insk_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insk.insk_backend.client.AITimesClient;
import com.insk.insk_backend.client.EmbeddingClient;
import com.insk.insk_backend.client.NaverNewsClient;
import com.insk.insk_backend.client.OpenAiAnalysisException;
import com.insk.insk_backend.client.TheGuruClient;
import com.insk.insk_backend.domain.AnalysisStatus;
import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.Keyword;
import com.insk.insk_backend.domain.User;
import com.insk.insk_backend.dto.AITimesDto;
import com.insk.insk_backend.dto.NaverNewsDto;
import com.insk.insk_backend.dto.OpenAIDto;
import com.insk.insk_backend.dto.TheGuruDto;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.KeywordRepository;
import com.insk.insk_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsPipelineService {

    private final KeywordRepository keywordRepository;
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;

    private final NaverNewsClient naverNewsClient;
    private final AITimesClient aiTimesClient;
    private final TheGuruClient theGuruClient;

    private final EmbeddingClient embeddingClient;
    private final LlmAnalysisService llmAnalysisService;
    // 멘토 #3: DB 쓰기(짧은 트랜잭션) 전담. 외부 호출은 이 클래스(트랜잭션 밖)에서 끝낸다.
    private final ArticlePersistenceService persistenceService;
    // 멘토 #4: 기사별 처리 병렬화 전용 풀 (빈 이름과 필드명이 같아 by-name 주입).
    private final Executor pipelineItemExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // v4 비용 사다리 — 제목 Jaccard 중복 체크 (멘토 피드백 #1, #8)
    // 완벽 dedup 아닌 cheap heuristic 1차 필터. retrieval corpus 다양성 보존 우선.
    @Value("${pipeline.title-jaccard-threshold:0.6}")
    private double titleJaccardThreshold;

    @Value("${pipeline.dedup-window-days:7}")
    private int dedupWindowDays;

    // DLQ 재처리 한도 (멘토 피드백 #5). 초과 시 DEAD로 격리해 영구 실패 기사의 유료 호출을 차단.
    // 비용 knob이라 외부화. 필드 초기값(3)은 Spring 미주입 환경(단위 테스트)용 안전값.
    @Value("${openai.dlq.max-reprocess-attempts:3}")
    private int maxReprocessAttempts = 3;

    // 본문 임베딩 dedup 임계치는 제거됨 (cheap heuristic으로 대체).
    // 운영 데이터로 ROC tuning 후 application.yml로 외부화 예정.

    @Scheduled(cron = "0 0 8 * * *")
    public void runPipeline() {
        // 스케줄러는 사용자 정보 없이 실행 (전체 사용자 대상)
        runPipelineSync(null);
    }

    /**
     * 비동기로 파이프라인 실행 (타임아웃 방지)
     */
    @Async("taskExecutor")
    public void runPipelineAsync(String userEmail) {
        runPipelineSync(userEmail);
    }

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

    /**
     * DLQ 재처리 (멘토 피드백 #5).
     * 재시도·폴백 모두 실패해 ANALYSIS_FAILED로 보존된 기사를 본문 재수집 후 다시 분석한다.
     * 재실패하면 retry_count를 올리고, 한도(maxReprocessAttempts) 초과 시 DEAD로 격리해
     * 재처리 풀(FAILED 조회)에서 빼 영구 실패 기사의 유료 호출 비용을 차단한다.
     */
    public void reprocessFailedAnalyses() {
        List<Article> failed = articleRepository.findByAnalysisStatus(AnalysisStatus.FAILED);
        log.info("🔁 DLQ 재처리 시작: {}건", failed.size());

        for (Article a : failed) {
            // 외부 호출(스크랩·분석·임베딩)은 트랜잭션 밖, DB 쓰기만 persistenceService의 짧은 트랜잭션.
            String body = naverNewsClient.scrapeArticleBody(a.getOriginalUrl());
            if (body == null || body.isBlank()) continue;

            try {
                OpenAIDto.AnalysisResponse ar = llmAnalysisService.analyze(body);
                String embeddingJson = embedJson(body);
                persistenceService.persistAnalyzed(a, embeddingJson, ar, null, null);
                log.info("✅ DLQ 재처리 성공: {}", a.getTitle());
            } catch (OpenAiAnalysisException e) {
                boolean dead = persistenceService.persistReprocessFailure(a, maxReprocessAttempts);
                if (dead) {
                    log.warn("DLQ 재처리 {}회 초과 → DEAD 격리: {}", maxReprocessAttempts, a.getTitle());
                } else {
                    log.warn("DLQ 재처리 재실패({}/{}, FAILED 유지): {} ({})",
                            a.getRetryCount(), maxReprocessAttempts, a.getTitle(), e.getMessage());
                }
            }
        }
    }

    private void processNaver(List<NaverNewsDto> list, Keyword keyword, String userEmail) {
        User user = resolveUser(userEmail);
        // 멘토 #4: 기사별 처리를 병렬 실행. #3로 각 기사가 독립 짧은 트랜잭션이라 스레드별 커넥션으로 안전.
        runItemsInParallel(list.stream()
                .map(item -> (Runnable) () -> processNaverItem(item, keyword, user))
                .toList());
    }

    private void processNaverItem(NaverNewsDto item, Keyword keyword, User user) {
        try {
            String url = item.getOriginalUrl();
            if (articleRepository.existsByOriginalUrl(url)) return;     // [1] URL 매칭 ($0)

            String cleanTitle = removeHtmlTags(item.getTitle());
            if (isDuplicateByTitle(cleanTitle)) return;                // [2] 제목 Jaccard ($0)

            String body = naverNewsClient.scrapeArticleBody(url);
            if (body == null || body.isBlank()) return;

            Article a = Article.builder()
                    .title(cleanTitle)
                    .originalUrl(url)
                    .publishedAt(item.getPubDate())
                    .createdAt(LocalDateTime.now())
                    .source("Naver")
                    .country("KR")
                    .language("ko")
                    .build();

            persistOrDlq(a, body, keyword, user, cleanTitle);
        } catch (Exception e) {
            // URL 유니크 경쟁 등 개별 기사 실패는 배치 전체를 막지 않도록 건너뛴다.
            log.warn("기사 처리 실패(건너뜀): {} ({})", item.getOriginalUrl(), e.toString());
        }
    }

    private void processAITimes(List<AITimesDto> list, String userEmail) {
        User user = resolveUser(userEmail);
        runItemsInParallel(list.stream()
                .map(item -> (Runnable) () -> processAITimesItem(item, user))
                .toList());
    }

    private void processAITimesItem(AITimesDto item, User user) {
        try {
            String url = item.getOriginalUrl();
            if (articleRepository.existsByOriginalUrl(url)) return;     // [1] URL 매칭
            if (isDuplicateByTitle(item.getTitle())) return;           // [2] 제목 Jaccard

            String body = (item.getSummary() == null ? "" : item.getSummary());
            if (body.length() < 10) body = item.getTitle() + " " + body;
            if (body.isBlank()) return;

            LocalDateTime parsedPubDate = item.getPublishedAt();
            Article a = Article.builder()
                    .title(item.getTitle())
                    .originalUrl(url)
                    .publishedAt(parsedPubDate != null ? parsedPubDate : LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .source("AITimes")
                    .country("KR")
                    .language("ko")
                    .build();

            persistOrDlq(a, body, null, user, item.getTitle());
        } catch (Exception e) {
            log.warn("기사 처리 실패(건너뜀): {} ({})", item.getOriginalUrl(), e.toString());
        }
    }

    private void processTheGuru(List<TheGuruDto> list, String userEmail) {
        User user = resolveUser(userEmail);
        runItemsInParallel(list.stream()
                .map(item -> (Runnable) () -> processTheGuruItem(item, user))
                .toList());
    }

    private void processTheGuruItem(TheGuruDto item, User user) {
        try {
            String url = item.getOriginalUrl();
            if (articleRepository.existsByOriginalUrl(url)) return;     // [1] URL 매칭
            if (isDuplicateByTitle(item.getTitle())) return;           // [2] 제목 Jaccard

            String body = (item.getSummary() == null ? "" : item.getSummary());
            if (body.length() < 10) body = item.getTitle() + " " + body;
            if (body.isBlank()) return;

            LocalDateTime parsedPubDate = item.getPublishedAt();
            Article a = Article.builder()
                    .title(item.getTitle().replaceAll("<[^>]+>", ""))
                    .originalUrl(url)
                    .publishedAt(parsedPubDate != null ? parsedPubDate : LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .source("TheGuru")
                    .country("KR")
                    .language("ko")
                    .build();

            persistOrDlq(a, body, null, user, item.getTitle());
        } catch (Exception e) {
            log.warn("기사 처리 실패(건너뜀): {} ({})", item.getOriginalUrl(), e.toString());
        }
    }

    private User resolveUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) return null;
        return userRepository.findByEmail(userEmail).orElse(null);
    }

    /** 멘토 #4: 작업들을 pipelineItemExecutor에서 병렬 실행하고 모두 끝날 때까지 대기. */
    private void runItemsInParallel(List<Runnable> tasks) {
        CompletableFuture<?>[] futures = tasks.stream()
                .map(t -> CompletableFuture.runAsync(t, pipelineItemExecutor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
    }

    /**
     * 분석(재시도+폴백) → 성공 시 임베딩 후 저장, 최종 실패 시 FAILED 보존(DLQ).
     * 외부 호출(분석·임베딩)은 트랜잭션 밖, DB 쓰기만 persistenceService의 짧은 트랜잭션.
     */
    private void persistOrDlq(Article a, String body, Keyword keyword, User user, String title) {
        OpenAIDto.AnalysisResponse ar;
        try {
            ar = llmAnalysisService.analyze(body);                      // 재시도+폴백
        } catch (OpenAiAnalysisException e) {
            persistenceService.persistFailed(a);                        // 유실 대신 FAILED 보존(DLQ)
            log.warn("분석 최종 실패, DLQ 저장: {} ({})", title, e.getMessage());
            return;
        }
        String embeddingJson = embedJson(body);
        persistenceService.persistAnalyzed(a, embeddingJson, ar, keyword, user);
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

    /** 본문을 임베딩해 JSON 문자열로 반환 (외부 호출이라 트랜잭션 밖). 실패 시 null → 임베딩 없이 저장. */
    private String embedJson(String body) {
        try {
            List<Double> emb = embeddingClient.embed(body);
            return objectMapper.writeValueAsString(emb);
        } catch (Exception e) {
            log.error("Embedding 생성 실패", e);
            return null;
        }
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



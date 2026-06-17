package com.insk.insk_backend.service;

import com.insk.insk_backend.client.AITimesClient;
import com.insk.insk_backend.client.EmbeddingClient;
import com.insk.insk_backend.client.NaverNewsClient;
import com.insk.insk_backend.client.OpenAiAnalysisException;
import com.insk.insk_backend.client.QdrantClient;
import com.insk.insk_backend.client.TheGuruClient;
import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.Keyword;
import com.insk.insk_backend.dto.NaverNewsDto;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.KeywordRepository;
import com.insk.insk_backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase B 회귀 테스트 — 외부 AI 분석 실패 시 기사 보존(DLQ) 위임 (멘토 #5).
 *
 * <p>과거: {@code OpenAIClient.analyzeArticle()}가 실패 시 null을 반환하고 파이프라인이
 * {@code if (ar == null) continue;}로 기사를 버려, 일시적 오류 한 번에 기사가 영구 유실됐다.
 *
 * <p>현재: 재시도·폴백이 모두 실패하면 파이프라인은 기사를 버리지 않고
 * {@code persistenceService.persistFailed}로 위임해 ANALYSIS_FAILED로 보존한다.
 * 실제 상태 전이(FAILED/DEAD)와 트랜잭션 경계는 {@link ArticlePersistenceServiceTest}에서 검증한다.
 */
class NewsPipelineResilienceTest {

    private final KeywordRepository keywordRepository = mock(KeywordRepository.class);
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NaverNewsClient naverNewsClient = mock(NaverNewsClient.class);
    private final AITimesClient aiTimesClient = mock(AITimesClient.class);
    private final TheGuruClient theGuruClient = mock(TheGuruClient.class);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final LlmAnalysisService llmAnalysisService = mock(LlmAnalysisService.class);
    private final ArticlePersistenceService persistenceService = mock(ArticlePersistenceService.class);
    private final QdrantClient qdrantClient = mock(QdrantClient.class);

    // 병렬 실행을 동기(인라인)로 만들어 검증을 결정적으로 — Executor.execute(r) → r.run()
    private final NewsPipelineService service = new NewsPipelineService(
            keywordRepository, articleRepository, userRepository,
            naverNewsClient, aiTimesClient, theGuruClient,
            embeddingClient, llmAnalysisService, persistenceService,
            Runnable::run, qdrantClient);

    @Test
    @DisplayName("재시도·폴백 모두 실패하면 기사를 버리지 않고 persistFailed로 보존 위임한다(DLQ)")
    void analysisFailure_delegatesToPersistFailed_notLost() {
        // given: 키워드 1개 → 네이버 기사 1건 (중복 아님, 본문 정상)
        Keyword kw = mock(Keyword.class);
        when(kw.getKeyword()).thenReturn("AI");

        NaverNewsDto dto = mock(NaverNewsDto.class);
        when(dto.getOriginalUrl()).thenReturn("https://example.com/news/1");
        when(dto.getTitle()).thenReturn("삼성전자, AI 반도체 전략 발표");

        when(keywordRepository.findByApprovedTrue()).thenReturn(List.of(kw));
        when(naverNewsClient.searchNews(any(), anyInt())).thenReturn(List.of(dto));
        when(articleRepository.existsByOriginalUrl(any())).thenReturn(false);
        when(articleRepository.findTitlesPublishedAfter(any())).thenReturn(List.of());
        when(naverNewsClient.scrapeArticleBody(any())).thenReturn("기사 본문 내용");
        // 재시도·폴백까지 모두 실패한 상황: 예외 전파
        when(llmAnalysisService.analyze(any()))
                .thenThrow(new OpenAiAnalysisException("분석 최종 실패", new RuntimeException("boom")));
        when(aiTimesClient.fetchNews(anyInt())).thenReturn(List.of());
        when(theGuruClient.fetchNews(anyInt())).thenReturn(List.of());

        // when
        service.runPipelineSync(null);

        // then: 유실되지 않고 DLQ 보존(persistFailed)으로 위임, 성공 저장은 호출 안 됨
        verify(persistenceService, times(1)).persistFailed(any(Article.class));
        verify(persistenceService, never()).persistAnalyzed(any(), any(), any(), any(), any());
    }
}

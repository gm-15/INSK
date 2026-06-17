package com.insk.insk_backend.service;

import com.insk.insk_backend.client.EmbeddingClient;
import com.insk.insk_backend.client.QdrantClient;
import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleScore;
import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.dto.ArticleDto;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.ArticleScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 부서별 Top-5 추천(getTop5) 단위 테스트 — Qdrant ANN 기반 (멘토 #1).
 *
 * <p>과거(FakeEmbedding 시절)엔 256차원 가짜 벡터와 1536차원 기사 임베딩의 차원 불일치를
 * try-catch가 0.0으로 삼켜 모든 추천이 0점이 되는 silent failure가 있었다. 지금은 임베딩 검색을
 * Qdrant(컬렉션 차원 고정)로 위임해 그 실패 모드 자체가 사라졌고, 본 테스트는 getTop5가
 * Qdrant KNN 결과를 인기점수로 재랭킹해 반환하는 동작을 고정한다.
 */
class DepartmentRecommendationSilentFailureTest {

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleScoreRepository scoreRepository = mock(ArticleScoreRepository.class);
    private final DepartmentInterestService interestService = mock(DepartmentInterestService.class);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final QdrantClient qdrantClient = mock(QdrantClient.class);

    private final DepartmentArticleService service = new DepartmentArticleService(
            articleRepository, scoreRepository, interestService, embeddingClient, qdrantClient);

    @Test
    @DisplayName("Qdrant KNN 결과를 관련도 점수로 반환한다(0점 아님)")
    void getTop5_returnsQdrantHitsWithNonZeroScore() {
        when(interestService.getInterestKeywords(any())).thenReturn(List.of("ai", "llm"));
        when(embeddingClient.embed(any())).thenReturn(constantVector1536(0.5));
        when(qdrantClient.search(any(), anyInt()))
                .thenReturn(List.of(new QdrantClient.ScoredId(1L, 0.73)));

        Article a = mock(Article.class);
        when(a.getArticleId()).thenReturn(1L);
        when(a.getTitle()).thenReturn("삼성전자, AI 반도체 전략 발표");
        when(a.getOriginalUrl()).thenReturn("https://example.com/a/1");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(a));
        when(scoreRepository.findByArticle_ArticleId(1L)).thenReturn(Optional.empty());

        List<ArticleDto.SimpleResponse> top5 = service.getTop5(DepartmentType.T_AI_SERVICE);

        assertThat(top5).hasSize(1);
        assertThat(top5.get(0).getScore())
                .as("Qdrant 관련도(cosine)가 그대로 점수에 반영되어 0이 아니다")
                .isEqualTo(0.73);
    }

    @Test
    @DisplayName("키워드 임베딩을 하나도 만들지 못하면 빈 추천을 반환한다")
    void getTop5_whenAllKeywordEmbeddingsFail_returnsEmpty() {
        when(interestService.getInterestKeywords(any())).thenReturn(List.of("ai"));
        when(embeddingClient.embed(any())).thenReturn(null);

        List<ArticleDto.SimpleResponse> top5 = service.getTop5(DepartmentType.T_AI_SERVICE);

        assertThat(top5).isEmpty();
    }

    @Test
    @DisplayName("Option C: 관련도 높은(점수행 없는) 기사가 점수행만 있는(관련도 낮은) 기사보다 위에 온다")
    void getTop5_relevanceLeadsOverPopularityBaseline() {
        when(interestService.getInterestKeywords(any())).thenReturn(List.of("ai"));
        when(embeddingClient.embed(any())).thenReturn(constantVector1536(1.0));
        // A: 관련도 1.0 + 점수행 없음 / B: 관련도 0.0 + 점수행 54점(과거 상위 독점 케이스)
        when(qdrantClient.search(any(), anyInt())).thenReturn(List.of(
                new QdrantClient.ScoredId(1L, 1.0), new QdrantClient.ScoredId(2L, 0.0)));

        Article a = mock(Article.class);
        when(a.getArticleId()).thenReturn(1L);
        when(a.getTitle()).thenReturn("관련도 높은 기사");
        when(a.getOriginalUrl()).thenReturn("https://example.com/a");
        Article b = mock(Article.class);
        when(b.getArticleId()).thenReturn(2L);
        when(b.getTitle()).thenReturn("점수만 있는 기사");
        when(b.getOriginalUrl()).thenReturn("https://example.com/b");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(a));
        when(articleRepository.findById(2L)).thenReturn(Optional.of(b));
        ArticleScore scoreB = mock(ArticleScore.class);
        when(scoreB.getScore()).thenReturn(54.0);
        when(scoreRepository.findByArticle_ArticleId(1L)).thenReturn(Optional.empty());
        when(scoreRepository.findByArticle_ArticleId(2L)).thenReturn(Optional.of(scoreB));

        List<ArticleDto.SimpleResponse> top5 = service.getTop5(DepartmentType.T_AI_SERVICE);

        assertThat(top5).hasSize(2);
        assertThat(top5.get(0).getArticleId())
                .as("baseline 50 부스트가 제거되어 관련도가 순위를 주도함")
                .isEqualTo(1L);
    }

    private List<Double> constantVector1536(double v) {
        List<Double> list = new ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) list.add(v);
        return list;
    }
}

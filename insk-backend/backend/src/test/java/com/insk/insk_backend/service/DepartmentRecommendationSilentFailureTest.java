package com.insk.insk_backend.service;

import com.insk.insk_backend.client.EmbeddingClient;
import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleEmbedding;
import com.insk.insk_backend.domain.ArticleScore;
import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.dto.ArticleDto;
import com.insk.insk_backend.repository.ArticleEmbeddingRepository;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.ArticleScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 부서 추천 Silent Failure 회귀 테스트.
 *
 * <p>과거: 키워드를 256차원 가짜 벡터(FakeKeywordEmbedding)로 만들어 1536차원 기사 임베딩과
 * 비교했고, 차원 불일치 예외가 calculateRelevance의 catch에서 0.0으로 삼켜져
 * 모든 추천 점수가 0이 되는 silent failure가 있었다. (응답은 200, 추천은 무력화)
 *
 * <p>현재: 키워드도 실제 OpenAI 임베딩(1536차원)을 사용해 차원이 맞고 관련도가 정상 계산된다.
 * 이 테스트는 "정상 기사가 더 이상 조용히 0점으로 죽지 않음"을 고정한다.
 */
class DepartmentRecommendationSilentFailureTest {

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleScoreRepository scoreRepository = mock(ArticleScoreRepository.class);
    private final ArticleEmbeddingRepository embeddingRepository = mock(ArticleEmbeddingRepository.class);
    private final DepartmentInterestService interestService = mock(DepartmentInterestService.class);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);

    private final DepartmentArticleService service = new DepartmentArticleService(
            articleRepository, scoreRepository, embeddingRepository, interestService, embeddingClient);

    @Test
    @DisplayName("수정 후: 실제 임베딩(1536차원)으로 관련도가 계산되어 추천 점수가 0이 아니다")
    void getTop5_realKeywordEmbedding_producesNonZeroScore() {
        // given: 1536차원 임베딩을 가진 정상 기사 1건
        Article article = mock(Article.class);
        when(article.getArticleId()).thenReturn(1L);
        when(article.getTitle()).thenReturn("삼성전자, AI 반도체 전략 발표");
        when(article.getOriginalUrl()).thenReturn("https://example.com/a/1");

        ArticleEmbedding embedding = mock(ArticleEmbedding.class);
        when(embedding.getEmbeddingJson()).thenReturn(json1536());

        when(articleRepository.findAll()).thenReturn(List.of(article));
        when(interestService.getInterestKeywords(any())).thenReturn(List.of("ai", "llm"));
        // 키워드도 기사와 동일한 1536차원으로 임베딩됨 → silent failure 원인 제거
        when(embeddingClient.embed(any())).thenReturn(constantVector1536(0.5));
        when(embeddingRepository.findByArticle_ArticleId(any())).thenReturn(List.of(embedding));
        when(scoreRepository.findByArticle_ArticleId(any())).thenReturn(Optional.empty());

        // when
        List<ArticleDto.SimpleResponse> top5 = service.getTop5(DepartmentType.T_AI_SERVICE);

        // then: 차원이 맞아 관련도가 정상 계산되므로 더 이상 0점이 아니다
        assertThat(top5).hasSize(1);
        assertThat(top5.get(0).getScore())
                .as("실제 임베딩으로 관련도가 계산되어 silent failure가 해소됨")
                .isNotEqualTo(0.0);
    }

    @Test
    @DisplayName("키워드 임베딩을 하나도 만들지 못하면 빈 추천을 반환한다(조용히 0점 대신)")
    void getTop5_whenAllKeywordEmbeddingsFail_returnsEmpty() {
        when(interestService.getInterestKeywords(any())).thenReturn(List.of("ai"));
        when(embeddingClient.embed(any())).thenReturn(null); // 임베딩 실패

        List<ArticleDto.SimpleResponse> top5 = service.getTop5(DepartmentType.T_AI_SERVICE);

        assertThat(top5).isEmpty();
    }

    @Test
    @DisplayName("Option C: 관련도 높은(점수행 없는) 기사가 점수행만 있는(관련도 낮은) 기사보다 위에 온다")
    void getTop5_relevanceLeadsOverPopularityBaseline() {
        // 기사 A: 키워드와 정렬된 임베딩(cosine=1, 높은 관련도) + 점수행 없음
        Article a = mock(Article.class);
        when(a.getArticleId()).thenReturn(1L);
        when(a.getTitle()).thenReturn("관련도 높은 기사");
        when(a.getOriginalUrl()).thenReturn("https://example.com/a");
        ArticleEmbedding embA = mock(ArticleEmbedding.class);
        when(embA.getEmbeddingJson()).thenReturn(constantJson(1.0));

        // 기사 B: 키워드와 직교한 임베딩(cosine=0, 낮은 관련도) + 점수행 54점(과거 상위 독점 케이스)
        Article b = mock(Article.class);
        when(b.getArticleId()).thenReturn(2L);
        when(b.getTitle()).thenReturn("점수만 있는 기사");
        when(b.getOriginalUrl()).thenReturn("https://example.com/b");
        ArticleEmbedding embB = mock(ArticleEmbedding.class);
        when(embB.getEmbeddingJson()).thenReturn(halfPositiveHalfNegativeJson());
        ArticleScore scoreB = mock(ArticleScore.class);
        when(scoreB.getScore()).thenReturn(54.0);

        when(articleRepository.findAll()).thenReturn(List.of(a, b));
        when(interestService.getInterestKeywords(any())).thenReturn(List.of("ai"));
        when(embeddingClient.embed(any())).thenReturn(constantVector1536(1.0));
        when(embeddingRepository.findByArticle_ArticleId(1L)).thenReturn(List.of(embA));
        when(embeddingRepository.findByArticle_ArticleId(2L)).thenReturn(List.of(embB));
        when(scoreRepository.findByArticle_ArticleId(1L)).thenReturn(Optional.empty());
        when(scoreRepository.findByArticle_ArticleId(2L)).thenReturn(Optional.of(scoreB));

        // when
        List<ArticleDto.SimpleResponse> top5 = service.getTop5(DepartmentType.T_AI_SERVICE);

        // then: 점수행이 있다고 상위를 독점하지 않고, 부서 관련도가 높은 A가 1위
        assertThat(top5).hasSize(2);
        assertThat(top5.get(0).getArticleId())
                .as("baseline 50 부스트가 제거되어 관련도가 순위를 주도함")
                .isEqualTo(1L);
    }

    /** OpenAI text-embedding-3-small 출력과 같은 길이(1536)의 임베딩 JSON (0이 아닌 값으로 채움) */
    private String json1536() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1536; i++) {
            if (i > 0) sb.append(",");
            sb.append(0.01 * (i % 100) + 0.001);
        }
        return sb.append("]").toString();
    }

    private List<Double> constantVector1536(double v) {
        List<Double> list = new ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) list.add(v);
        return list;
    }

    /** 모든 원소가 v인 1536차원 JSON. all-1.0 키워드와 cosine=1(완전 정렬). */
    private String constantJson(double v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1536; i++) {
            if (i > 0) sb.append(",");
            sb.append(v);
        }
        return sb.append("]").toString();
    }

    /** 앞 절반 +1.0, 뒤 절반 -1.0인 1536차원 JSON. all-1.0 키워드와 cosine=0(직교). */
    private String halfPositiveHalfNegativeJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1536; i++) {
            if (i > 0) sb.append(",");
            sb.append(i < 768 ? 1.0 : -1.0);
        }
        return sb.append("]").toString();
    }
}

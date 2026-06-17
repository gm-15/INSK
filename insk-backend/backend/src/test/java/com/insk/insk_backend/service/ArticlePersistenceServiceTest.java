package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.AnalysisStatus;
import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.repository.ArticleAnalysisRepository;
import com.insk.insk_backend.repository.ArticleEmbeddingRepository;
import com.insk.insk_backend.repository.ArticleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 멘토 #3/#5 — DB 쓰기 책임(짧은 트랜잭션) 단위 테스트.
 *
 * <p>외부 호출과 분리된 DB 쓰기 로직을 검증한다: 최종 실패 보존(FAILED),
 * 재처리 한도 초과 시 DEAD 격리, 한도 미만 시 FAILED 유지.
 */
class ArticlePersistenceServiceTest {

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleEmbeddingRepository embeddingRepository = mock(ArticleEmbeddingRepository.class);
    private final ArticleAnalysisRepository analysisRepository = mock(ArticleAnalysisRepository.class);

    private final ArticlePersistenceService service =
            new ArticlePersistenceService(articleRepository, embeddingRepository, analysisRepository);

    @Test
    @DisplayName("최종 실패 기사는 ANALYSIS_FAILED로 보존된다(DLQ)")
    void persistFailed_marksFailed() {
        Article a = Article.builder().title("t").originalUrl("https://example.com/1").build();

        service.persistFailed(a);

        assertThat(a.getAnalysisStatus()).isEqualTo(AnalysisStatus.FAILED);
        verify(articleRepository, times(1)).save(a);
    }

    @Test
    @DisplayName("DLQ 재처리가 한도(3) 초과하면 DEAD로 격리하고 true를 반환한다")
    void persistReprocessFailure_exceedingLimit_isolatesAsDead() {
        Article a = Article.builder().title("t").originalUrl("https://example.com/2").build();
        a.markAnalysisFailed();
        a.incrementRetryCount(); // 1
        a.incrementRetryCount(); // 2 (다음 실패면 3 = 한도)

        boolean dead = service.persistReprocessFailure(a, 3);

        assertThat(dead).isTrue();
        assertThat(a.getRetryCount()).isEqualTo(3);
        assertThat(a.getAnalysisStatus()).isEqualTo(AnalysisStatus.DEAD);
        verify(articleRepository, times(1)).save(a);
    }

    @Test
    @DisplayName("DLQ 재처리가 한도 미만이면 retry_count만 올리고 FAILED를 유지한다")
    void persistReprocessFailure_underLimit_staysFailed() {
        Article a = Article.builder().title("t").originalUrl("https://example.com/3").build();
        a.markAnalysisFailed();

        boolean dead = service.persistReprocessFailure(a, 3);

        assertThat(dead).isFalse();
        assertThat(a.getRetryCount()).isEqualTo(1);
        assertThat(a.getAnalysisStatus()).isEqualTo(AnalysisStatus.FAILED);
        verify(articleRepository, times(1)).save(a);
    }
}

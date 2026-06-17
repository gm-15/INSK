package com.insk.insk_backend.service;

import com.insk.insk_backend.client.EmbeddingClient;
import com.insk.insk_backend.client.QdrantClient;
import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleScore;
import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.dto.ArticleDto;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.ArticleScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 부서별 Top-5 추천. 멘토 피드백 #1에 따라 brute-force(findAll + Java cosine)를
 * Qdrant VectorDB의 ANN(HNSW) KNN 검색으로 교체했다.
 *
 * <p>흐름: 부서 키워드 임베딩의 평균을 질의 벡터로 만들어 Qdrant에 KNN을 던져 후보(관련도=cosine)를
 * 받고, 전역 인기점수를 소폭 가산해 재랭킹한 뒤 상위 5개를 반환한다. 메타데이터는 MySQL, 벡터는 Qdrant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleScoreRepository scoreRepository;
    private final DepartmentInterestService interestService;
    private final EmbeddingClient embeddingClient;
    private final QdrantClient qdrantClient;

    // 인기점수 재랭킹 여지를 위해 5보다 넉넉히 후보를 받는다.
    private static final int CANDIDATE_K = 30;
    // 인기점수 raw가 무반응(0)일 때 normalize 결과가 50이 되는 중립값.
    private static final double SCORE_BASELINE = 50.0;
    // 부서 추천에서 전역 인기점수가 줄 수 있는 최대 가산(부서별 관련도를 덮지 않도록 소폭).
    private static final double POPULARITY_BOOST = 0.2;

    @Cacheable(cacheNames = "departmentTop5", key = "#dept.name()")
    public List<ArticleDto.SimpleResponse> getTop5(DepartmentType dept) {
        List<String> keywords = interestService.getInterestKeywords(dept);
        if (keywords.isEmpty()) return List.of();

        // 부서 키워드 임베딩 → 평균 = 질의 벡터.
        List<List<Double>> keywordEmbeddings = keywords.stream()
                .map(embeddingClient::embed)
                .filter(Objects::nonNull)
                .toList();
        if (keywordEmbeddings.isEmpty()) {
            log.warn("부서 {} 키워드 임베딩을 하나도 생성하지 못했습니다.", dept);
            return List.of();
        }
        List<Double> query = averageVector(keywordEmbeddings);

        // Qdrant ANN KNN — 관련도(cosine)는 Qdrant 인덱스가 계산해 반환한다.
        List<QdrantClient.ScoredId> hits = qdrantClient.search(query, CANDIDATE_K);
        if (hits.isEmpty()) {
            log.warn("부서 {} Qdrant 검색 결과 없음(미색인/미가동 가능).", dept);
            return List.of();
        }

        // 후보를 메타데이터 로드 + 인기점수 가산으로 재랭킹 → 상위 5.
        return hits.stream()
                .map(h -> {
                    Article a = articleRepository.findById(h.id()).orElse(null);
                    if (a == null) return null;
                    ArticleScore score = scoreRepository.findByArticle_ArticleId(a.getArticleId()).orElse(null);
                    double finalScore = h.score() + POPULARITY_BOOST * normalizedPopularity(score);
                    return new ArticleDto.SimpleResponse(
                            a.getArticleId(), a.getTitle(), a.getOriginalUrl(), finalScore);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ArticleDto.SimpleResponse::getScore).reversed())
                .limit(5)
                .toList();
    }

    /** 여러 키워드 임베딩을 평균내 하나의 질의 벡터로 만든다. */
    private List<Double> averageVector(List<List<Double>> vectors) {
        int dim = vectors.get(0).size();
        double[] sum = new double[dim];
        for (List<Double> v : vectors) {
            for (int i = 0; i < dim; i++) sum[i] += v.get(i);
        }
        List<Double> avg = new ArrayList<>(dim);
        for (int i = 0; i < dim; i++) avg.add(sum[i] / vectors.size());
        return avg;
    }

    /**
     * 전역 인기점수를 baseline(50)을 제거해 [0,1]로 정규화한다.
     * 점수행이 없거나 중립(50) 이하면 0을 반환해 가산을 주지 않는다.
     */
    private double normalizedPopularity(ArticleScore score) {
        if (score == null) return 0.0;
        return Math.max(0.0, (score.getScore() - SCORE_BASELINE) / SCORE_BASELINE);
    }
}

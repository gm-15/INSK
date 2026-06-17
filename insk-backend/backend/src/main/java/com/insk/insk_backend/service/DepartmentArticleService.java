package com.insk.insk_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insk.insk_backend.client.EmbeddingClient;
import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleAnalysis;
import com.insk.insk_backend.domain.ArticleEmbedding;
import com.insk.insk_backend.domain.ArticleScore;
import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.dto.ArticleDto;
import com.insk.insk_backend.repository.ArticleAnalysisRepository;
import com.insk.insk_backend.repository.ArticleEmbeddingRepository;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.ArticleScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleScoreRepository scoreRepository;
    private final ArticleEmbeddingRepository embeddingRepository;
    private final DepartmentInterestService interestService;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 인기점수 raw가 무반응(0)일 때 normalize 결과가 50이 되는 중립값.
    private static final double SCORE_BASELINE = 50.0;
    // 부서 추천에서 전역 인기점수가 줄 수 있는 최대 가산(부서별 관련도를 덮지 않도록 소폭).
    private static final double POPULARITY_BOOST = 0.2;

    @Cacheable(cacheNames = "departmentTop5", key = "#dept.name()")
    public List<ArticleDto.SimpleResponse> getTop5(DepartmentType dept) {

        List<String> keywords = interestService.getInterestKeywords(dept);
        if (keywords.isEmpty()) return List.of();

        // 부서 키워드 임베딩은 기사와 무관하므로 요청당 1회만 계산해 재사용한다.
        // (과거엔 기사마다 가짜 256차원 벡터를 새로 만들어 1536차원 기사와 비교했고,
        //  차원 불일치 예외가 삼켜져 모든 추천 점수가 0이 되는 silent failure가 있었다.)
        List<List<Double>> keywordEmbeddings = keywords.stream()
                .map(embeddingClient::embed)
                .filter(Objects::nonNull)
                .toList();
        if (keywordEmbeddings.isEmpty()) {
            log.warn("부서 {} 키워드 임베딩을 하나도 생성하지 못했습니다.", dept);
            return List.of();
        }

        List<Article> articles = articleRepository.findAll();

        return articles.stream()
                .map(a -> {
                    double relevance = calculateRelevance(a, keywordEmbeddings);
                    ArticleScore score = scoreRepository.findByArticle_ArticleId(a.getArticleId())
                            .orElse(null);

                    // 부서 추천은 부서별 신호(relevance)가 순위를 정한다.
                    // 전역 인기점수는 baseline(50)을 제거해 정규화한 뒤 소폭만 가산한다.
                    // (과거: score*0.7이 relevance*30을 압도 + baseline 50 탓에 점수행이 있는
                    //  기사가 모든 부서 상위를 독점하던 것을 라이브 측정으로 발견해 재설계.)
                    double finalScore = relevance + POPULARITY_BOOST * normalizedPopularity(score);

                    return new ArticleDto.SimpleResponse(
                            a.getArticleId(),
                            a.getTitle(),
                            a.getOriginalUrl(),
                            finalScore
                    );
                })
                .sorted(Comparator.comparing(ArticleDto.SimpleResponse::getScore).reversed())
                .limit(5)
                .toList();
    }

    /**
     * 전역 인기점수를 baseline(50)을 제거해 [0,1]로 정규화한다.
     * 점수행이 없거나 중립(50) 이하면 0을 반환해 가산을 주지 않는다.
     * (과거: 점수행이 있다는 이유만으로 baseline 50이 적용돼 +0.7*50의 임의 부스트를 받았다.)
     */
    private double normalizedPopularity(ArticleScore score) {
        if (score == null) return 0.0;
        return Math.max(0.0, (score.getScore() - SCORE_BASELINE) / SCORE_BASELINE);
    }

    private double calculateRelevance(Article article, List<List<Double>> keywordEmbeddings) {
        List<ArticleEmbedding> embList =
                embeddingRepository.findByArticle_ArticleId(article.getArticleId());

        if (embList.isEmpty()) return 0.0;

        try {
            List<Double> articleEmb =
                    objectMapper.readValue(
                            embList.get(0).getEmbeddingJson(),
                            new TypeReference<>() {}
                    );

            double total = 0;
            for (List<Double> kwEmb : keywordEmbeddings) {
                total += cosine(articleEmb, kwEmb);
            }
            return total / keywordEmbeddings.size();

        } catch (Exception e) {
            // 과거: 차원 불일치 예외를 여기서 조용히 삼켜 모든 추천 점수가 0이 되는
            //       silent failure가 발생했다(응답은 200, 추천은 무력화).
            // 이제는 로그로 노출해 같은 실패가 다시 숨지 않도록 한다.
            log.warn("관련도 계산 실패 articleId={}: {}", article.getArticleId(), e.toString());
            return 0.0;
        }
    }

    private double cosine(List<Double> a, List<Double> b) {
        // 차원 불일치는 조용히 0을 만드는 대신 명시적으로 드러낸다 (silent failure 재발 방지).
        if (a.size() != b.size()) {
            throw new IllegalArgumentException(
                    "임베딩 차원 불일치: " + a.size() + " vs " + b.size());
        }
        double dot = 0, magA = 0, magB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            magA += a.get(i) * a.get(i);
            magB += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(magA) * Math.sqrt(magB));
    }
}

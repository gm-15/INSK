package com.insk.insk_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insk.insk_backend.client.EmbeddingClient;
import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleEmbedding;
import com.insk.insk_backend.domain.ArticleScore;
import com.insk.insk_backend.domain.Keyword;
import com.insk.insk_backend.repository.ArticleEmbeddingRepository;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.ArticleScoreRepository;
import com.insk.insk_backend.repository.ArticleFeedbackRepository;
import com.insk.insk_backend.repository.KeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleScoreService {

    private final ArticleRepository articleRepository;
    private final ArticleFeedbackRepository feedbackRepository;
    private final ArticleScoreRepository scoreRepository;
    private final ArticleEmbeddingRepository embeddingRepository;
    private final KeywordRepository keywordRepository;
    private final EmbeddingClient embeddingClient;

    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional
    public ArticleScore updateScore(Long articleId) {

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("기사 없음"));

        long likeCount = feedbackRepository.countByArticleArticleIdAndLiked(articleId, true);
        long dislikeCount = feedbackRepository.countByArticleArticleIdAndLiked(articleId, false);

        double textScore = calculateTextRelevance(articleId);
        int viewCount = 0;

        double raw =
                (likeCount * 2.0)
                        - (dislikeCount * 1.0)
                        + textScore
                        + Math.log(viewCount + 1);

        double score = normalize(raw);

        ArticleScore articleScore = scoreRepository.findByArticle_ArticleId(articleId)
                .orElseGet(() -> ArticleScore.builder()
                        .article(article)
                        .build());

        articleScore.updateScoreData(
                score,
                (int) likeCount,
                (int) dislikeCount,
                textScore,
                viewCount
        );

        return scoreRepository.save(articleScore);
    }


    @Transactional(readOnly = true)
    public ArticleScore getScore(Long articleId) {
        return scoreRepository.findByArticle_ArticleId(articleId)
                .orElseGet(() -> {
                    // 점수가 없으면 기본값 반환 (읽기 전용이므로 저장하지 않음)
                    Article article = articleRepository.findById(articleId)
                            .orElseThrow(() -> new IllegalArgumentException("기사 없음"));
                    return ArticleScore.builder()
                            .article(article)
                            .score(0.0)
                            .likeCount(0)
                            .dislikeCount(0)
                            .textScore(0.0)
                            .viewCount(0)
                            .build();
                });
    }

    private double calculateTextRelevance(Long articleId) {

        List<ArticleEmbedding> embList = embeddingRepository.findByArticle_ArticleId(articleId);
        if (embList.isEmpty()) return 0.0;

        List<Double> articleEmb;
        try {
            articleEmb = mapper.readValue(
                    embList.get(0).getEmbeddingJson(),
                    new TypeReference<>() {}
            );
        } catch (Exception e) {
            return 0.0;
        }

        List<Keyword> keywords = keywordRepository.findByApprovedTrue();
        if (keywords.isEmpty()) return 0.0;

        double sum = 0;
        int count = 0;

        for (Keyword k : keywords) {
            List<Double> keyEmb = embeddingClient.embed(k.getKeyword());
            if (keyEmb == null || keyEmb.isEmpty()) continue;

            sum += cosine(articleEmb, keyEmb);
            count++;
        }

        return count == 0 ? 0.0 : (sum / count) * 10.0;
    }

    private double cosine(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        double dot = 0, magA = 0, magB = 0;
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            dot += a.get(i) * b.get(i);
            magA += a.get(i) * a.get(i);
            magB += b.get(i) * b.get(i);
        }
        double denominator = Math.sqrt(magA) * Math.sqrt(magB);
        if (denominator == 0.0) {
            return 0.0;
        }
        return dot / denominator;
    }

    private double normalize(double raw) {
        if (Double.isNaN(raw) || Double.isInfinite(raw)) return 0.0;
        double clamped = Math.max(-100, Math.min(100, raw));
        return Math.max(0, Math.min(100, (clamped + 100) / 2));
    }
}

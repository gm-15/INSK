package com.insk.insk_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleScoreRepository scoreRepository;
    private final ArticleEmbeddingRepository embeddingRepository;
    private final DepartmentInterestService interestService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ArticleDto.SimpleResponse> getTop5(DepartmentType dept) {

        List<String> keywords = interestService.getInterestKeywords(dept);
        if (keywords.isEmpty()) return List.of();

        List<Article> articles = articleRepository.findAll();

        return articles.stream()
                .map(a -> {
                    double relevance = calculateRelevance(a, keywords);
                    ArticleScore score = scoreRepository.findByArticle_ArticleId(a.getArticleId())
                            .orElse(null);

                    double finalScore =
                            (score != null ? score.getScore() : 0.0) * 0.7
                                    + relevance * 30.0;

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

    private double calculateRelevance(Article article, List<String> keywords) {
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
            for (String kw : keywords) {
                List<Double> kwEmb = FakeKeywordEmbedding.make(kw);
                total += cosine(articleEmb, kwEmb);
            }
            return total / keywords.size();

        } catch (Exception e) {
            return 0.0;
        }
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
}

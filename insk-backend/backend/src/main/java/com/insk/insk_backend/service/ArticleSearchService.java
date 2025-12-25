package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleAnalysis;
import com.insk.insk_backend.domain.ArticleScore;
import com.insk.insk_backend.dto.ArticleSearchDto;
import com.insk.insk_backend.repository.ArticleAnalysisRepository;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.ArticleScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleSearchService {

    private final ArticleRepository articleRepository;
    private final ArticleScoreRepository articleScoreRepository;
    private final ArticleAnalysisRepository analysisRepository;

    @Transactional(readOnly = true)
    public ArticleSearchDto.SearchResponse search(String query, int limit) {

        if (query == null) query = "";
        if (limit <= 0) limit = 20;

        String q = query.trim();
        if (q.isEmpty()) {
            return ArticleSearchDto.SearchResponse.builder()
                    .query(query)
                    .articles(List.of())
                    .build();
        }

        List<Article> byTitle =
                articleRepository.findByTitleContainingIgnoreCaseOrderByPublishedAtDesc(q);

        List<ArticleAnalysis> byText =
                analysisRepository.findBySummaryContainingIgnoreCaseOrInsightContainingIgnoreCase(q, q);

        Set<Long> articleIds = new HashSet<>();
        byTitle.forEach(a -> articleIds.add(a.getArticleId()));
        byText.forEach(a -> articleIds.add(a.getArticle().getArticleId()));

        List<Article> merged = articleIds.stream()
                .map(id -> articleRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Article::getPublishedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        List<ArticleSearchDto.ArticleSummary> list = merged.stream()
                .map(a -> {
                    ArticleScore score = articleScoreRepository.findByArticle_ArticleId(a.getArticleId())
                            .orElse(null);

                    return ArticleSearchDto.ArticleSummary.builder()
                            .articleId(a.getArticleId())
                            .title(a.getTitle())
                            .source(a.getSource())
                            .publishedAt(a.getPublishedAt())
                            .score(score != null ? score.getScore() : null)
                            .build();
                })
                .toList();

        return ArticleSearchDto.SearchResponse.builder()
                .query(query)
                .articles(list)
                .build();
    }
}

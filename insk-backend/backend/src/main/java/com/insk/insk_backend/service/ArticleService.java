package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleAnalysis;
import com.insk.insk_backend.domain.ArticleFeedback;
import com.insk.insk_backend.dto.ArticleDto;
import com.insk.insk_backend.repository.ArticleAnalysisRepository;
import com.insk.insk_backend.repository.ArticleFeedbackRepository;
import com.insk.insk_backend.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleAnalysisRepository analysisRepository;
    private final ArticleFeedbackRepository feedbackRepository;

    /**
     * 카테고리와 출처로 기사 필터링 (사용자별)
     */
    public Page<ArticleDto.Response> getArticles(String category, String source, String userEmail, Pageable pageable) {

        // 사용자별 필터링
        List<ArticleAnalysis> filteredAnalyses;
        if (userEmail != null && !userEmail.isBlank()) {
            // 사용자별 필터링
            if (category != null && !category.isEmpty() && source != null && !source.isEmpty()) {
                filteredAnalyses = analysisRepository.findByUser_EmailAndCategoryAndSource(userEmail, category, source);
            } else if (category != null && !category.isEmpty()) {
                filteredAnalyses = analysisRepository.findByUser_EmailAndCategory(userEmail, category);
            } else if (source != null && !source.isEmpty()) {
                filteredAnalyses = analysisRepository.findByUser_EmailAndSource(userEmail, source);
            } else {
                filteredAnalyses = analysisRepository.findByUser_Email(userEmail);
            }
        } else {
            // 사용자 정보가 없으면 전체 조회 (하위 호환성)
            if (category != null && !category.isEmpty() && source != null && !source.isEmpty()) {
                filteredAnalyses = analysisRepository.findByCategoryAndSource(category, source);
            } else if (category != null && !category.isEmpty()) {
                filteredAnalyses = analysisRepository.findByCategory(category);
                // 출처 필터 추가
                if (source != null && !source.isEmpty()) {
                    filteredAnalyses = filteredAnalyses.stream()
                            .filter(aa -> aa.getArticle().getSource().equals(source))
                            .toList();
                }
            } else if (source != null && !source.isEmpty()) {
                // 출처만 필터링
                filteredAnalyses = analysisRepository.findAll().stream()
                        .filter(aa -> aa.getArticle().getSource().equals(source))
                        .toList();
            } else {
                // 전체 조회
                filteredAnalyses = analysisRepository.findAll();
            }
        }

        // Article ID 추출
        List<Long> articleIds = filteredAnalyses.stream()
                .map(aa -> aa.getArticle().getArticleId())
                .distinct()
                .toList();

        if (articleIds.isEmpty()) {
            return org.springframework.data.domain.Page.empty(pageable);
        }

        // 페이지네이션 적용
        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int start = pageNumber * pageSize;
        int end = Math.min(start + pageSize, articleIds.size());
        List<Long> pagedIds = articleIds.subList(start, end);

        // Article 조회
        List<Article> articles = pagedIds.stream()
                .map(id -> articleRepository.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> {
                    if (a.getPublishedAt() == null && b.getPublishedAt() == null) return 0;
                    if (a.getPublishedAt() == null) return 1;
                    if (b.getPublishedAt() == null) return -1;
                    return b.getPublishedAt().compareTo(a.getPublishedAt());
                })
                .toList();

        // ArticleAnalysis 일괄 조회 (N+1 쿼리 문제 해결)
        List<ArticleAnalysis> analyses = analysisRepository.findByArticle_ArticleIdIn(pagedIds);
        Map<Long, ArticleAnalysis> analysisMap = analyses.stream()
                .collect(Collectors.toMap(
                        aa -> aa.getArticle().getArticleId(),
                        aa -> aa
                ));

        // DTO 변환
        List<ArticleDto.Response> content = articles.stream()
                .map(article -> {
                    ArticleAnalysis analysis = analysisMap.get(article.getArticleId());
                    return ArticleDto.Response.builder()
                            .articleId(article.getArticleId())
                            .title(article.getTitle())
                            .originalUrl(article.getOriginalUrl())
                            .summary(analysis != null ? analysis.getSummary() : "")
                            .category(analysis != null ? analysis.getCategory() : "")
                            .source(article.getSource())
                            .country(article.getCountry())
                            .language(article.getLanguage())
                            .publishedAt(article.getPublishedAt())
                            .build();
                })
                .toList();

        return new org.springframework.data.domain.PageImpl<>(
                content,
                pageable,
                articleIds.size()
        );
    }

    public ArticleDto.DetailResponse getArticleById(Long articleId) {

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("기사 없음"));

        ArticleAnalysis analysis = analysisRepository.findByArticle(article).orElse(null);

        return ArticleDto.DetailResponse.builder()
                .articleId(article.getArticleId())
                .title(article.getTitle())
                .originalUrl(article.getOriginalUrl())
                .summary(analysis != null ? analysis.getSummary() : "")
                .insight(analysis != null ? analysis.getInsight() : "")
                .category(analysis != null ? analysis.getCategory() : "")
                .tags(analysis != null ? analysis.getTags() : "[]")
                .publishedAt(article.getPublishedAt())
                .source(article.getSource())
                .country(article.getCountry())
                .language(article.getLanguage())
                .build();
    }

    /**
     * 사용자가 좋아요한 관심기사 조회
     */
    public Page<ArticleDto.Response> getFavoriteArticles(String userEmail, Pageable pageable) {
        // 사용자가 좋아요한 피드백 조회
        List<ArticleFeedback> favoriteFeedbacks = feedbackRepository.findByUser_EmailAndLikedTrue(userEmail);
        
        if (favoriteFeedbacks.isEmpty()) {
            return Page.empty(pageable);
        }

        // Article ID 추출
        List<Long> articleIds = favoriteFeedbacks.stream()
                .map(f -> f.getArticle().getArticleId())
                .distinct()
                .toList();

        // 페이지네이션 적용
        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int start = pageNumber * pageSize;
        int end = Math.min(start + pageSize, articleIds.size());
        List<Long> pagedIds = articleIds.subList(start, end);

        // Article 조회
        List<Article> articles = pagedIds.stream()
                .map(id -> articleRepository.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> {
                    if (a.getPublishedAt() == null && b.getPublishedAt() == null) return 0;
                    if (a.getPublishedAt() == null) return 1;
                    if (b.getPublishedAt() == null) return -1;
                    return b.getPublishedAt().compareTo(a.getPublishedAt());
                })
                .toList();

        // ArticleAnalysis 일괄 조회
        List<ArticleAnalysis> analyses = analysisRepository.findByArticle_ArticleIdIn(pagedIds);
        Map<Long, ArticleAnalysis> analysisMap = analyses.stream()
                .collect(Collectors.toMap(
                        aa -> aa.getArticle().getArticleId(),
                        aa -> aa
                ));

        // DTO 변환
        List<ArticleDto.Response> content = articles.stream()
                .map(article -> {
                    ArticleAnalysis analysis = analysisMap.get(article.getArticleId());
                    return ArticleDto.Response.builder()
                            .articleId(article.getArticleId())
                            .title(article.getTitle())
                            .originalUrl(article.getOriginalUrl())
                            .summary(analysis != null ? analysis.getSummary() : "")
                            .category(analysis != null ? analysis.getCategory() : "")
                            .source(article.getSource())
                            .country(article.getCountry())
                            .language(article.getLanguage())
                            .publishedAt(article.getPublishedAt())
                            .build();
                })
                .toList();

        return new PageImpl<>(content, pageable, articleIds.size());
    }
}

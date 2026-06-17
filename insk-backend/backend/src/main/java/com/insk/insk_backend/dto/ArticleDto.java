package com.insk.insk_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleAnalysis;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ArticleDto {

    // ===========================
    // 📌 뉴스 목록 조회 Response
    // ===========================
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response implements Serializable {

        private Long articleId;
        private String title;
        private String originalUrl;
        private String summary;
        private String category;

        private String source;   // 🔥 필드 추가
        private String country;  // 🔥 필드 추가
        private String language; // 🔥 필드 추가

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime publishedAt;

        public static Response from(Article article, ArticleAnalysis analysis) {

            return Response.builder()
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
        }
    }

    // ===========================
    // 📌 뉴스 상세 Response
    // ===========================
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetailResponse implements Serializable {

        private Long articleId;
        private String title;
        private String originalUrl;
        private String summary;
        private String insight;
        private String category;
        private String tags;   // 🔥 tagsJson → tags 로 통일

        private String source;
        private String country;
        private String language;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime publishedAt;

        public static DetailResponse from(Article article, ArticleAnalysis analysis) {
            return DetailResponse.builder()
                    .articleId(article.getArticleId())
                    .title(article.getTitle())
                    .originalUrl(article.getOriginalUrl())
                    .summary(analysis != null ? analysis.getSummary() : "")
                    .insight(analysis != null ? analysis.getInsight() : "")
                    .category(analysis != null ? analysis.getCategory() : "")
                    .tags(analysis != null ? analysis.getTags() : "[]") // 🔥 수정됨
                    .source(article.getSource())
                    .country(article.getCountry())
                    .language(article.getLanguage())
                    .publishedAt(article.getPublishedAt())
                    .build();
        }
    }

        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SimpleResponse implements Serializable {
            private Long articleId;
            private String title;
            private String url;
            private double score;
        }
}

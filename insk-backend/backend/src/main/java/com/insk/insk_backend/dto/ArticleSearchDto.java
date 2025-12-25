package com.insk.insk_backend.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class ArticleSearchDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArticleSummary {
        private Long articleId;
        private String title;
        private String source;
        private LocalDateTime publishedAt;
        private Double score;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchResponse {
        private String query;
        private List<ArticleSummary> articles;
    }
}

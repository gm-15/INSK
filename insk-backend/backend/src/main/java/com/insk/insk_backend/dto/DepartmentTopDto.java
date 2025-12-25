// DepartmentTopDto.java
package com.insk.insk_backend.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class DepartmentTopDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopArticle {
        private Long articleId;
        private String title;
        private String source;
        private LocalDateTime publishedAt;
        private Double score;
        private Long viewCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopListResponse {
        private String department;
        private int days;
        private List<TopArticle> articles;
    }
}

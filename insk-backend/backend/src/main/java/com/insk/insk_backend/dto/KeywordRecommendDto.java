package com.insk.insk_backend.dto;

import com.insk.insk_backend.domain.DepartmentType;
import lombok.*;

import java.util.List;

public class KeywordRecommendDto {

    @Getter
    @Setter
    public static class RecommendRequest {
        private DepartmentType department;
        private Integer limit = 10;
    }

    @Getter
    @Setter
    public static class ApproveRequest {
        private String keyword;
        private String category; // LLM, Telco, INFRA, AI Ecosystem
    }

    @Getter
    @Setter
    public static class RejectRequest {
        private String keyword;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Candidate {
        private String keyword;
        private String category;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendResponse {
        private List<Candidate> recommended;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LlmResult {
        private List<Candidate> recommended;
    }
}

package com.insk.insk_backend.dto;

import com.insk.insk_backend.domain.DepartmentKeywordRule;
import com.insk.insk_backend.domain.DepartmentType;
import lombok.*;

public class DepartmentKeywordRuleDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RuleResponse {
        private Long ruleId;
        private DepartmentType department;
        private String category;
        private String keyword;
        private int priority;
        private boolean active;

        public static RuleResponse of(DepartmentKeywordRule r) {
            return RuleResponse.builder()
                    .ruleId(r.getId())       // ★ 핵심 수정 반영
                    .department(r.getDepartment())
                    .category(r.getCategory())
                    .keyword(r.getKeyword())
                    .priority(r.getPriority())
                    .active(r.isActive())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private DepartmentType department;
        private String category;
        private String keyword;
        private int priority;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String category;
        private String keyword;
        private int priority;
        private boolean active;
    }
}

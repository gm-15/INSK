package com.insk.insk_backend.dto;

import lombok.*;

public class KeywordDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        private Long userId;     // 키워드 등록자 (선택)
        private String keyword;  // 키워드 본문
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long keywordId;
        private String keyword;
        private boolean approved;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class OtherUsersKeywordResponse {
        private String keyword;
        private boolean approved;
        private int count; // 중복된 키워드의 개수
    }
}

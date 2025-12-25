// ArticleFeedbackDto 수정본

package com.insk.insk_backend.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class ArticleFeedbackDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {
        private Boolean liked;       // true/false/null
        private String feedbackText; // 선택
    }

    @Getter
    @Builder
    public static class Response {
        private Long feedbackId;
        private Long articleId;
        private Boolean liked;
        private String feedbackText;
        private String userEmail;
        private String department;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class SummaryResponse {
        private Long articleId;
        private long likes;
        private long dislikes;
        private List<String> recentComments;
        private MyFeedback myFeedback;

        @Getter
        @Builder
        public static class MyFeedback {
            private Boolean liked;
            private String text;
        }
    }
}

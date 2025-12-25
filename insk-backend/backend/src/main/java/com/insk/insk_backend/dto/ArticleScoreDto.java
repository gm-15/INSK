package com.insk.insk_backend.dto;

import com.insk.insk_backend.domain.ArticleScore;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ArticleScoreDto {

    @Getter
    @Builder
    public static class Response {
        private Long articleId;
        private double score;
        private int likeCount;
        private int dislikeCount;
        private double textRelevanceScore;
        private int viewCount;

        public static Response from(ArticleScore s) {
            return Response.builder()
                    .articleId(s.getArticle().getArticleId())
                    .score(s.getScore())                      // score 필드
                    .likeCount(s.getLikeCount())              // likes → getLikeCount()
                    .dislikeCount(s.getDislikeCount())        // dislikes → getDislikeCount()
                    .textRelevanceScore(s.getTextRelevanceScore()) // textScore → getTextRelevanceScore()
                    .viewCount(s.getViewCount())
                    .build();
        }
    }
}

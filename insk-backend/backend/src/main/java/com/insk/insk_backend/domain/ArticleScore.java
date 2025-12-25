package com.insk.insk_backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "article_scores")
public class ArticleScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "dislike_count", nullable = false)
    private int dislikeCount;

    @Column(name = "text_score", nullable = false)
    private double textScore;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    public void updateScoreData(double score, int likeCount, int dislikeCount, double textScore, int viewCount) {
        this.score = score;
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
        this.textScore = textScore;
        this.viewCount = viewCount;
    }

    public int getLikeCount() { return likeCount; }
    public int getDislikeCount() { return dislikeCount; }
    public double getTextRelevanceScore() { return textScore; }
}

package com.insk.insk_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "article_feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ArticleFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기사 FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    // 사용자 FK (익명 피드백 허용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // true = 좋아요, false = 싫어요
    @Column
    private Boolean liked;

    // 텍스트 피드백
    @Column(columnDefinition = "TEXT")
    private String feedbackText;

    // 생성일시
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 수정일시
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 최초 저장 시
    @PrePersist
    void onCreate() {
        if (this.createdAt == null) { // Null-safe (이전 null row 이슈 방지)
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = this.createdAt;
    }

    // 업데이트 시
    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 좋아요/싫어요 업데이트 메서드
    public void updateLikeDislike(Boolean liked) {
        this.liked = liked;
    }
}

package com.insk.insk_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@EntityListeners(AuditingEntityListener.class)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "keywords")
public class Keyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keyword_id", updatable = false)
    private Long id;

    // Keyword(N) : User(1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)

    @Builder.Default
    private boolean approved = false; // 기본값: 미승인
    // 승인 상태 변경용 Setter
    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    @Setter
    @Column(name = "category", length = 50)
    private String category; // 예: LLM, Telco, INFRA, AI Ecosystem

    @Builder
    public Keyword(String keyword, Boolean approved, User user) {
        this.keyword = keyword;
        this.approved = approved;
        this.user = user;
    }
}

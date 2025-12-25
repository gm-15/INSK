package com.insk.insk_backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "article_embeddings")
public class ArticleEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Article 1:1 Embedding
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false, unique = true)
    private Article article;

    @Lob
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String embeddingJson; // JSON 형태로 저장

    @Builder
    public ArticleEmbedding(Article article, String embeddingJson) {
        this.article = article;
        this.embeddingJson = embeddingJson;
    }
}

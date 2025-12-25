package com.insk.insk_backend.repository;

import com.insk.insk_backend.domain.ArticleEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleEmbeddingRepository extends JpaRepository<ArticleEmbedding, Long> {

    // Article 1:N Embedding 구조 → List 반환
    List<ArticleEmbedding> findByArticle_ArticleId(Long articleId);
}

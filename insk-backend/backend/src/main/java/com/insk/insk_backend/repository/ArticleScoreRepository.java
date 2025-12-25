package com.insk.insk_backend.repository;

import com.insk.insk_backend.domain.ArticleScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArticleScoreRepository extends JpaRepository<ArticleScore, Long> {

    Optional<ArticleScore> findByArticle_ArticleId(Long articleId);
}

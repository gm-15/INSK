package com.insk.insk_backend.repository;

import com.insk.insk_backend.domain.ArticleFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleFeedbackRepository extends JpaRepository<ArticleFeedback, Long> {

    long countByArticleArticleIdAndLiked(Long articleId, boolean liked);
    List<ArticleFeedback> findByArticle_ArticleId(Long articleId);
    boolean existsByArticle_ArticleIdAndUser_Id(Long articleId, Long userId);
    
    // 사용자별 기사 피드백 조회 (좋아요/싫어요용)
    List<ArticleFeedback> findByArticle_ArticleIdAndUser_Id(Long articleId, Long userId);
    
    // 사용자별 기사 피드백 조회 (이메일 기반)
    List<ArticleFeedback> findByArticle_ArticleIdAndUser_Email(Long articleId, String email);
    
    // 사용자별 좋아요한 기사 조회
    List<ArticleFeedback> findByUser_EmailAndLikedTrue(String email);

}

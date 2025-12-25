package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleFeedback;
import com.insk.insk_backend.domain.User;
import com.insk.insk_backend.dto.ArticleFeedbackDto;
import com.insk.insk_backend.repository.ArticleFeedbackRepository;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ArticleFeedbackService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final ArticleFeedbackRepository feedbackRepository;
    private final ArticleScoreService articleScoreService;

    @Transactional
    public ArticleFeedbackDto.Response createFeedback(
            Long articleId,
            ArticleFeedbackDto.CreateRequest req,
            String userEmail
    ) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("기사 없음"));

        User user = null;
        if (userEmail != null) {
            user = userRepository.findByEmail(userEmail).orElse(null);
        }

        // 좋아요/싫어요인 경우 기존 피드백 확인 및 처리
        if (req.getLiked() != null && user != null) {
            List<ArticleFeedback> existingFeedbacks = 
                    feedbackRepository.findByArticle_ArticleIdAndUser_Email(articleId, userEmail);
            
            // 같은 기사에 대한 기존 좋아요/싫어요 피드백 찾기
            ArticleFeedback existingLikeDislike = existingFeedbacks.stream()
                    .filter(f -> f.getLiked() != null)
                    .findFirst()
                    .orElse(null);
            
            if (existingLikeDislike != null) {
                // 같은 버튼을 다시 누른 경우: 취소 (삭제)
                if (existingLikeDislike.getLiked().equals(req.getLiked())) {
                    feedbackRepository.delete(existingLikeDislike);
                    articleScoreService.updateScore(articleId);
                    
                    // 삭제 후 빈 응답 반환 (취소됨을 의미)
                    return ArticleFeedbackDto.Response.builder()
                            .feedbackId(null)
                            .articleId(article.getArticleId())
                            .liked(null)
                            .feedbackText(null)
                            .userEmail(user.getEmail())
                            .department(user.getDepartment() != null ? user.getDepartment().name() : null)
                            .createdAt(null)
                            .build();
                } else {
                    // 다른 버튼을 누른 경우: 기존 피드백 업데이트
                    existingLikeDislike.updateLikeDislike(req.getLiked());
                    ArticleFeedback saved = feedbackRepository.save(existingLikeDislike);
                    articleScoreService.updateScore(articleId);
                    
                    return ArticleFeedbackDto.Response.builder()
                            .feedbackId(saved.getId())
                            .articleId(article.getArticleId())
                            .liked(saved.getLiked())
                            .feedbackText(saved.getFeedbackText())
                            .userEmail(user.getEmail())
                            .department(user.getDepartment() != null ? user.getDepartment().name() : null)
                            .createdAt(saved.getCreatedAt())
                            .build();
                }
            }
        }

        // 새로운 피드백 생성
        ArticleFeedback feedback = ArticleFeedback.builder()
                .article(article)
                .user(user)
                .liked(req.getLiked())
                .feedbackText(req.getFeedbackText())
                .build();

        ArticleFeedback saved = feedbackRepository.save(feedback);

        articleScoreService.updateScore(articleId);

        return ArticleFeedbackDto.Response.builder()
                .feedbackId(saved.getId())
                .articleId(article.getArticleId())
                .liked(saved.getLiked())
                .feedbackText(saved.getFeedbackText())
                .userEmail(user != null ? user.getEmail() : null)
                .department(user != null && user.getDepartment() != null
                        ? user.getDepartment().name()
                        : null)
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ArticleFeedbackDto.Response> getFeedbacks(Long articleId) {

        return feedbackRepository.findByArticle_ArticleId(articleId).stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(f -> ArticleFeedbackDto.Response.builder()
                        .feedbackId(f.getId())
                        .articleId(f.getArticle().getArticleId())
                        .liked(f.getLiked())
                        .feedbackText(f.getFeedbackText())
                        .userEmail(f.getUser() != null ? f.getUser().getEmail() : null)
                        .department(f.getUser() != null && f.getUser().getDepartment() != null
                                ? f.getUser().getDepartment().name()
                                : null)
                        .createdAt(f.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public ArticleFeedbackDto.SummaryResponse getFeedbackSummary(Long articleId, String userEmail) {

        long likes = feedbackRepository.countByArticleArticleIdAndLiked(articleId, true);
        long dislikes = feedbackRepository.countByArticleArticleIdAndLiked(articleId, false);

        List<ArticleFeedback> all = feedbackRepository.findByArticle_ArticleId(articleId);

        List<String> recentComments = all.stream()
                .filter(f -> f.getFeedbackText() != null && !f.getFeedbackText().isBlank())
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .limit(5)
                .map(ArticleFeedback::getFeedbackText)
                .toList();

        ArticleFeedback mine = null;
        if (userEmail != null) {
            // 좋아요/싫어요 피드백만 찾기 (텍스트 피드백 제외)
            mine = all.stream()
                    .filter(f -> f.getUser() != null
                            && userEmail.equals(f.getUser().getEmail())
                            && f.getLiked() != null) // 좋아요/싫어요만
                    .findFirst()
                    .orElse(null);
        }

        ArticleFeedbackDto.SummaryResponse.MyFeedback myFeedback = null;
        if (mine != null) {
            myFeedback = ArticleFeedbackDto.SummaryResponse.MyFeedback.builder()
                    .liked(mine.getLiked())
                    .text(mine.getFeedbackText())
                    .build();
        }

        return ArticleFeedbackDto.SummaryResponse.builder()
                .articleId(articleId)
                .likes(likes)
                .dislikes(dislikes)
                .recentComments(recentComments)
                .myFeedback(myFeedback)
                .build();
    }
}

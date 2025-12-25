package com.insk.insk_backend.controller;

import com.insk.insk_backend.dto.ArticleFeedbackDto;
import com.insk.insk_backend.service.ArticleFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/articles/{articleId}/feedbacks")
@RequiredArgsConstructor
public class ArticleFeedbackController {

    private final ArticleFeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<ArticleFeedbackDto.Response> createFeedback(
            @PathVariable Long articleId,
            @RequestBody ArticleFeedbackDto.CreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String email = (userDetails != null ? userDetails.getUsername() : null);
        ArticleFeedbackDto.Response resp =
                feedbackService.createFeedback(articleId, request, email);
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    public ResponseEntity<List<ArticleFeedbackDto.Response>> getFeedbacks(
            @PathVariable Long articleId
    ) {
        return ResponseEntity.ok(feedbackService.getFeedbacks(articleId));
    }

    @GetMapping("/summary")
    public ResponseEntity<ArticleFeedbackDto.SummaryResponse> getFeedbackSummary(
            @PathVariable Long articleId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String email = (userDetails != null ? userDetails.getUsername() : null);
        return ResponseEntity.ok(
                feedbackService.getFeedbackSummary(articleId, email)
        );
    }
}
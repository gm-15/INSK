package com.insk.insk_backend.controller;

import com.insk.insk_backend.dto.KeywordRecommendDto;
import com.insk.insk_backend.service.KeywordRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/keywords")
public class KeywordRecommendController {

    private final KeywordRecommendService keywordRecommendService;

    @PostMapping("/recommend")
    public ResponseEntity<KeywordRecommendDto.RecommendResponse> recommend(
            @RequestBody KeywordRecommendDto.RecommendRequest request
    ) {
        return ResponseEntity.ok(keywordRecommendService.recommend(request));
    }

    @PostMapping("/approve")
    public ResponseEntity<Void> approve(
            @RequestBody KeywordRecommendDto.ApproveRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String userEmail = (userDetails != null ? userDetails.getUsername() : null);
        keywordRecommendService.approve(request, userEmail);
        return ResponseEntity.ok().build();
    }
}

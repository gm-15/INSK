package com.insk.insk_backend.controller;

import com.insk.insk_backend.domain.Keyword;
import com.insk.insk_backend.dto.KeywordDto;
import com.insk.insk_backend.service.KeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/keywords")
@RequiredArgsConstructor
public class KeywordController {

    private final KeywordService keywordService;

    /**
     * 🔹 키워드 생성
     */
    @PostMapping
    public ResponseEntity<KeywordDto.Response> createKeyword(
            @RequestBody KeywordDto.CreateRequest req,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String userEmail = (userDetails != null ? userDetails.getUsername() : null);
        Keyword saved = keywordService.createKeyword(req, userEmail);

        return ResponseEntity.ok(
                KeywordDto.Response.builder()
                        .keywordId(saved.getId())
                        .keyword(saved.getKeyword())
                        .approved(saved.isApproved())
                        .build()
        );
    }

    /**
     * 🔹 승인된 키워드 조회 (사용자별)
     */
    @GetMapping("/approved")
    public ResponseEntity<List<KeywordDto.Response>> getApprovedKeywords(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String userEmail = (userDetails != null ? userDetails.getUsername() : null);
        
        List<KeywordDto.Response> responses = keywordService.getApprovedKeywords(userEmail)
                .stream()
                .map(k -> KeywordDto.Response.builder()
                        .keywordId(k.getId())
                        .keyword(k.getKeyword())
                        .approved(k.isApproved())
                        .build()
                )
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * 🔹 전체 키워드 조회
     */
    @GetMapping
    public ResponseEntity<List<KeywordDto.Response>> getAllKeywords() {

        List<KeywordDto.Response> responses = keywordService.getAllKeywords()
                .stream()
                .map(k -> KeywordDto.Response.builder()
                        .keywordId(k.getId())
                        .keyword(k.getKeyword())
                        .approved(k.isApproved())
                        .build()
                )
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * 🔹 다른 사용자가 추가한 키워드 조회 (중복 제거 및 카운트 포함)
     */
    @GetMapping("/others")
    public ResponseEntity<List<KeywordDto.OtherUsersKeywordResponse>> getOtherUsersKeywords(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String userEmail = (userDetails != null ? userDetails.getUsername() : null);
        
        List<KeywordDto.OtherUsersKeywordResponse> responses = keywordService.getOtherUsersKeywords(userEmail);

        return ResponseEntity.ok(responses);
    }
}

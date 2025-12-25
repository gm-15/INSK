package com.insk.insk_backend.controller;

import com.insk.insk_backend.domain.Keyword;
import com.insk.insk_backend.service.KeywordManageService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/keywords")
public class KeywordManageController {

    private final KeywordManageService keywordManageService;

    // ⚠ 기존 "/approved" → "/manage/approved" 로 변경 (충돌 방지)
    @GetMapping("/manage/approved")
    public ResponseEntity<List<Keyword>> getApprovedKeywords() {
        return ResponseEntity.ok(keywordManageService.getApprovedKeywords());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKeyword(@PathVariable Long id) {
        keywordManageService.deleteKeyword(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reject")
    public ResponseEntity<Void> rejectKeyword(@RequestBody RejectRequest request) {
        keywordManageService.rejectKeyword(request.getKeyword());
        return ResponseEntity.ok().build();
    }

    @Getter
    @Setter
    public static class RejectRequest {
        private String keyword;
    }
}

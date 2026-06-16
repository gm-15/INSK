package com.insk.insk_backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase C 회귀 테스트 — 점수 재계산 POST는 인증 필수 (멘토 피드백 #6).
 *
 * <p>updateScore는 승인 키워드 수만큼 OpenAI 임베딩을 호출하는 비용 큰 변경 작업이라,
 * 미인증 호출이 비용 증폭 DoS가 되지 않도록 인증을 강제한다. 공개 조회(GET)는 그대로 열어 둔다.
 * (과거 permitAll 매처는 실제 경로 '/score/update'와 어긋난 dead config였음 → 제거 후 본 테스트로 고정.)
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScoreEndpointSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("미인증 POST /api/v1/articles/{id}/score/update → 거부(4xx)")
    void unauthenticatedArticleScoreUpdate_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/articles/1/score/update"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("미인증 POST /api/v1/scores/{id}/update → 거부(4xx)")
    void unauthenticatedScoresUpdate_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/scores/1/update"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("공개 조회 GET /api/v1/articles 는 인증 없이 허용(200)")
    void publicArticleList_allowed() throws Exception {
        mockMvc.perform(get("/api/v1/articles"))
                .andExpect(status().isOk());
    }
}

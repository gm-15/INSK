package com.insk.insk_backend.service;

import com.insk.insk_backend.client.KeywordAiClient;
import com.insk.insk_backend.domain.ArticleAnalysis;
import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.domain.Keyword;
import com.insk.insk_backend.domain.User;
import com.insk.insk_backend.dto.KeywordRecommendDto;
import com.insk.insk_backend.repository.ArticleAnalysisRepository;
import com.insk.insk_backend.repository.KeywordRepository;
import com.insk.insk_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordRecommendService {

    private final ArticleAnalysisRepository analysisRepository;
    private final KeywordRepository keywordRepository;
    private final KeywordAiClient keywordAiClient;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "keywordRecommendations", key = "#req.department != null ? #req.department.name() + ':' + (#req.limit != null ? #req.limit : 10) : 'ALL:' + (#req.limit != null ? #req.limit : 10)")
    public KeywordRecommendDto.RecommendResponse recommend(KeywordRecommendDto.RecommendRequest req) {
        try {
            DepartmentType dept = req.getDepartment();
            int limit = (req.getLimit() == null || req.getLimit() <= 0) ? 10 : req.getLimit();

            LocalDateTime from = LocalDateTime.now().minusDays(7);

            List<ArticleAnalysis> analyses =
                    analysisRepository.findByCreatedAtAfterOrderByCreatedAtDesc(from);

            if (analyses.isEmpty()) {
                return KeywordRecommendDto.RecommendResponse.builder()
                        .recommended(List.of())
                        .build();
            }

            String newsText = buildNewsContext(analyses, 40);

            String systemPrompt = """
                    너는 대기업 임직원용 AI 뉴스 트렌드 센서 INS-K의 '키워드 추천 엔진'이다.
                    입력으로 최근 뉴스의 제목, 요약, 인사이트가 주어진다.

                    너의 역할:
                    1. 최근 트렌드를 잘 설명하는 핵심 키워드를 뽑는다.
                    2. 각 키워드를 다음 4개 카테고리 중 하나로 분류한다.
                       - "LLM"
                       - "Telco"
                       - "INFRA"
                       - "AI Ecosystem"
                    3. JSON 형식으로만 답변해야 한다.
                       {
                         "recommended": [
                           { "keyword": "...", "category": "LLM" },
                           { "keyword": "...", "category": "INFRA" }
                         ]
                       }
                    4. keyword는 2~5 단어 이내의 영어 또는 한글로 작성한다.
                    """;

            String userPrompt = buildUserPrompt(dept, newsText, limit);

            List<KeywordRecommendDto.Candidate> rawCandidates =
                    keywordAiClient.callLlm(systemPrompt, userPrompt);

            if (rawCandidates == null || rawCandidates.isEmpty()) {
                return KeywordRecommendDto.RecommendResponse.builder()
                        .recommended(List.of())
                        .build();
            }

            Set<String> seen = new HashSet<>();
            List<KeywordRecommendDto.Candidate> filtered = rawCandidates.stream()
                    .filter(c -> c.getKeyword() != null && !c.getKeyword().isBlank())
                    .filter(c -> seen.add(c.getKeyword().trim().toLowerCase()))
                    .filter(c -> !keywordRepository.existsByKeywordIgnoreCase(c.getKeyword().trim()))
                    .limit(limit)
                    .collect(Collectors.toList());

            return KeywordRecommendDto.RecommendResponse.builder()
                    .recommended(filtered)
                    .build();
        } catch (Exception e) {
            log.error("키워드 추천 실패", e);
            return KeywordRecommendDto.RecommendResponse.builder()
                    .recommended(List.of())
                    .build();
        }
    }

    @Transactional
    public void approve(KeywordRecommendDto.ApproveRequest req, String userEmail) {
        String kw = Optional.ofNullable(req.getKeyword())
                .map(String::trim)
                .orElseThrow(() -> new IllegalArgumentException("keyword는 필수입니다."));

        if (kw.isBlank()) {
            throw new IllegalArgumentException("keyword는 비어 있을 수 없습니다.");
        }

        // 사용자 정보 조회
        com.insk.insk_backend.domain.User user = null;
        if (userEmail != null && !userEmail.isBlank()) {
            user = userRepository.findByEmail(userEmail).orElse(null);
        }

        // 현재 사용자가 이미 같은 키워드를 등록했는지 확인
        if (user != null) {
            boolean existsByUser = keywordRepository.findByUser_EmailAndApprovedTrue(userEmail)
                    .stream()
                    .anyMatch(k -> k.getKeyword().equalsIgnoreCase(kw));
            if (existsByUser) {
                // 이미 등록된 키워드이므로 그냥 반환 (중복 추가 방지)
                return;
            }
        } else {
            // 사용자 정보가 없으면 전체 키워드에서 중복 체크
            if (keywordRepository.existsByKeywordIgnoreCase(kw)) {
                return;
            }
        }

        Keyword entity = Keyword.builder()
                .keyword(kw)
                .approved(true)
                .user(user) // 현재 사용자에게 키워드 추가
                .build();

        entity.setCategory(req.getCategory());

        keywordRepository.save(entity);
    }

    private String buildNewsContext(List<ArticleAnalysis> analyses, int maxCount) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (ArticleAnalysis a : analyses) {
            if (count >= maxCount) break;
            sb.append("- Title: ").append(a.getArticle().getTitle()).append("\n");
            sb.append("  Summary: ").append(a.getSummary()).append("\n");
            sb.append("  Insight: ").append(a.getInsight()).append("\n\n");
            count++;
        }
        return sb.toString();
    }

    private String buildUserPrompt(DepartmentType dept, String newsText, int limit) {
        String deptText = (dept == null)
                ? "부서 정보 없음"
                : "사용자의 부서는 " + dept.name() + " 이다. 이 부서에 특히 중요할 만한 키워드를 우선적으로 추천하라.";

        return """
                아래는 최근 1주일간의 뉴스 분석 결과이다.

                %s

                %s

                위 내용을 기반으로, 중복되지 않게 최대 %d개의 새로운 키워드를 추천하라.
                각 키워드는 반드시 위에서 지정한 4개 카테고리 중 하나에 속해야 한다.
                JSON 외의 텍스트는 절대 포함하지 마라.
                """.formatted(newsText, deptText, limit);
    }
}

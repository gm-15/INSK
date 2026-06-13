package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.dto.ArticleDto;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 라이브 분포 검증 (수동 실행용) — 실제 DB + 실제 OpenAI 임베딩 사용.
 *
 * <p>실행: {@code ./gradlew test --tests "*DepartmentRecommendationLiveCheckTest"}
 * <p>목적: FakeEmbedding 수정 후 부서별 추천 점수가 0이 아닌 정상 분포인지 실데이터로 확인.
 * <p>주의: 실제 OpenAI 임베딩 호출(부서당 키워드 수만큼)이 발생한다. CI 자동 실행 대상 아님.
 */
@Disabled("수동 라이브 검증용 — 실제 DB + 실제 OpenAI 호출. 검증이 필요할 때만 주석 해제 후 실행")
@SpringBootTest
class DepartmentRecommendationLiveCheckTest {

    @Autowired
    DepartmentArticleService service;

    @Test
    void printDepartmentTop5Distribution() {
        System.out.println("\n##### 부서별 추천 Top5 라이브 분포 (수정 후) #####");
        int deptsWithResults = 0;
        int deptsWithNonZero = 0;

        for (DepartmentType dept : DepartmentType.values()) {
            List<ArticleDto.SimpleResponse> top5 = service.getTop5(dept);
            long nonZero = top5.stream().filter(r -> r.getScore() != 0.0).count();
            if (!top5.isEmpty()) deptsWithResults++;
            if (nonZero > 0) deptsWithNonZero++;

            System.out.printf("=== %-18s | 결과 %d건, 점수≠0: %d ===%n", dept, top5.size(), nonZero);
            for (ArticleDto.SimpleResponse r : top5) {
                String title = r.getTitle() == null ? "" : r.getTitle();
                if (title.length() > 45) title = title.substring(0, 45);
                System.out.printf("    score=%8.4f  %s%n", r.getScore(), title);
            }
        }

        System.out.printf("%n##### 요약: 추천 반환 부서 %d/10, 점수≠0 부서 %d/10 #####%n",
                deptsWithResults, deptsWithNonZero);
    }
}

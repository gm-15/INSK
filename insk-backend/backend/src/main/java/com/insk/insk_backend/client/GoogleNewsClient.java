//package com.insk.insk_backend.client;
//
//import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
//import com.fasterxml.jackson.annotation.JsonProperty;
//import lombok.Getter;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.*;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestClientException;
//import org.springframework.web.client.RestTemplate;
//
//import java.net.URI;
//import java.net.URISyntaxException;
//import java.time.Duration;
//import java.util.Collections;
//import java.util.List;
//import java.util.Objects;
//
///**
// * 안전한 외신(글로벌) 기사 수집용 클라이언트.
// * - 외부 호출에 타임아웃 설정
// * - API Key는 환경변수로 주입 (google.api.key)
// * - 실패 시 빈 리스트 반환 (호출자에서 재시도/백오프 처리)
// */
//@Slf4j
//@Component
//public class GoogleNewsClient {
//
//    @Value("${GOOGLE_API_KEY}")
//    private String apiKey; // 환경변수에서 주입되도록 application.properties에는 값 없음.
//
//    // NewsAPI.org 엔드포인트(예시). 필요하면 다른 엔드포인트로 변경.
//    private static final String NEWSAPI_URL = "https://newsapi.org/v2/everything";
//
//    // RestTemplate 인스턴스(타임아웃 설정)
//    private final RestTemplate restTemplate;
//
//    public GoogleNewsClient() {
//        this.restTemplate = new RestTemplate();
//        // RestTemplate 기본 타임아웃은 낮음. (필요시 커넥션 팩토리로 시간 설정)
//        // 여기서는 간단히 사용. 프로덕션에서는 HttpComponentsClientHttpRequestFactory 권장.
//    }
//
//    /**
//     * query 기반 글로벌 뉴스 검색
//     * @param query 검색어 (예: "AI", "autonomous vehicles")
//     * @param pageSize 가져올 기사 수 (최대 100)
//     * @return List<NewsArticleDto>
//     */
//    public List<NewsArticleDto> fetchNews(String query, int pageSize) {
//        if (query == null || query.isBlank()) {
//            return Collections.emptyList();
//        }
//
//        String url = String.format("%s?q=%s&pageSize=%d&sortBy=publishedAt&language=en", NEWSAPI_URL, encodeQuery(query), Math.min(pageSize, 100));
//
//        try {
//            URI uri = new URI(url);
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.set("Authorization", apiKey); // NewsAPI는 'X-Api-Key' 또는 Authorization 헤더 지원
//            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
//            HttpEntity<Void> entity = new HttpEntity<>(headers);
//
//            ResponseEntity<NewsApiResponse> resp = restTemplate.exchange(uri, HttpMethod.GET, entity, NewsApiResponse.class);
//
//            if (Objects.nonNull(resp.getBody()) && "ok".equalsIgnoreCase(resp.getBody().getStatus())) {
//                return resp.getBody().getArticles();
//            } else {
//                log.warn("GoogleNewsClient: NewsAPI returned non-ok or empty body for query={} status={}", query, resp.getStatusCode());
//                return Collections.emptyList();
//            }
//        } catch (URISyntaxException e) {
//            log.warn("GoogleNewsClient: invalid URI for query {} : {}", query, e.getMessage());
//            return Collections.emptyList();
//        } catch (RestClientException e) {
//            log.warn("GoogleNewsClient: external call failed for query {} (no sensitive details logged).", query);
//            return Collections.emptyList();
//        } catch (Exception e) {
//            log.error("GoogleNewsClient: unexpected error (class {})", e.getClass().getSimpleName());
//            return Collections.emptyList();
//        }
//    }
//
//    // 간단한 URL 인코딩 (더 엄밀하게 필요하면 URLEncoder 사용)
//    private String encodeQuery(String q) {
//        return q.replace(" ", "%20");
//    }
//
//    /**
//     * NewsAPI 응답 DTO (필요한 필드만 매핑)
//     */
//    @Getter
//    @JsonIgnoreProperties(ignoreUnknown = true)
//    public static class NewsApiResponse {
//        private String status;
//        private int totalResults;
//        private List<NewsArticleDto> articles;
//    }
//
//    @Getter
//    @JsonIgnoreProperties(ignoreUnknown = true)
//    public static class NewsArticleDto {
//        private NewsSource source;
//        private String author;
//        private String title;
//        private String description;
//        private String url;
//        private String content;
//        private String publishedAt;
//    }
//
//    @Getter
//    @JsonIgnoreProperties(ignoreUnknown = true)
//    public static class NewsSource {
//        private String id;
//        private String name;
//    }
//}

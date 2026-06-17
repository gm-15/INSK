package com.insk.insk_backend.client;

import com.insk.insk_backend.dto.NaverNewsDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class NaverNewsClient {

    @Value("${naver.api.client-id}")
    private String clientId;

    @Value("${naver.api.client-secret}")
    private String clientSecret;

    private final String API_URL = "https://openapi.naver.com/v1/search/news.json";
    private final RestTemplate restTemplate;   // 멘토 #5: 타임아웃 설정된 외부 API 전용 RestTemplate 주입

    public NaverNewsClient(RestTemplate externalApiRestTemplate) {
        this.restTemplate = externalApiRestTemplate;
    }

    /**
     * Naver News API를 호출하여 뉴스 목록을 가져옵니다.
     */
    public List<NaverNewsDto> searchNews(String query, int display) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", clientId);
        headers.set("X-Naver-Client-Secret", clientSecret);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // URL 인코딩 처리
        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String url = API_URL + "?query=" + encodedQuery + "&display=" + display + "&sort=sim";

        try {
            log.info("🔍 Naver News API 호출: query={}, display={}", query, display);
            ResponseEntity<NaverNewsDto.SearchResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, NaverNewsDto.SearchResponse.class
            );
            
            if (response.getBody() == null) {
                log.warn("⚠️ Naver News API 응답이 null입니다.");
                return Collections.emptyList();
            }
            
            List<NaverNewsDto> items = response.getBody().getItems();
            log.info("✅ Naver News API 성공: {}개 기사 조회됨", items != null ? items.size() : 0);
            return items != null ? items : Collections.emptyList();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("❌ Naver News API HTTP 에러: status={}, message={}", e.getStatusCode(), e.getMessage());
            if (e.getStatusCode().value() == 401) {
                log.error("⚠️ 네이버 API 인증 실패. API 키를 확인해주세요.");
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("❌ Naver News API 호출 실패: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * [ ⭐️ 'scrapeArticleBody'의 실제 구현 ⭐️ ]
     * Jsoup을 사용하여 기사 원문의 본문을 스크래핑합니다.
     */
    public String scrapeArticleBody(String url) {
        try {
            // User-Agent와 기타 헤더를 설정하여 403 에러 방지
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .referrer("https://www.google.com")
                    .timeout(10000) // 10초 타임아웃
                    .followRedirects(true)
                    .get();
            
            // 다양한 언론사의 본문 셀렉터 시도
            String body = doc.select("article, #articleBody, #article_body, #newsct_article, .article-body, .article_content, .article-content, .content-body").text();
            
            // 본문이 비어있으면 전체 텍스트에서 일부 추출 시도
            if (body == null || body.trim().isEmpty()) {
                body = doc.body().text();
            }
            
            return body;
        } catch (org.jsoup.HttpStatusException e) {
            log.warn("Jsoup 스크래핑 실패 (URL: {}): HTTP error fetching URL. Status={}, URL=[{}]", url, e.getStatusCode(), url);
            return null;
        } catch (Exception e) {
            log.warn("Jsoup 스크래핑 실패 (URL: {}): {}", url, e.getMessage());
            return null;
        }
    }
}
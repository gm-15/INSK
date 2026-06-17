package com.insk.insk_backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 멘토 피드백 #1: 임베딩 유사도 검색을 MySQL JSON brute-force가 아니라
 * VectorDB(Qdrant)의 HNSW 인덱스로 처리한다(=ANN, Approximate Nearest Neighbor).
 *
 * <p>Qdrant REST API로 컬렉션 보장 / 벡터 upsert / KNN(Top-K) 검색을 수행한다.
 * 메타데이터(기사 본문 등)는 MySQL에 두고, 벡터만 Qdrant에 둔다.
 */
@Slf4j
@Component
public class QdrantClient {

    private final String baseUrl;
    private final String collection;
    private final RestTemplate rest;
    private final ObjectMapper om = new ObjectMapper();

    public QdrantClient(
            @Value("${qdrant.host:localhost}") String host,
            @Value("${qdrant.port:6333}") int port,
            @Value("${qdrant.collection:articles}") String collection) {
        this.baseUrl = "http://" + host + ":" + port;
        this.collection = collection;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(3));
        f.setReadTimeout(Duration.ofSeconds(10));
        this.rest = new RestTemplate(f);
    }

    /** 컬렉션이 없으면 생성(벡터 차원 + Cosine 거리). 멱등. */
    public void ensureCollection(int vectorSize) {
        try {
            ResponseEntity<String> exists = rest.getForEntity(
                    baseUrl + "/collections/" + collection + "/exists", String.class);
            if (om.readTree(exists.getBody()).path("result").path("exists").asBoolean(false)) {
                return;
            }
        } catch (Exception ignore) {
            // 조회 실패 시 미존재로 간주하고 생성 시도
        }
        try {
            Map<String, Object> body = Map.of(
                    "vectors", Map.of("size", vectorSize, "distance", "Cosine"));
            rest.exchange(baseUrl + "/collections/" + collection,
                    HttpMethod.PUT, jsonEntity(body), String.class);
            log.info("Qdrant 컬렉션 생성: {} (dim={}, Cosine)", collection, vectorSize);
        } catch (Exception e) {
            log.warn("Qdrant 컬렉션 생성 실패(이미 존재 가능): {}", e.getMessage());
        }
    }

    /** 기사 벡터 upsert (point id = articleId). */
    public void upsert(long articleId, List<Double> vector) {
        try {
            Map<String, Object> body = Map.of(
                    "points", List.of(Map.of("id", articleId, "vector", vector)));
            rest.exchange(baseUrl + "/collections/" + collection + "/points?wait=true",
                    HttpMethod.PUT, jsonEntity(body), String.class);
        } catch (Exception e) {
            log.warn("Qdrant upsert 실패 articleId={}: {}", articleId, e.getMessage());
        }
    }

    /** KNN 검색 → (articleId, score) 상위 limit개. 실패 시 빈 리스트. */
    public List<ScoredId> search(List<Double> vector, int limit) {
        try {
            Map<String, Object> body = Map.of(
                    "vector", vector, "limit", limit, "with_payload", false);
            ResponseEntity<String> resp = rest.postForEntity(
                    baseUrl + "/collections/" + collection + "/points/search",
                    jsonEntity(body), String.class);
            List<ScoredId> out = new ArrayList<>();
            for (JsonNode n : om.readTree(resp.getBody()).path("result")) {
                out.add(new ScoredId(n.path("id").asLong(), n.path("score").asDouble()));
            }
            return out;
        } catch (Exception e) {
            log.warn("Qdrant 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private HttpEntity<String> jsonEntity(Object body) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(om.writeValueAsString(body), h);
    }

    /** Qdrant 검색 결과 한 건: 기사 id와 유사도 점수. */
    public record ScoredId(long id, double score) {}
}

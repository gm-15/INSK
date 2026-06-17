package com.insk.insk_backend.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insk.insk_backend.client.QdrantClient;
import com.insk.insk_backend.repository.ArticleEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 멘토 피드백 #1: 기동 시 Qdrant 컬렉션을 보장하고, MySQL에 이미 저장된 임베딩(JSON)을
 * Qdrant에 백필한다. 멱등(같은 articleId upsert)이라 재기동에도 안전하다.
 *
 * <p>Qdrant가 꺼져 있어도 {@link QdrantClient}가 예외를 삼키므로 앱 기동을 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorIndexInitializer implements ApplicationRunner {

    private static final int EMBEDDING_DIM = 1536; // text-embedding-3-small

    private final QdrantClient qdrantClient;
    private final ArticleEmbeddingRepository embeddingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(ApplicationArguments args) {
        qdrantClient.ensureCollection(EMBEDDING_DIM);

        List<Object[]> rows = embeddingRepository.findAllForIndexing();
        int indexed = 0;
        for (Object[] row : rows) {
            try {
                Long articleId = (Long) row[0];
                String json = (String) row[1];
                if (articleId == null || json == null) continue;
                List<Double> vector = objectMapper.readValue(json, new TypeReference<List<Double>>() {});
                if (vector.size() == EMBEDDING_DIM) {
                    qdrantClient.upsert(articleId, vector);
                    indexed++;
                }
            } catch (Exception e) {
                log.warn("벡터 백필 실패: {}", e.getMessage());
            }
        }
        log.info("🧭 Qdrant 벡터 백필 완료: {}/{}건", indexed, rows.size());
    }
}

package com.insk.insk_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/**
 * 멘토 피드백 #9: 분산 캐시(Redis) 설정.
 *
 * <p>부서 Top-5 추천처럼 요청마다 임베딩 호출 + 전 기사 cosine을 반복하는 비싼 read의 결과를
 * 캐싱해 반복 계산을 제거한다. JVM 로컬 캐시(ConcurrentMapCacheManager)와 달리 다중 인스턴스 간
 * 공유되고 재시작에도 보존된다. Boot RedisCacheManager 자동설정이 이 빈을 기본 설정으로 사용한다.
 *
 * <p>값 직렬화는 RedisCacheConfiguration 기본(JDK)을 사용한다. 캐시 값은 우리가 양끝을 모두
 * 제어하는 신뢰 데이터라 JDK 직렬화로 충분하며, 캐시 대상 DTO는 Serializable을 구현한다.
 */
@Configuration
public class RedisConfig {

    @Value("${cache.ttl-minutes:10}")
    private long ttlMinutes;

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(ttlMinutes))
                .disableCachingNullValues();
    }
}

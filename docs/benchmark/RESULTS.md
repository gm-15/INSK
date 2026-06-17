# Redis 캐시 벤치마크 — 부서 Top-5 추천 (멘토 피드백 #9)

> 목표: 비싼 read 엔드포인트에 분산 캐시(Redis)를 적용하고, 효과를 JMeter로 **재현 가능하게** 측정한다.
> 이 문서의 수치는 아래 `.jmx`와 `.jtl` 원본으로 재현·검증할 수 있다.

## 대상 엔드포인트
`GET /api/v1/articles/top5/{department}` → `DepartmentArticleService.getTop5(dept)`

캐시 미스 1회가 하는 일:
- 부서 키워드(4개)를 OpenAI `text-embedding-3-small`로 임베딩 (외부 API 호출)
- 전체 기사 로드 후 기사마다 임베딩 DB 조회 + 1536차원 cosine 계산

`@Cacheable(cacheNames = "departmentTop5", key = "#dept.name()")`로 결과를 Redis에 캐싱(TTL 10분).

## 환경
- App: Spring Boot 3.5.6 / Java 21, 로컬 실행 (`bootRun`)
- DB: MySQL 8 로컬 (`insk_db`, 누적 기사 약 320건)
- Cache: Redis 7 (Docker `redis:7-alpine`)
- Load: Apache JMeter (Docker `justb4/jmeter`), 테스트 플랜 [`department-top5.jmx`](department-top5.jmx)
- 대상 부서: `T_AI_SERVICE`, 측정일 2026-06-17

## 방법
| 구분 | 설정 | 부하 | 원본 |
|---|---|---|---|
| Baseline (캐시 OFF) | `spring.cache.type=none` (매 요청 풀 계산) | 1 thread × 10 | [`results-miss.jtl`](results-miss.jtl) |
| Redis 캐시 (HIT) | 기본(Redis), 1회 워밍 후 | 30 threads × 100 = 3000 | [`results-hit.jtl`](results-hit.jtl) |

> Baseline을 저부하(1 thread × 10)로 둔 이유: 미스 1회가 OpenAI 임베딩을 호출하므로, 고부하 반복은 비용·rate limit을 유발한다. 미스의 **응답시간**을 보기엔 순차 측정이 가장 깔끔하다.

## 결과

| 지표 | 캐시 OFF (miss) | Redis 캐시 (hit) |
|---|---:|---:|
| 요청 수 | 10 | 3,000 |
| 에러 | 0 | 0 |
| **평균** | **3,950 ms** | **21.5 ms** |
| p50 | 3,828 ms | 19 ms |
| p90 | 4,384 ms | 34 ms |
| p95 | 4,384 ms | 40 ms |
| p99 | 4,384 ms | 60 ms |
| min / max | 3,685 / 4,384 ms | 6 / 103 ms |
| throughput | 0.3 req/s (1 thread) | 658 req/s (30 threads) |

**평균 응답시간 3,950 ms → 21.5 ms (약 184배 단축), p95 4,384 ms → 40 ms.**

## 정직한 한계
- Baseline n=10 (의도적 저부하). 미스 응답시간은 **외부 임베딩 호출 + N+1 기사 임베딩 조회**가 지배하며, 캐시는 이를 통째로 제거한다.
- throughput은 서로 다른 동시성(1 vs 30 thread)에서 측정 → **응답시간이 동일 조건 비교**이고, throughput은 "캐시가 동시성 확장을 열어준다"는 방향성 지표로만 본다(통제된 최대 처리량 측정 아님).
- 미스 경로의 N+1 조회 자체도 개선 여지가 있으나, 본 작업 범위는 캐시 적용·측정이다.

## 재현
```bash
# 1) Redis 기동
docker run -d --name insk-redis -p 6379:6379 redis:7-alpine

# 2) 캐시 HIT 측정 (앱 기본 기동 후 1회 워밍)
docker run --rm -v "$PWD:/test" justb4/jmeter -n -t /test/department-top5.jmx \
  -l /test/results-hit.jtl -Jhost=host.docker.internal -Jthreads=30 -Jloops=100

# 3) Baseline 측정 (앱을 --spring.cache.type=none 으로 기동 후)
docker run --rm -v "$PWD:/test" justb4/jmeter -n -t /test/department-top5.jmx \
  -l /test/results-miss.jtl -Jhost=host.docker.internal -Jthreads=1 -Jloops=10
```

# 기술 선택 의사결정 기록 (ADR-lite)

> v4 핵심 작업에서 "대안을 검토하고, 우리 프로젝트 맥락에 맞는 이유로 선택했다"를 기록한다.
> 각 항목: 상황 / 대안 / 선택 / 우리 프로젝트에 맞는 이유 / 한계. 측정 수치는 [docs/benchmark/](benchmark/) 참조.

---

## 1. 외부 LLM 재시도·폴백 — Spring Retry (vs Resilience4j)

- **상황**: OpenAI 호출이 일시적으로 실패(타임아웃·rate limit·5xx)하면 기사가 유실됐다. 재시도와 폴백이 필요.
- **대안 검토**
  - 직접 `try-catch` + for 루프: 백오프·jitter 정책이 비즈니스 로직에 섞이고 재사용 어려움.
  - **Spring Retry**: `@Retryable`/`@Recover` 선언형. 재시도에 집중. Spring Boot 기본 친화.
  - Resilience4j: 서킷브레이커·rate limiter·bulkhead·메트릭까지 갖춘 종합 툴킷.
- **선택**: **Spring Retry**.
- **우리 프로젝트에 맞는 이유**: 우리에게 필요한 건 "지수 백오프 재시도 + 폴백 모델" 딱 그만큼이었다. 서킷브레이커·rate limiter가 필요한 고트래픽 마이크로서비스가 아니라 **하루 한 번 도는 배치**다. 레퍼런스도 "단순·경량 재시도엔 Spring Retry, 고급 복원력엔 Resilience4j"로 권한다. 의존성·러닝커브를 늘리지 않고 80% 케이스를 덮는 선택.
- **한계**: 서킷브레이커가 없어 장애가 지속되면 매번 재시도 비용을 치른다(그래서 DLQ의 `retry_count`/`DEAD`로 영구 실패를 격리해 보완 → 결정 2). 모든 예외를 일괄 재시도하므로 비일시적 오류(400)도 재시도하는 낭비 여지. 또한 Spring Retry는 **Spring Framework 7에서 코어로 흡수돼 유지보수 모드** — 우리 Spring Boot 3.5.6(Framework 6.x)에선 여전히 표준 방식이다.
- **참고**: [Resilience4j 공식 저장소](https://github.com/resilience4j/resilience4j) — 6개 모듈(CircuitBreaker·RateLimiter·Bulkhead·Retry·TimeLimiter·Cache)을 가진 종합 fault-tolerance 툴킷 · [Spring Retry 공식 저장소](https://github.com/spring-projects/spring-retry) — 재시도 전용(`@Retryable`/`@Recover`/`@Backoff`)

## 2. DLQ(분석 실패 보존) — DB status 컬럼 (vs Kafka 메시지 큐)

- **상황**: 재시도·폴백까지 실패한 기사를 버리지 않고 보존·재처리해야 한다.
- **대안 검토**
  - **DB status 컬럼**: `analysis_status=ANALYSIS_FAILED` + `retry_count`. 같은 DB에 한 컬럼만 추가.
  - Kafka/메시지 큐 dead-letter 토픽: 이벤트 기반·고처리량에 강함.
- **선택**: **DB status 컬럼** (`ANALYSIS_FAILED` → 한도 초과 시 `DEAD`).
- **우리 프로젝트에 맞는 이유**: DLQ는 **패턴**이고 카프카 전용이 아니다(카프카도 native DLQ가 없어 별도 토픽으로 직접 구현). 우리는 **저처리량 배치 + 단일 MySQL** 환경이라, 레퍼런스가 권하는 "임계·저처리량 실패는 DB 테이블로 보존(ACID·관계형 조회·기존 백업/보안 재사용)"에 정확히 해당한다. `status` + `retry_count` + 한도 초과 격리는 레퍼런스의 DLQ 테이블 설계와 동일한 구성.
- **한계**: 큐가 아니라 테이블이므로 "queue"라 부르면 오해(정확히는 dead-letter table). 영구 실패가 누적되면 정리(TTL/아카이브)가 필요. 고처리량·이벤트 기반으로 가면 메시지 큐 재검토.
- **참고**: [SystemDR: The Dead Letter Queue Pattern](https://javatsc.substack.com/p/day-26-the-dead-letter-queue-pattern) — DB 테이블·메시지큐·하이브리드 방식과 status enum(`CREATED→PROCESSING→RETRYING→DEAD_LETTER`) 기반 추적을 다룸

## 3. 분산 캐시 — Redis (vs 로컬 ConcurrentMapCacheManager)

- **상황**: 부서 Top-5 추천이 요청마다 임베딩 호출 + 전 기사 cosine을 반복(미스 ~4s). 결과를 캐싱해야 한다. 기존엔 `@EnableCaching`만 있고 실제로는 JVM 로컬 캐시였다.
- **대안 검토**
  - 로컬 `ConcurrentMapCacheManager`: 가장 빠르고 단순. 단 **인스턴스마다 별도 캐시**.
  - **Redis**: 외부 분산 캐시. 다중 인스턴스 공유 + 재시작 보존.
- **선택**: **Redis** 분산 캐시 (값 직렬화는 JDK — 결정 사유 아래).
- **우리 프로젝트에 맞는 이유**: 멘토가 정확히 지적했듯 "인메모리는 서버별로 캐시가 달라 반쪽짜리"다. 로드밸런서 뒤 인스턴스가 2대면 캐시 불일치가 생긴다. 배포(AWS EB)·확장을 전제하면 **공유 캐시(Redis)** 가 맞다. JMeter 실측 평균 3,950ms→21.5ms(184배, [docs/benchmark](benchmark/)).
  - **직렬화 JDK vs JSON 하위 결정**: `GenericJackson2JsonRedisSerializer` + default typing이 루트 `List`에서 직렬화/역직렬화 비대칭으로 깨졌다. 내부 신뢰 데이터인 캐시값엔 **JDK 직렬화**가 단순·안정이라 채택(대상 DTO는 `Serializable` 구현).
- **한계**: 로컬 캐시보다 네트워크 왕복이 있어 ms 단위 더 느림(미스 대비 무시할 수준). JDK 직렬화는 비가독·버전 호환 주의(내부 캐시라 허용). Redis 장애 시 graceful degradation 미구현.
- **참고**: [NashTech: Redis vs In-Memory Cache, When to Use What](https://blog.nashtechglobal.com/redis-cache-vs-in-memory-cache-when-to-use-what/)

## 4. 트랜잭션 범위 — 외부 호출 분리 + 짧은 트랜잭션 (vs @Transactional 단순 제거)

- **상황**: `runPipelineSync` 전체가 하나의 `@Transactional`이라, 스크래핑·OpenAI(수 초~분) 동안 DB 커넥션을 점유 → Connection Pool Exhaustion 위험.
- **대안 검토**
  - `@Transactional` **단순 제거**: 외부 호출 중 커넥션은 안 잡지만, 기사·임베딩·분석 저장이 각각 별도 트랜잭션이 되어 **기사 단위 원자성**이 깨짐.
  - **별도 빈 + 짧은 트랜잭션**: 외부 호출은 트랜잭션 밖, DB 쓰기 3개만 한 짧은 트랜잭션으로 묶음.
- **선택**: **`ArticlePersistenceService`(별도 빈)로 짧은 트랜잭션 분리.**
- **우리 프로젝트에 맞는 이유**: 레퍼런스가 권하는 "트랜잭션 범위에서 외부 API 호출은 분리하라, 별도 클래스로 빼면 트랜잭션이 잘 작동한다"를 그대로 적용. `@Transactional`은 AOP 프록시라 같은 클래스 self-invocation엔 안 먹으므로 **별도 빈**이라야 새 트랜잭션이 열린다. 외부 호출 중 커넥션을 놓아 풀 고갈을 막으면서도, 저장 3개를 한 트랜잭션으로 묶어 원자성을 지켰다. 제약된 풀(3)·동시 9 측정에서 커넥션 점유 1,544ms→비점유 509ms(3.0배).
- **한계**: 기사 **간** 원자성은 없음(의도 — 한 건 실패가 배치를 롤백하면 안 됨). detached 엔티티 FK 참조로 저장.
- **참고**: [velog: 트랜잭션 범위에서는 필요한 로직만 호출하자](https://velog.io/@glencode/%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%98-%EB%B2%94%EC%9C%84%EC%97%90%EC%84%9C%EB%8A%94-%ED%95%84%EC%9A%94%ED%95%9C-%EB%A1%9C%EC%A7%81%EB%A7%8C-%ED%98%B8%EC%B6%9C%ED%95%98%EC%9E%90) — "외부 API 호출은 트랜잭션 밖으로 분리, 네트워크 지연 시 커넥션 풀 고갈", 프록시 self-invocation 한계까지 짚음

## 5. 병렬 처리 — CompletableFuture + 전용 풀 (vs @Async / 공용 ForkJoinPool)

- **상황**: 기사를 한 건씩 순차 처리. 기사 1건은 대부분 외부 I/O 대기라 병렬화 이득이 크다.
- **대안 검토**
  - `@Async` 메서드 분리: Spring 프록시 기반. self-invocation·반환 조합에 제약.
  - `CompletableFuture` + **공용 `ForkJoinPool.commonPool()`**: 별도 풀 설정 불필요하나, 앱 전체가 공유해 스레드 기아·격리 부재.
  - **`CompletableFuture` + 전용 Executor**: fan-out/allOf로 모으고, 풀을 우리가 통제.
- **선택**: **`CompletableFuture.runAsync(task, pipelineItemExecutor)` + `allOf().join()`**, 전용 풀 분리.
- **우리 프로젝트에 맞는 이유**: 레퍼런스가 경고하듯 commonPool은 I/O 집약 작업에서 스레드 기아를 부른다 → I/O 바운드 전용 풀로 격리. 또 파이프라인이 `@Async("taskExecutor")`에서 도는데 **같은 풀에 per-item 작업을 또 얹고 join하면 nested 풀 고갈(데드락)** → 그래서 `taskExecutor`와 분리한 `pipelineItemExecutor`(max 8)를 따로 뒀고, 풀 크기가 **동시 OpenAI 호출 상한(rate limit 방어)** 역할도 한다. 결과를 변환·체이닝할 게 없는 fan-out/fan-in이라 `thenApply` 같은 콜백은 불필요해 안 썼다. 24건·건당 200ms·pool 8 측정에서 순차 5,041ms→병렬 631ms(8.0배).
- **한계**: 같은 배치 내 제목 dedup이 병렬에선 약해짐(서로 미커밋이라 안 보임 — best-effort heuristic이라 허용). 동시성은 풀 크기(8)가 상한. `join`은 블로킹(목적이 "논블로킹"이 아니라 "병렬 실행"이라 의도적).
- **참고**: [DZone: Be Aware of ForkJoinPool#commonPool()](https://dzone.com/articles/be-aware-of-forkjoinpoolcommonpool) — 공용 풀은 CPU 코어 수에 따라 스레드 수가 달라지고 블로킹 작업엔 부적합 → 전용 풀 격리 근거

# 트랜잭션 분리(#3) · 기사별 병렬화(#4) 측정

> 두 작업은 성능·동시성이 본질이라 수치로 검증한다. 외부 호출(스크래핑·OpenAI)은 비용·변동성이 커서
> **그 대기 시간을 `sleep`으로 모사**해 측정한다. 기사 처리는 외부 I/O 대기가 지배적이므로, 모사 지연이
> 실제 파이프라인이 받는 효과를 그대로 반영한다. 수치는 단위 테스트로 재현·고정된다.

## #4 기사별 병렬화 — 순차 vs 병렬
[PipelineParallelismBenchmarkTest](../../insk-backend/backend/src/test/java/com/insk/insk_backend/service/PipelineParallelismBenchmarkTest.java)

| 조건 | 순차 | 병렬(pool=8) | 효과 |
|---|---:|---:|---:|
| 24건 × I/O 200ms/건 | **5,041 ms** | **631 ms** | **8.0배** |

- 기사 처리는 대부분 외부 응답 **대기**(CPU 아님)라, 대기를 겹치는 병렬화 이득이 풀 크기(8)에 수렴한다.
- 전용 풀 `pipelineItemExecutor`(max 8)에서 `CompletableFuture.runAsync` 후 `allOf().join()`.

## #3 트랜잭션 분리 — 외부 I/O 동안 커넥션 점유 vs 비점유
[ConnectionHoldBenchmarkTest](../../insk-backend/backend/src/test/java/com/insk/insk_backend/service/ConnectionHoldBenchmarkTest.java)

커넥션 풀을 **3**으로 제약하고 외부 I/O(500ms)를 **9개 동시**에 수행.

| 방식 | 전체 시간 | 효과 |
|---|---:|---:|
| 커넥션 점유(트랜잭션 안에서 I/O) | **1,544 ms** | 풀(3)에 막혀 ~3파로 직렬화 |
| 커넥션 비점유(I/O를 트랜잭션 밖으로) | **509 ms** | 9개 I/O 완전 병렬 |
| | | **3.0배** |

- 옛 구조: `existsByOriginalUrl`로 커넥션을 잡은 뒤 스크랩·OpenAI(수 초) 동안 **계속 점유** → 동시 요청이 풀에 막혀 직렬화(심하면 pool exhaustion 타임아웃).
- 현 구조(#3): 외부 호출을 트랜잭션 밖에서 끝내고 저장만 짧은 트랜잭션 → 커넥션을 I/O 동안 안 잡아 동시성 확보.

## 정직한 한계
- 실제 OpenAI 호출이 아니라 **모사 지연(sleep)** 측정. 절대 시간이 아니라 **순차/병렬·점유/비점유의 구조적 차이**를 보는 것이 목적.
- #4 speedup은 풀 크기 상한(8)에 수렴 — 외부 rate limit도 풀 크기로 함께 제한됨.
- #3은 풀 크기·동시성·I/O 길이에 따라 배수가 달라짐(여기선 pool=3, 9동시, 500ms 기준).

## 재현
```bash
./gradlew test --tests "*PipelineParallelismBenchmarkTest" --tests "*ConnectionHoldBenchmarkTest"
# 출력: [#4 병렬화] ... speedup=8.0배 / [#3 트랜잭션 분리] ... 3.0배
```

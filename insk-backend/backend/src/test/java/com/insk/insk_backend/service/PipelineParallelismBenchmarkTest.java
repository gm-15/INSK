package com.insk.insk_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 멘토 #4 측정 — 기사별 처리 병렬화 효과 (순차 vs 병렬 wall-clock).
 *
 * <p>기사 1건 처리는 대부분 외부 I/O 대기(스크래핑 + OpenAI 응답)다. 그 대기 시간을
 * {@code sleep}으로 모사해, 같은 일을 순차로 돌릴 때와 전용 풀에서 병렬로 돌릴 때의
 * 전체 시간을 비교한다. (실제 OpenAI 호출은 비용·변동성이 커 모사로 측정한다.)
 * 결과는 콘솔과 테스트 리포트(system-out)에 출력된다.
 */
class PipelineParallelismBenchmarkTest {

    private static final int ITEMS = 24;          // 한 배치에서 처리할 기사 수
    private static final long IO_LATENCY_MS = 200; // 기사당 외부 I/O 대기 모사
    private static final int POOL_SIZE = 8;        // pipelineItemExecutor maxPoolSize와 동일

    private void simulateItemWork() {
        try {
            Thread.sleep(IO_LATENCY_MS); // 스크랩 + OpenAI 응답 대기 모사 (CPU 아님, 대기)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("병렬 처리가 순차 대비 (대략 풀 크기 배수)만큼 빠르다")
    void parallel_isMuchFasterThanSequential() throws Exception {
        // 순차
        long seqStart = System.nanoTime();
        for (int i = 0; i < ITEMS; i++) simulateItemWork();
        long seqMs = (System.nanoTime() - seqStart) / 1_000_000;

        // 병렬 (전용 풀)
        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
        long parStart = System.nanoTime();
        CompletableFuture<?>[] futures = IntStream.range(0, ITEMS)
                .mapToObj(i -> CompletableFuture.runAsync(this::simulateItemWork, pool))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
        long parMs = (System.nanoTime() - parStart) / 1_000_000;
        pool.shutdown();

        System.out.printf(
                "[#4 병렬화] ITEMS=%d, IO=%dms/건, pool=%d | 순차=%dms, 병렬=%dms, speedup=%.1f배%n",
                ITEMS, IO_LATENCY_MS, POOL_SIZE, seqMs, parMs, (double) seqMs / parMs);

        // 외부 I/O 대기형이라 병렬이 순차보다 최소 3배 이상 빨라야 한다.
        assertThat(parMs).isLessThan(seqMs / 3);
    }
}

package com.insk.insk_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 멘토 #3 측정 — 외부 I/O 동안 DB 커넥션 점유 vs 비점유 (제약된 풀에서 동시성).
 *
 * <p>커넥션 풀을 3으로 제약하고 외부 I/O(sleep)를 9개 동시에 수행할 때의 전체 시간을 비교한다.
 * <ul>
 *   <li><b>conn-held-in-tx</b>: 트랜잭션 안에서 쿼리(커넥션 획득) → sleep(I/O) → 쿼리.
 *       I/O 동안 커넥션 점유. (옛 파이프라인: existsByOriginalUrl로 커넥션 잡고 스크랩·OpenAI 동안 유지)</li>
 *   <li><b>conn-released</b>: sleep(I/O)을 트랜잭션 밖에서 → 그 다음 짧은 트랜잭션 쿼리.
 *       I/O 동안 커넥션 비점유. (현 파이프라인: #3 트랜잭션 분리)</li>
 * </ul>
 * 커넥션을 I/O 동안 잡으면 풀(3)에 막혀 9개가 직렬화되고, 놓아주면 9개 I/O가 완전 병렬이 된다.
 * 결과는 콘솔과 테스트 리포트(system-out)에 출력된다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.minimum-idle=3",
        "spring.datasource.hikari.connection-timeout=10000"
})
@Import(ConnectionHoldBenchmarkTest.BenchBeans.class)
class ConnectionHoldBenchmarkTest {

    private static final int CONCURRENCY = 9;
    private static final long IO_MS = 500;

    @Autowired ConnHeldInTx connHeldInTx;
    @Autowired ConnReleased connReleased;

    @Test
    @DisplayName("외부 I/O 동안 커넥션을 놓아주면 제약된 풀에서도 직렬화되지 않는다")
    void releasingConnectionDuringIo_avoidsPoolSerialization() throws Exception {
        long heldMs = runConcurrent(() -> connHeldInTx.process(IO_MS));
        long releasedMs = runConcurrent(() -> connReleased.process(IO_MS));

        System.out.printf(
                "[#3 트랜잭션 분리] pool=3, concurrency=%d, IO=%dms | conn-held-in-tx=%dms, conn-released=%dms, %.1f배%n",
                CONCURRENCY, IO_MS, heldMs, releasedMs, (double) heldMs / releasedMs);

        // 커넥션을 I/O 동안 놓아주면(분리) 풀 직렬화를 피해 전체 시간이 더 짧다.
        assertThat(releasedMs).isLessThan(heldMs);
    }

    private long runConcurrent(Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        long start = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENCY; i++) futures.add(pool.submit(task));
        for (Future<?> f : futures) f.get();
        long ms = (System.nanoTime() - start) / 1_000_000;
        pool.shutdown();
        return ms;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @TestConfiguration
    static class BenchBeans {
        @Bean ConnHeldInTx connHeldInTx(JdbcTemplate jdbc) { return new ConnHeldInTx(jdbc); }
        @Bean ShortTxQuery shortTxQuery(JdbcTemplate jdbc) { return new ShortTxQuery(jdbc); }
        @Bean ConnReleased connReleased(ShortTxQuery q) { return new ConnReleased(q); }
    }

    /** 옛 방식: 트랜잭션 안에서 커넥션을 획득한 뒤 외부 I/O 동안 점유. */
    public static class ConnHeldInTx {
        private final JdbcTemplate jdbc;
        public ConnHeldInTx(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Transactional
        public void process(long ioMs) {
            jdbc.queryForObject("SELECT 1", Integer.class); // 커넥션 획득 (existsByOriginalUrl 역할)
            sleep(ioMs);                                    // 외부 I/O — 커넥션을 점유한 채 대기
            jdbc.queryForObject("SELECT 1", Integer.class); // 저장 역할
        }
    }

    /** 현 방식: 외부 I/O는 트랜잭션 밖, 저장만 짧은 트랜잭션(별도 빈이라 프록시 적용). */
    public static class ConnReleased {
        private final ShortTxQuery shortTx;
        public ConnReleased(ShortTxQuery shortTx) { this.shortTx = shortTx; }

        public void process(long ioMs) {
            sleep(ioMs);     // 외부 I/O — 커넥션 비점유
            shortTx.run();   // 짧은 트랜잭션
        }
    }

    public static class ShortTxQuery {
        private final JdbcTemplate jdbc;
        public ShortTxQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Transactional
        public void run() {
            jdbc.queryForObject("SELECT 1", Integer.class);
            jdbc.queryForObject("SELECT 1", Integer.class);
        }
    }
}

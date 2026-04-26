# 멘토 피드백 → V4 변경 대조표

> 피드백 출처: SKT AI Data Engineering팀 장원범님 코드 리뷰 이메일
> 작성일: 2026-03-04
> 목적: 피드백의 어떤 지적이 어떤 코드를 어떻게 바꿨는지 1:1 추적

---

## 피드백 1. 중복 체크 로직의 비효율성 및 메모리 리스크

### 멘토 원문
> "isDuplicate 로직에서 embeddingRepository.findAll()을 호출하여 DB의 모든 임베딩 벡터를 메모리에 로드하고 있습니다. 기사가 늘어날수록 O(N)만큼 메모리가 증가하여 좋은 방식은 아닙니다."

> "단순한 기사 중복 체크를 위해 본문 전체를 임베딩하고 코사인 유사도를 계산하는 것은 LLM API 비용과 CPU 낭비입니다."

> "중복된 기사임에도 불구하고 수집 단계에서 API(Embedding, ChatCompletion)를 호출한 뒤 저장 단계에서 중복을 체크한다면 이미 비용이 발생한 상태입니다."

### V3 문제 코드

**파일**: `NewsPipelineService.java`

```java
// ❌ V3 — 전체 임베딩 로드 (OOM 위험)
private boolean isDuplicate(String body) {
    List<ArticleEmbedding> list = embeddingRepository.findAll(); // 기사 1만 건이면 1만 개 벡터 메모리에 올림
    if (list.isEmpty()) return false;

    List<Double> newEmb = embeddingClient.embed(body); // API 비용 발생 ← 이미 돈 나감
    if (newEmb == null) return false;

    for (ArticleEmbedding emb : list) {               // O(N) 순회
        // ...코사인 유사도 계산...
        if (sim >= DUP_THRESHOLD) return true;
    }
    return false;
}

// ❌ V3 — 중복 체크 전에 LLM 이미 호출됨
private void processNaver(List<NaverNewsDto> list, ...) {
    for (NaverNewsDto item : list) {
        String url = item.getOriginalUrl();
        if (articleRepository.existsByOriginalUrl(url)) continue; // URL 체크만 1차

        String body = naverNewsClient.scrapeArticleBody(url);
        if (body == null || body.isBlank()) continue;
        if (isDuplicate(body)) continue;               // ← 이미 스크래핑 완료 후 체크

        OpenAIDto.AnalysisResponse ar = openAIClient.analyzeArticle(body); // ← isDuplicate 전에 호출되는 경우도 존재
    }
}
```

### V4 변경 내용

**원칙 정립**: LLM API 호출 전에 모든 저비용 필터를 통과시킨다.

**신규 생성**: `DuplicateCheckService.java`

```java
// ✅ V4 — findAll() 금지, 최근 N일 제목만 조회
@Transactional(readOnly = true)
public boolean isDuplicateByTitle(String newTitle) {
    LocalDateTime since = LocalDateTime.now().minusDays(dedupWindowDays); // 전체 아님, 윈도우만
    List<String> recentTitles = articleRepository.findTitlesPublishedAfter(since);

    for (String existingTitle : recentTitles) {
        if (jaccardSimilarity(newTitle, existingTitle) >= jaccardThreshold) return true;
    }
    return false;
}
```

**V4 처리 순서 (비용 체계)**:

```
[1] URL 정확 매칭      → 비용 $0, O(1) 인덱스 조회
[2] 제목 Jaccard 유사도 → 비용 $0, 최근 7일 제목만 메모리에
[3] VectorDB 유사도    → 비용 ~$0, ANN 인덱싱 (Phase 2)
---
[4] GPT-4o 분석       → 비용 ~$0.05/건 ← 여기까지 온 것만 호출
```

**삭제**: `NewsPipelineService.isDuplicate()`, `NewsPipelineService.cosine()` 전량 제거

---

## 피드백 2. 비용 최적화 — 모델 오남용

### 멘토 원문
> "단순 번역(translateKeyword)이나 간단한 요약 작업에 최고가 모델인 gpt-4o를 사용 중입니다. 단순 작업은 gpt-4o-mini로 교체 시 성능 차이 없이 비용을 약 90% 이상 절감할 수 있습니다."

### V3 문제 코드

**파일**: `OpenAIClient.java`

```java
// ❌ V3 — 모든 작업에 gpt-4o 하드코딩
public String translateKeyword(String keywordKo) {
    OpenAIDto.ChatRequest req = new OpenAIDto.ChatRequest(
        "gpt-4o",   // ← 단순 번역에 최고가 모델
        null,
        userPrompt,
        false
    );
    // ...
}

public OpenAIDto.AnalysisResponse analyzeArticle(String articleBody) {
    OpenAIDto.ChatRequest requestBody = new OpenAIDto.ChatRequest(
        "gpt-4o",   // ← 분석은 맞는 선택이지만 코드에 직접 박혀있음
        SYSTEM_PROMPT,
        articleBody,
        true
    );
    // ...
}
```

**파일**: `KeywordAiClient.java` (추정 — 동일 패턴)

### V4 변경 내용

**`application.properties`에 모델 외부화**:

```properties
# 분석용 (고비용, 고품질 필요)
openai.model.analysis=gpt-4o
openai.model.analysis-fallback=gpt-4o-mini

# 단순 작업용 (번역, 분류 등)
openai.model.simple=gpt-4o-mini

# 임베딩
openai.model.embedding=text-embedding-3-small
```

**`OpenAIClient.java` 변경**:
```java
// ✅ V4 — 메서드 시그니처에 model 파라미터 추가
public OpenAIDto.AnalysisResponse analyzeArticle(String articleBody, String model) {
    // model을 외부에서 주입받음
}

// translateKeyword → gpt-4o-mini 사용 (90% 비용 절감)
```

**`LlmCallService.java`에서 모델 결정 책임 집중**:
```java
@Value("${openai.model.analysis}")
private String analysisModel;   // gpt-4o

@Value("${openai.model.simple}")
private String simpleModel;     // gpt-4o-mini
```

---

## 피드백 3. 잘못된 트랜잭션 범위 (Connection Pool Exhaustion)

### 멘토 원문
> "runPipelineSync 전체가 하나의 @Transactional로 묶여 있습니다. 네이버 API나 OpenAI 응답이 지연되면, 그 시간 동안 DB 커넥션을 점유하게 되어 Connection Pool Exhaustion로 이어져 회원가입이나 DB조회 같은 기능이 마비가 될 수 있습니다."

### V3 문제 코드

**파일**: `NewsPipelineService.java`

```java
// ❌ V3 — 하나의 @Transactional이 외부 API 호출을 모두 감쌈
@Async("taskExecutor")
@Transactional                                           // ← 커넥션 점유 시작
public void runPipelineAsync(String userEmail) {
    runPipelineSync(userEmail);
}

@Transactional                                           // ← 중복 선언
public void runPipelineSync(String userEmail) {
    for (Keyword k : keywords) {
        naverNewsClient.searchNews(k.getKeyword(), 10);  // 외부 HTTP 수십 초 대기
        openAIClient.analyzeArticle(body);               // LLM 30초+ 대기 가능
        embeddingClient.embed(body);                     // 또 LLM
        // ↑ 이 모든 시간 동안 DB 커넥션 1개 점유 중
        // → 커넥션 풀이 5개면 동시 파이프라인 5개째에 전체 서비스 마비
    }
}                                                        // ← 커넥션 반환
```

### V4 변경 내용

**원칙 정립**: `@Transactional`은 DB 쓰기 직전에만, 최대한 짧게.

**`NewsPipelineService.java`**:
```java
// ✅ V4 — @Transactional 완전 제거
@Async("taskExecutor")
// @Transactional 없음
public void runPipelineAsync(String userEmail) {
    runPipelineSync(userEmail);
}

// @Transactional 없음
public void runPipelineSync(String userEmail) {
    List<Keyword> keywords = fetchKeywords(userEmail);  // 별도 readOnly 트랜잭션으로 분리

    for (Keyword k : keywords) {
        processItem(...);  // 각 기사마다 독립적으로 처리
    }
}

private void processItem(...) {
    // [1] 중복 체크 (readOnly 트랜잭션)
    // [2] 스크래핑 (트랜잭션 없음)
    Article saved = articleSaveService.saveArticleCollected(...); // [3] 짧은 트랜잭션
    // [4] LLM 호출 (트랜잭션 없음 — 실패해도 커넥션 안전)
    try {
        OpenAIDto.AnalysisResponse ar = openAIClient.analyzeArticle(body);
        articleSaveService.saveAnalysis(saved, ar, ...);  // [5] 짧은 트랜잭션
        articleSaveService.saveEmbedding(saved, emb);     // [6] 짧은 트랜잭션
    } catch (Exception e) {
        log.error("LLM 실패, 기사 보존됨: {}", saved.getArticleId());
        // DB 커넥션 안 잡고 있었으므로 롤백할 것도 없음
    }
}
```

**신규 생성**: `ArticleSaveService.java`
- `@Transactional` 메서드만 모아놓는 전용 클래스
- 각 메서드가 독립 트랜잭션으로 동작

---

## 피드백 4. 동기식 순차 처리 — 병렬화 부재

### 멘토 원문
> "키워드별로 루프를 돌며 순차 처리하는 방식은 확장성이 없습니다. 키워드가 늘어나면 수집 시간이 기하급수적으로 늘어납니다. 병렬 처리를 해야합니다."

### V3 문제 코드

**파일**: `NewsPipelineService.java`

```java
// ❌ V3 — 키워드 10개면 순차 10회, 키워드 50개면 순차 50회
for (Keyword k : keywords) {
    List<NaverNewsDto> list = naverNewsClient.searchNews(k.getKeyword(), 10); // 동기
    processNaver(list, k, userEmail);  // 동기
    // 총 소요시간 = 키워드 수 × (API 응답시간 + LLM 응답시간)
}
```

### V4 변경 내용

**`NewsPipelineService.java`** (STEP 9):

```java
// ✅ V4 — CompletableFuture로 키워드 병렬 처리
List<CompletableFuture<Void>> futures = keywords.stream()
    .map(k -> CompletableFuture.runAsync(
        () -> processKeyword(k, userEmail), taskExecutor))
    .collect(Collectors.toList());

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
// 키워드 10개 → 동시 실행 → 전체 시간 ≈ 가장 느린 키워드 1개의 시간
```

**`AsyncConfig.java`** 스레드풀 확장:

```java
// ❌ V3
executor.setCorePoolSize(2);
executor.setMaxPoolSize(5);

// ✅ V4
executor.setCorePoolSize(5);
executor.setMaxPoolSize(10);
```

---

## 피드백 5. 재시도(Retry) 로직 부재

### 멘토 원문
> "외부 API(네이버, OpenAI) 호출 실패에 대한 재시도 로직이 없어 일시적 네트워크 오류에도 데이터가 유실됩니다."

> "Caching → retry → fallback 순으로 접근하는데, 특정 모델을 반드시 써야한다면 API call 중간에 sleep을 exponential로 늘리면서 retry를 5번 정도 진행합니다."

### V3 문제 코드

**파일**: `OpenAIClient.java`

```java
// ❌ V3 — 실패하면 null 반환하고 끝. 재시도 없음
public OpenAIDto.AnalysisResponse analyzeArticle(String articleBody) {
    try {
        // API 호출
        return objectMapper.readValue(jsonContent, OpenAIDto.AnalysisResponse.class);
    } catch (Exception e) {
        log.error("OpenAI API 분석 실패: {}", e.getMessage());
        return null;  // ← 조용히 실패, 재시도 없음
    }
}
```

### V4 변경 내용

**`build.gradle`** 의존성 추가:
```groovy
implementation 'org.springframework.retry:spring-retry'
implementation 'org.springframework:spring-aspects'
```

**신규 생성**: `LlmCallService.java`

```java
// ✅ V4 — Exponential Backoff Retry + Fallback
@Retryable(
    retryFor = {OpenAiApiException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000)
    // 1초 → 2초 → 4초 → 8초 → 16초 → 실패
)
public OpenAIDto.AnalysisResponse analyzeWithRetry(String body) {
    return openAIClient.analyzeArticle(body, analysisModel);
}

@Recover  // 5회 모두 실패 시 fallback 모델로 1회 시도
public OpenAIDto.AnalysisResponse fallbackAnalyze(Exception e, String body) {
    log.warn("Primary 모델 실패, fallback: {}", fallbackModel);
    return openAIClient.analyzeArticle(body, fallbackModel);
}
```

---

## 피드백 6. 보안 취약점 — Score API 권한 제어 미흡

### 멘토 원문
> "기사 점수 부여(POST .../score) API가 permitAll()로 열려 있어 외부인이 데이터를 조작할 수 있습니다."

### V3 문제 코드

**파일**: `SecurityConfig.java`

```java
// ❌ V3 — 인증 없이 누구나 기사 점수 조작 가능
.requestMatchers(HttpMethod.POST, "/api/v1/articles/*/score").permitAll()
```

### V4 변경 내용

**`SecurityConfig.java`**:

```java
// ✅ V4 — 해당 줄 완전 삭제
// anyRequest().authenticated() 가 아래에서 커버함
// POST /api/v1/articles/*/score/update → 인증 필요
```

---

## 피드백 7. 보안 취약점 — CORS 하드코딩

### 멘토 원문
> "localhost:3000만 허용되어 있어, 실제 운영 도메인 배포 시 프론트엔드 접속이 차단됩니다. 환경 변수 기반으로 유연하게 변경되도록 수정해야 합니다."

### V3 문제 코드

**파일**: `SecurityConfig.java`

```java
// ❌ V3 — 운영 배포 시 프론트엔드 차단됨
configuration.setAllowedOrigins(List.of("http://localhost:3000"));
```

### V4 변경 내용

**`SecurityConfig.java`**:
```java
// ✅ V4 — 환경변수로 주입
@Value("${cors.allowed-origins}")
private String allowedOrigins;

configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
```

**`application.properties`**:
```properties
cors.allowed-origins=http://localhost:3000
```

**`application-prod.properties`**:
```properties
cors.allowed-origins=https://insk.example.com
```

---

## 피드백 8. 하드코딩된 설정값 — 운영 중 튜닝 불가

### 멘토 원문
> "유사도 임계치(0.88), 모델명 등이 코드에 박혀 있어 운영 중 튜닝이 불가능합니다. application.yml이나 DB 등으로 설정을 외부화해야 합니다."

### V3 하드코딩 목록

**파일**: `NewsPipelineService.java`
```java
private static final double DUP_THRESHOLD = 0.88;  // 코드에 박힌 임계치
```

**파일**: `OpenAIClient.java`
```java
"gpt-4o"                    // 모델명
"text-embedding-3-small"    // 임베딩 모델명
```

**파일**: `KeywordRecommendService.java`
```java
LocalDateTime.now().minusDays(7)  // 컨텍스트 기간
int limit = 10;                    // 최대 추천 수
int maxCount = 40;                 // 뉴스 컨텍스트 수
```

### V4 변경 내용

**`application.properties`에 전량 이관**:

```properties
# LLM 모델
openai.model.analysis=gpt-4o
openai.model.analysis-fallback=gpt-4o-mini
openai.model.simple=gpt-4o-mini
openai.model.embedding=text-embedding-3-small
openai.timeout-seconds=120

# 파이프라인 튜닝 파라미터
pipeline.dup-threshold=0.90
pipeline.title-jaccard-threshold=0.6
pipeline.dedup-window-days=7
pipeline.naver-results-per-keyword=10
pipeline.max-articles-for-context=40
pipeline.context-days=7
```

**효과**: 코드 배포 없이 운영 중 파라미터 조정 가능.

---

## 피드백 9. Redis — 중복 실행 방지 및 캐시 정합성

### 멘토 원문
> "Redis가 있던데 Redis + RDB를 활용하면 state machine처럼 상태관리를 할 수 있습니다."

> (멀티 인스턴스 관련) "인메모리 캐시는 서버별로 각자 가져 캐시 기능이 반쪽짜리 기능이 됩니다."

### V3 문제 코드

**파일**: `build.gradle`
```groovy
// ❌ V3 — Redis 주석 처리, 비활성화
//implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

**파일**: `CacheConfig.java`
```java
// ❌ V3 — @EnableCaching만 있고 실제 캐시 구현 없음 (기본 인메모리)
@Configuration
@EnableCaching
public class CacheConfig {
    // 내용 없음 → 서버가 2대면 캐시 불일치
}
```

### V4 변경 내용

**`build.gradle`** 주석 해제:
```groovy
// ✅ V4
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

**`CacheConfig.java`** Redis CacheManager 구성:
```java
// ✅ V4 — Redis 기반 분산 캐시 (서버 N대에서도 동일 캐시)
@Bean
public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofHours(24));

    Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
    cacheConfigs.put("articleAnalysis", config.entryTtl(Duration.ofHours(24)));
    cacheConfigs.put("keywordRecommendations", config.entryTtl(Duration.ofHours(1)));

    return RedisCacheManager.builder(connectionFactory)
        .withInitialCacheConfigurations(cacheConfigs)
        .build();
}
```

**`LlmCallService.java`** 프롬프트 캐시:
```java
// 동일 기사 재처리 시 LLM 호출 0회 (Redis에 결과 캐싱)
@Cacheable(cacheNames = "articleAnalysis",
    key = "T(org.springframework.util.DigestUtils).md5DigestAsHex(#body.bytes)")
public OpenAIDto.AnalysisResponse analyzeWithRetry(String body) { ... }
```

---

## 코드 분석으로 추가 발견한 사항 (멘토 피드백 외)

멘토 피드백과 별도로 코드 직접 분석 시 발견한 문제들.

### A. `FakeKeywordEmbedding` — 부서별 Top5 점수 무의미

**파일**: `DepartmentArticleService.java`

```java
// ❌ 발견: hashCode 기반 가짜 256차원 벡터
List<Double> kwEmb = FakeKeywordEmbedding.make(kw);
// 실제 OpenAI 임베딩은 1536차원 → 차원 불일치로 코사인 유사도 의미 없음
```

**V4 변경**: `FakeKeywordEmbedding.java` 파일 삭제 + `embeddingClient.embed(kw)` 사용

### B. 4개 부서 키워드 매핑 누락

**파일**: `DepartmentInterestService.java`

```java
// ❌ 발견: T_HR, T_MARKETING, T_STRATEGY, T_ENTERPRISE_B2B 없음
// → 해당 부서 Top5 조회 시 항상 빈 결과 반환
```

**V4 변경**: 4개 부서 키워드 추가

### C. 점수 범위 문서 불일치

**파일**: `ArticleScoreService.java`

```java
// ❌ 발견: README에는 0~10점이라 되어 있으나 실제 normalize() 는 0~100 반환
private double normalize(double raw) {
    return Math.max(0, Math.min(100, (clamped + 100) / 2)); // 0~100
}
```

**V4 변경**: README 수정 or normalize 함수 스펙 통일

---

## 변경 영향도 요약표

| 멘토 지적 | 심각도 | 변경 파일 | 변경 유형 |
|----------|--------|----------|----------|
| findAll() OOM | 🔴 Critical | `NewsPipelineService.java` | 메서드 제거 |
| API 호출 전 중복 체크 | 🔴 Critical | `DuplicateCheckService.java` | 신규 생성 |
| @Transactional 범위 | 🔴 Critical | `NewsPipelineService.java` | 어노테이션 제거 |
| 모델 하드코딩 | 🟠 Major | `OpenAIClient.java`, `application.properties` | 외부화 |
| Retry 부재 | 🟠 Major | `LlmCallService.java` | 신규 생성 |
| Score API permitAll | 🟠 Major | `SecurityConfig.java` | 1줄 삭제 |
| CORS 하드코딩 | 🟠 Major | `SecurityConfig.java`, `application.properties` | 환경변수화 |
| 임계치 하드코딩 | 🟡 Minor | `application.properties` | 외부화 |
| Redis 비활성화 | 🟡 Minor | `build.gradle`, `CacheConfig.java` | 주석 해제 + 구현 |
| 순차 처리 | 🟡 Minor | `NewsPipelineService.java`, `AsyncConfig.java` | 병렬화 |

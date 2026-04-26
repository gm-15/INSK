# INSK — News Intelligence Platform

> A Spring Boot 3 / Next.js 15 platform that ingests articles from multiple external sources, runs OpenAI-based summarization and embedding, and serves per-department article recommendations.
> **Currently in v4 redesign based on a senior engineer code review.**

🇰🇷 한국어 버전: [README.ko.md](README.ko.md)

---

## ✨ At a Glance

| | v3 (Shipped) | v4 (Designed, Implementation in Progress) |
|---|---|---|
| **Status** | Deployed via AWS Elastic Beanstalk | Migration plan documented in [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md) |
| **LLM cost strategy** | Direct GPT-4o per article | Cost-tiered filter ladder: $0 URL → $0 title Jaccard → ~$0 vector ANN → $0.05 GPT-4o |
| **Transaction scope** | Pipeline-wide `@Transactional` | Per-write minimal transactions; external API calls outside DB connection |
| **Retry & fallback** | None — silent failure | `@Retryable(maxAttempts=5)` + `@Recover` fallback model |
| **Caching** | In-memory `ConcurrentMapCacheManager` | `RedisCacheManager` + prompt caching keyed by article-body MD5 |
| **Concurrency** | Sequential per-keyword loop | `CompletableFuture.runAsync` parallel keyword processing |
| **Configuration** | Hardcoded constants | Externalized to `application.properties` for runtime tuning |

> **Honest note:** Cost figures shown above are *design targets* for v4 (per-call OpenAI pricing × planned filter ratios). Latency and aggregate-cost benchmarks will be added once v4 is implemented and load tested.

---

## 📰 Why This Project Exists (Background)

INSK was built to address a recurring time-sink among current employees at SK affiliates. As of 2024, line-of-business workers reported spending **~1.5 hours per day (7–8 hours per week)** on manual news clipping — collecting, deduplicating, and summarizing industry articles to support strategy planning and partnership negotiations. By 2025, several teams had downgraded the activity to a once-per-week cadence simply because the manual cost crowded out the strategic work the clipping was meant to enable.

INSK automates the ingestion-and-analysis loop and delivers only the high-relevance subset back to each employee's department. The engineering challenge — and the focus of v4's mentor-informed redesign — is keeping the LLM cost per article low enough that the platform's economics work at organizational scale, while still preserving end-to-end reliability so a single LLM API failure does not discard the article entirely. The five operational principles below (cost-tiered filtering, transaction-scope decomposition, retry+fallback, distributed cache, parallel keyword processing) are direct answers to that constraint.

---

## 🏛️ Architecture (v3, Deployed)

```
┌──────────────────────────────────────────────────────────────────┐
│  External Sources                                                │
│  Naver News API · AI Times · The Guru                            │
└──────────────────────────────────────────────────────────────────┘
              │
              ▼  Jsoup scraper · Spring WebFlux client
┌──────────────────────────────────────────────────────────────────┐
│  Spring Boot 3.5.6 (Java 21)                                     │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ NewsPipelineService (@Async)                             │    │
│  │   ingestion → OpenAI analysis → embedding → scoring      │    │
│  └──────────────────────────────────────────────────────────┘    │
│  Spring Security + JWT (1h TTL)                                  │
│  Per-Department Top-5 Recommendation                             │
└──────────────────────────────────────────────────────────────────┘
              │
              ▼
┌──────────────────────────────────────────────────────────────────┐
│  MySQL 8.0                                                       │
│  users · keywords · articles · article_analyses ·                │
│  article_embeddings · article_feedbacks · article_scores         │
└──────────────────────────────────────────────────────────────────┘
              │
              ▼
┌──────────────────────────────────────────────────────────────────┐
│  Next.js 15.5.4 (App Router) · React 19 · Tailwind CSS 4         │
│  /  /articles/[id]  /keywords  /departments  /favorites          │
└──────────────────────────────────────────────────────────────────┘

Deployment:  GitHub Actions  →  AWS ECR  →  Elastic Beanstalk
             (multi-stage Dockerfile · EB Ready-state polling guard)
```

---

## 🔧 Tech Stack

### Backend
- **Spring Boot 3.5.6** · **Java 21** · Gradle
- Spring Data JPA · Hibernate · MySQL 8.0
- Spring Security · jjwt 0.12.x · BCrypt
- Jsoup 1.17.2 · Spring WebFlux
- PDFBox 2.0.30 · iText 7.2.5
- Spring `@Async` (ThreadPoolTaskExecutor)

### Frontend
- **Next.js 15.5.4** (App Router)
- React 19.1 · TypeScript 5
- Tailwind CSS 4 · Axios

### AI / Data
- **OpenAI GPT-4o** (article analysis)
- **text-embedding-3-small** (semantic embedding)
- **gpt-4o-mini** (planned for low-cost branches in v4)

### Infrastructure
- **AWS Elastic Beanstalk** (`ap-northeast-2`)
- **AWS ECR** (multi-stage Docker images)
- **GitHub Actions** (test → build → ECR push → S3 → EB deploy with Ready-state polling)

---

## 🎯 Core Capabilities (v3, Shipped)

### 1. Multi-source News Ingestion
- Naver News API search + body scraping
- AI Times · The Guru direct ingestion
- URL-based deduplication
- 403-prevention headers (User-Agent, Referrer)

### 2. AI Analysis Pipeline
- Korean summary generation (GPT-4o)
- Insight extraction
- Forced JSON-schema category classification: **Telco / LLM / INFRA / AI Ecosystem**
- Tag generation (JSON array)
- HTML tag stripping

### 3. Embedding & Scoring
- Per-article embedding (`text-embedding-3-small`)
- User keyword vs. article cosine similarity
- 0–10 score range, adjusted by feedback weighting

### 4. Per-Department Top-5
- 10 organizational units (T_CLOUD, T_NETWORK_INFRA, T_HR, T_AI_SERVICE, T_MARKETING, T_STRATEGY, T_ENTERPRISE_B2B, T_PLATFORM_DEV, T_TELCO_MNO, T_FINANCE)
- Per-department keyword aggregation → ranked top-5

### 5. Feedback Loop
- Like / dislike toggle
- Text feedback (anonymous-allowed)
- Score auto-recompute on feedback

### 6. Authentication & Security
- JWT (1-hour TTL)
- BCrypt password hashing
- Password-reset token (1-hour, one-time)
- Spring Security endpoint protection

### 7. PDF Export
- Article-detail PDF generation (PDFBox / iText)

---

## 📚 The v4 Story — Why This Project Matters

After v3 deployment, I requested a code review from a senior engineer at **SKT AI Data Engineering team** rather than calling the project finished. The reviewer returned **9 critical and major findings**, each with code-level critique. I documented every finding in a 1:1 mapping with v3 problem code and v4 redesign in [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md).

### v3 → v4 Migration — Headline Items

#### 🔴 Critical · Cost-Tiered Duplicate Filtering
> Mentor: *"For simple article duplication checks, embedding the entire body and computing cosine similarity wastes LLM API cost and CPU."*

**v3 problem.** `embeddingRepository.findAll()` loaded all vectors into memory; LLM cost was incurred *before* duplication detection.

**v4 design.** A strict cost ladder — no LLM call before all cheap filters pass.
```
[1] URL exact match           $0           O(1) index lookup
[2] Title Jaccard similarity  $0           Recent-window (7 days) titles only
[3] Vector ANN (Phase 2)     ~$0           Indexed similarity
─────────────────────────────────────────────────────────────────
[4] GPT-4o analysis          ~$0.05/call   Only items past [1]–[3]
```

#### 🔴 Critical · `@Transactional` Scope Decomposition
> Mentor: *"`runPipelineSync` is wrapped in a single `@Transactional`. While Naver and OpenAI responses delay, the DB connection stays held — leading to Connection Pool Exhaustion that paralyzes other operations like signup."*

**v3 problem.** Pipeline-wide `@Transactional` held a DB connection through tens of seconds of external API waits.

**v4 design.**
- `@Transactional` removed from pipeline orchestration
- New `ArticleSaveService` class concentrates all DB-write methods, each in its own short transaction
- LLM calls happen *outside* any transaction — failure rolls nothing back

#### 🔴 Critical · LLM Calls Before Duplication Checks
> Mentor: *"If you call APIs (Embedding, ChatCompletion) at ingestion and only check duplication at save time, the cost has already been incurred."*

**v4 design.** Duplication is decided strictly before any paid API call. New `DuplicateCheckService.isDuplicateByTitle(...)` runs against a sliding window of recent titles — no `findAll()` ever.

#### 🟠 Major · Retry + Fallback as a First-Class Concern
> Mentor: *"For unavoidable model dependencies, retry with exponential backoff (~5 times). Caching → retry → fallback in that order."*

**v4 design.**
```java
@Retryable(
    retryFor = OpenAiApiException.class,
    maxAttempts = 5,
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000)
)
public AnalysisResponse analyzeWithRetry(String body) { ... }

@Recover
public AnalysisResponse fallbackAnalyze(Exception e, String body) {
    // After 5 failures, fall back to gpt-4o-mini once
}
```

#### 🟠 Major · Model Branching by Task Cost
> Mentor: *"Using gpt-4o for simple translation or summarization. Switching to gpt-4o-mini saves ~90% cost without quality loss."*

**v4 design.** Externalized in `application.properties`:
```properties
openai.model.analysis=gpt-4o
openai.model.analysis-fallback=gpt-4o-mini
openai.model.simple=gpt-4o-mini
openai.model.embedding=text-embedding-3-small
```

#### 🟠 Major · Security — Score API & CORS
- `POST /api/v1/articles/*/score/update` no longer `permitAll()` (was a data-tampering risk in v3)
- CORS allowed-origins moved to env var (`cors.allowed-origins`) for safe production deployment

#### 🟡 Minor · Configuration Externalization
- Similarity threshold (0.88), dedup window (7 days), max context articles (40), Naver result count (10), and timeouts — all moved from hardcoded constants to `application.properties` for runtime tuning without redeployment.

#### 🟡 Minor · Distributed Cache + Prompt Caching
- Activated `spring-boot-starter-data-redis`
- `RedisCacheManager` for distributed cache (replaces single-instance `ConcurrentMapCacheManager`)
- `@Cacheable` keyed by article-body MD5 for prompt-level caching → repeated-article LLM calls drop to 0

#### 🟡 Minor · Parallel Keyword Processing
- v3 sequential `for (Keyword k : keywords)` loop replaced with `CompletableFuture.runAsync`
- Total time approximates the slowest single keyword instead of the sum

### Additional Issues (Self-Found, Beyond Mentor Scope)
- `FakeKeywordEmbedding` (256-dim hashCode-based vector) had a dimension mismatch with real OpenAI 1536-dim embeddings — replaced with real `embeddingClient.embed(keyword)` calls.
- Four departments (T_HR, T_MARKETING, T_STRATEGY, T_ENTERPRISE_B2B) had no keyword mapping → top-5 always empty for them. Fixed.
- Score-range mismatch (README claimed 0–10, code returned 0–100) — reconciled.

---

## 📊 Database Schema (selected)

| Entity | Key Fields |
|---|---|
| `User` | `user_id`, `email` (UNIQUE), `password` (BCrypt), `department` (ENUM), `reset_token` |
| `Article` | `article_id`, `title`, `original_url` (UNIQUE), `body`, `source`, `published_at` |
| `ArticleAnalysis` | `analysis_id`, `article_id` (FK), `user_id` (FK), `summary`, `insight`, `category`, `tags` (JSON) |
| `ArticleEmbedding` | `embedding_id`, `article_id` (FK), `embedding` (JSON vector) |
| `Keyword` | `keyword_id`, `keyword`, `approved`, `category`, `user_id` (FK) |
| `ArticleFeedback` | `id`, `article_id` (FK), `user_id` (FK, nullable), `liked`, `feedback_text` |
| `ArticleScore` | `score_id`, `article_id` (FK, UNIQUE), `score`, `like_count`, `dislike_count`, `view_count` |

---

## 🚦 API Surface (selected)

### Auth
- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `PUT  /api/v1/auth/me/department`

### Pipeline
- `POST /api/v1/articles/run-pipeline` (async)

### Articles
- `GET  /api/v1/articles` (paginated · category & source filters)
- `GET  /api/v1/articles/{id}`
- `GET  /api/v1/articles/{id}/score`
- `POST /api/v1/articles/{id}/score/update` *(auth required from v4)*
- `POST /api/v1/articles/{id}/feedback`
- `GET  /api/v1/articles/{id}/feedback/summary`
- `GET  /api/v1/articles/{id}/pdf`

### Keywords
- `GET    /api/v1/keywords` · `POST` · `DELETE /{keywordId}`
- `GET    /api/v1/keywords/others`
- `POST   /api/v1/keywords/recommend`
- `POST   /api/v1/keywords/approve`

### Departments
- `GET /api/v1/departments/{department}/articles/top5`

---

## 🚧 Status & Roadmap

| Layer | v3 (Now) | v4 (Designed) | v5 (Future) |
|---|---|---|---|
| Pipeline | Sequential per-keyword | `CompletableFuture` parallel | Kafka-backed event-driven |
| LLM cost control | Direct GPT-4o | 4-tier filter ladder | VectorDB ANN (Qdrant) |
| Persistence | MySQL 8 | + Redis CacheManager | + read replicas |
| Resilience | None | `@Retryable` + `@Recover` | + circuit breaker |
| Observability | SLF4J basic | + Actuator + structured logging | + Prometheus + Grafana |
| Auth | JWT 1h | JWT 1h + email-based reset link | + OAuth providers |

---

## 🛠️ Local Development

### Backend
```bash
cd insk-backend/backend
./gradlew bootRun
```

Required configuration (in `application.yml` or environment variables — **never commit secrets**):
- `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`
- `openai.api-key`
- `naver.client-id`, `naver.client-secret`
- `jwt.secret`

### Frontend
```bash
cd insk-frontend
npm install
npm run dev
```

Required env (`.env.local`): `NEXT_PUBLIC_API_BASE_URL`

---

## 📖 Field Notes — From v1 Prototype to v3 Production

The journey from a no-code Make + Streamlit prototype to a Spring Boot 3 / Next.js 15 production deployment is documented in four blog posts on velog.io/@gm-15:

- [Development · Part 1 — Throwing the prototype away and deciding to build a real service](https://velog.io/@gm-15/INSK-%EA%B0%9C%EB%B0%9C%ED%8E%B81%ED%94%84%EB%A1%9C%ED%86%A0%ED%83%80%EC%9E%85%EC%9D%84-%EB%B2%84%EB%A6%AC%EA%B3%A0-%EC%B2%98%EC%9D%8C%EC%9C%BC%EB%A1%9C-%EC%84%9C%EB%B9%84%EC%8A%A4%EB%A5%BC-%EB%A7%8C%EB%93%A4%EA%B2%A0%EB%8B%A4%EA%B3%A0-%EA%B2%B0%EC%A0%95%ED%95%9C-%EC%88%9C%EA%B0%84)
- [Development · Part 2 — From feature-centric to user-centric: the v3 redesign](https://velog.io/@gm-15/INSK-%EA%B0%9C%EB%B0%9C%ED%8E%B8-2)
- [Deployment — What I assumed when shipping](https://velog.io/@gm-15/INSK-%EB%B0%B0%ED%8F%AC%ED%8E%B8)
- [Troubleshooting — Five production issues and what they revealed about my design assumptions](https://velog.io/@gm-15/INSK-%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85%ED%8E%B8)

The troubleshooting post — written before the senior engineer review — already identified that the deepest production issues were not bugs but unverified assumptions baked into the architecture. The v4 redesign is the structural answer to that realization.

---

## 🙏 Acknowledgments

The v4 redesign is grounded in a code review by a senior engineer at **SKT AI Data Engineering team**, who walked through this codebase and pointed out both the critical risks and the engineering principles that should guide a v2 generation of the system. Every v3→v4 mapping is documented in [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md).

---

## 👤 Author

**Park, Gunwoo (gm-15)**
Software Engineering · Sangmyung University
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)
- Email: gunwoo363@gmail.com

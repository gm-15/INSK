# INSK : News Intelligence Platform

> A Spring Boot 3 / Next.js 15 platform that collects articles from 3 external news sources, runs them through OpenAI analysis and embedding, and recommends the most relevant articles for each of SK's 10 departments.
>
> **Backend work that does not stop at shipping a feature, but redesigns it from the angles of operations, data correctness, and scalability.** After deploying v3 on AWS Elastic Beanstalk, I received 9 code-review items from an SKT AI Data senior engineer, and in v4 I **implemented all 9 in code and measured the effect in numbers.**

🎬 [Demo video](https://www.youtube.com/watch?v=WlKGbvbxHik), 🇰🇷 [Korean README](README.md), 🧭 [Technical decisions](docs/TECHNICAL_DECISIONS.md), 📊 [Benchmarks](docs/benchmark/), 📜 [v3 snapshot](archive/README_v3_legacy.ko.md)

> 🎓 **Related side research**: [INSK-trend-forecast](https://github.com/gm-15/INSK-trend-forecast), a time-series course team project that measures Korean AI-news RAG search and recommendation quality with Precision@k and nDCG. That measurement habit carried over into INSK's recommendation validation.

---

## Results at a Glance (v4, all measured, artifacts committed)

![INSK v4 data flow (collect, cost ladder, resilience/storage, recommendation)](docs/img/insk-v4-dataflow.png)

| Work | Before → After | Improvement |
|---|---|---|
| **Redis distributed cache** (#9) | department recommendation 3,950ms → **21.5ms** (p95 4,384 → 40ms) | **~99.5% faster (184x)** |
| **Per-article parallelization** (#4) | collection processing 5,041ms → **631ms** | **~87.5% faster (8.0x)** |
| **ANN (VectorDB) recommendation** (#1) | recommendation recompute 3,950ms → **1,616ms** | **~59% faster (2.4x)** |
| **Transaction scope split** (#3) | constrained pool, 9 concurrent 1,544ms → **509ms** | **~67% faster (3.0x)** |
| **Classification balance recovery** | one-category skew 71% → **55%** / LLM 4% → **20%** | **-16%p / 5x recovered** |
| **Department-recommendation silent failure** | every department scored 0 → **10/10 departments healthy** | found and fixed by measurement |

> All performance numbers were measured with JMeter and reproducible benchmarks; the `.jmx` files, results, and artifacts are committed under [`docs/benchmark/`](docs/benchmark/).

---

## Project Summary

| Item | Detail |
|---|---|
| One line | Collect from 3 news sources (Naver News API, AI Times RSS, The Guru RSS), OpenAI analysis, classify into 10 department ENUM x 4 categories, per-department Top-5 recommendation |
| Active period | 2025.07.01 ~ 2026.06 (staged evolution, see version table) |
| Role | SK mySUNI Sunny-C cohort 4 v1/v2 team member, then sole owner of v3/v4 |
| Stack | Java 21, Spring Boot 3.5.6, MySQL 8, Redis, Qdrant, Next.js 15, OpenAI gpt-4o-mini, text-embedding-3-small, AWS Elastic Beanstalk, GitHub Actions |
| Status | v3 deployed on AWS EB, **v4: all 9 senior-review items implemented + 4 performance metrics measured** |
| Key assets | [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md) (9 senior items, 1:1 mapping to code), [TECHNICAL_DECISIONS.md](docs/TECHNICAL_DECISIONS.md) (7 decisions with alternatives and rationale) |

---

## What Problem Is This Solving

IT/AI staff across SK's 10 departments manually clip, dedupe, and summarize industry news every day. As of 2024, SK affiliate staff reportedly spent **about 1.5 hours per day, 7~8 hours per week** on news clipping; in 2025 some teams downgraded the activity to once a week, because the manual cost was eating into strategic work.

INSK automates this collect-and-analyze loop and returns only the articles that are genuinely relevant per department. Because classification, summarization, and recommendation all run on LLMs, three engineering challenges follow.

1. **Cost**: keep the per-LLM-call unit cost low enough to be economical at organization scale.
2. **Reliability**: make sure a single LLM API failure never drops an article; preserve retry, fallback, and reprocess paths.
3. **Scalability**: design distributed cache, VectorDB, and transaction boundaries so behavior stays consistent as instances scale out.

---

## Version Evolution: roughly one year of staged growth

This was not built in one shot; it **evolved in stages over roughly a year.** It started as a PoC in the SK Sunny-C cohort 4 in summer 2025, the product code (v3) was completed and deployed at year-end, and the following spring and summer it was rearchitected into v4 after senior review. The seemingly long total span is not one task dragging on but staged growth (**PoC → productized deployment → rearchitecture**); v4's core improvements were concentrated in **June 2026.**

| Version | Period | Stack | Result |
|---|---|---|---|
| v1/v2 (Sunny-C) | **2025.07.01 ~ 08.21** | Make → Python + Streamlit | In-house PoC, operational validation |
| **v3** | **2025.09.09 ~ 12.26** | **Spring Boot 3 + Next.js 15**, AWS EB + GitHub Actions ECR, daily 08:00 cron | Deployed on AWS |
| **v4** | **2026.05.19 ~ 06.18** | Absorbed 9 senior-review items: retry/fallback/DLQ, transaction split, parallelization, Redis cache, VectorDB (Qdrant) ANN, security | **All 9 implemented + measured** |

---

## Core Contributions (Gunwoo Park)

> Shared lens: even when the surface response (HTTP 200) looks fine, measure one level below to find the hidden defect.

1. **Silent failure found and fixed by measurement (signature)**: department Top-5 recommendation scores were silently dead at **0 for every department**, behind a healthy 200 response and normal article payloads. I found it by logging and measuring the recommendation scores directly. The cause was a dimension mismatch between article embeddings (OpenAI 1536-d) and keyword embeddings (placeholder 256-d) whose exception was swallowed as 0.0 by a `try-catch`. I switched to real embeddings, surfaced the dimension mismatch via an explicit exception and log (regression guard), and added the 4 missing department mappings, recovering **domain-appropriate recommendations for all 10 departments** (qualitative validation).
2. **Classification balance recovery**: gpt-4o-mini was tagging LLM articles as AI Business, producing a 71% skew to one category, which I found by measuring with SQL. I redesigned the 4-category definitions and boundaries, rebuilt the SYSTEM_PROMPT, and ran a DB migration, recovering **AI Business 71% → 55% (-16%p) and LLM 4% → 20% (5x).**
3. **All 9 senior-review items implemented + measured**: I fixed transaction scope, synchronous processing, the missing cache, and brute-force search in turn, all verified by benchmark (see "Results at a Glance"). The alternatives and rationale for each choice are recorded as 7 entries in [TECHNICAL_DECISIONS.md](docs/TECHNICAL_DECISIONS.md).
4. **Joint LLM cost and reliability design**: model externalization (analysis / simple / embedding, externalized so each task can pin its own model; currently analysis also runs on gpt-4o-mini for cost), exponential-backoff retry plus fallback, and a DLQ state machine (`ANALYSIS_FAILED` then `DEAD` after the limit) so transient errors lose **zero** articles.

---

## 9 Senior-Review Items → v4 Status

| # | Item (summary) | Reflected |
|:-:|---|---|
| 1 | dedup `findAll` OOM and cost; embedding JSON storage cannot be indexed → VectorDB/HNSW | ✅ Title Jaccard dedup ($0 filter before the LLM) + **recommendation moved to Qdrant ANN (KNN)** |
| 2 | gpt-4o overused for simple work | ✅ Model externalization (analysis/simple/embedding/fallback) |
| 3 | `runPipelineSync` fully `@Transactional` → connection-pool exhaustion | ✅ External calls outside the transaction, only the save in a short transaction (`ArticlePersistenceService`) |
| 4 | Sequential processing → parallelize | ✅ Per-article `CompletableFuture` + dedicated pool |
| 5 | No retry, transient errors lose articles | ✅ `@Retryable` (exp backoff + jitter), `@Recover` fallback, DLQ, auto reprocess |
| 6 | Score API `permitAll` | ✅ Dead rule removed + auth regression test |
| 7 | Hardcoded CORS | ✅ `cors.allowed-origins` externalized |
| 8 | Hardcoded config values | ✅ Thresholds, retry, cache TTL, etc. externalized |
| 9 | In-memory cache is per-server and partial → Redis | ✅ Redis distributed cache (JDK serialization) |

> Beyond the review, I also added **external-API timeouts** and **DLQ auto-drain (@Scheduled)** from my own audit. Full matrix in [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md).

---

## Measurements (actual)

### v4 four performance metrics (JMeter, reproducible bench, [`docs/benchmark/`](docs/benchmark/))

| Work | miss/processing Before | After | Improvement | Key |
|---|---:|---:|---:|---|
| Redis cache (#9) | 3,950 ms | 21.5 ms | 99.5%↓ (184x) | repeated compute removed, p95 4,384→40ms |
| Parallelization (#4) | 5,041 ms | 631 ms | 87.5%↓ (8.0x) | overlapped I/O waits, dedicated pool (max 8) |
| ANN recommendation (#1) | 3,950 ms | 1,616 ms | 59%↓ (2.4x) | N+1 loads, JSON parsing removed |
| Transaction split (#3) | 1,544 ms | 509 ms | 67%↓ (3.0x) | connection not held during external I/O (pool=3, 9 concurrent) |

### v3 operational measurement (gpt-4o-mini, measured 2026-05)

| Metric | Value |
|---|:---:|
| Cumulative articles | ~320 |
| **Per-article unit cost** | **~$0.0005** (gpt-4o-mini) |
| Model cost | running analysis on gpt-4o-mini cuts ~90%+ per call vs gpt-4o |

### Classification balance / department recommendation

| Item | Before | After |
|---|---|---|
| Classification skew (AI Business) | 71% | **55%** (-16%p) |
| LLM category | 4% | **20%** (5x) |
| Department recommendation score | every department 0 | **10/10 departments healthy** |

> **Honest labeling**: the cost-ladder "filter rates" (URL 40%, Jaccard 8%) are [predicted figures](MENTOR_FEEDBACK_CHANGELOG.md). Department recommendation has no ground-truth set, so it is **qualitatively validated**; I do not claim quantitative scores like Precision@k. The old "Redis 195→70ms" figure has no artifact, so it is retired, and only the v4 measurements above are cited.

---

## Architecture

### Data flow (full v4 pipeline)

![INSK v4 news analysis pipeline data flow (collect → cost ladder → resilience/storage → recommendation)](docs/img/insk-v4-dataflow.png)

### v3 data flow (deployed)

```
3 news sources (Naver REST+Jsoup, AI Times RSS, The Guru RSS)
        │
        ▼
Spring Boot 3.5.6 (Java 21)
  NewsPipelineService: collect → analyze → embed → score (per-article parallel)
  Spring Security + JWT (1h TTL), per-department Top-5 recommendation
        │
        ├─ MySQL 8.0  (metadata: users, articles, analyses, scores ...)
        ├─ Redis      (distributed cache: department Top-5)
        └─ Qdrant     (VectorDB: article embedding HNSW index)
        │
        ▼
Next.js 15.5.4 (App Router) + Tailwind 4
Deploy: GitHub Actions → AWS ECR → Elastic Beanstalk
```

### v4 cost ladder + resilience

```
new article in
    ▼ Layer 1 | URL match              ($0)              ✅
    ▼ Layer 2 | Title Jaccard          ($0)              ✅
    ▼ Layer 3 | gpt-4o-mini analysis   ($0.0005/article) ✅  ← only new articles passing layers 1~2
       │
       ├─ retry (5x exp backoff + jitter) → fallback (higher model) → on failure DLQ (ANALYSIS_FAILED→DEAD)  ✅
       └─ index analysis embedding into Qdrant (outside tx) → department recommendation via Qdrant KNN  ✅

Department recommendation: average keyword embeddings → Qdrant ANN (top30) → popularity re-rank → Top5  (Redis cache)
```

> Note: mentor #1's "VectorDB/HNSW" was applied to **department-recommendation search** (brute-force → ANN, 2.4x). A semantic-dedup ANN was not adopted, due to threshold false-positive risk and the absence of a ground-truth set; the same Qdrant infra is in place, so it remains a candidate for later if needed.

**Cost-ladder filter rates (predicted, not measured)**

| Stage | Cost | Filter rate (predicted) |
|:---:|:---:|:---:|
| 1. URL exact match | $0 | ~40% |
| 2. Title Jaccard | $0 | ~8% |
| 3. gpt-4o-mini analysis | $0.0005/article | only articles passing layers 1~2 |

> Filter rates are predicted figures from [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md), and will be updated with real load-test measurements.

---

## Implemented / Future Track

**Implemented (all on main):** all 9 senior items, external-API timeout, DLQ auto-drain, silent-failure fix, classification-balance recovery. Includes measurements, artifacts, and regression tests.

**Future (for multi-instance deployment):** ShedLock (scheduler dedup), PENDING-first persistence (crash-safety), PDF S3 migration, Qdrant fallback/circuit breaker, scraping UA rotation, cost-ladder semantic-dedup ANN.

---

## Tech Stack

| Area | Stack |
|---|---|
| Backend | Spring Boot 3.5.6, Java 21, Gradle, Spring Data JPA, Hibernate, MySQL 8.0, **Spring Retry**, **Redis (RedisCacheManager)**, Spring Security, jjwt 0.12.x, Jsoup 1.17.2, PDFBox, iText |
| Vector / AI | **Qdrant (VectorDB, HNSW)**, OpenAI gpt-4o-mini (analysis and default, switchable via externalized config), gpt-4o (fallback), text-embedding-3-small (embedding) |
| Frontend | Next.js 15.5.4 (App Router), React 19.1, TypeScript 5, Tailwind CSS 4, Axios |
| Infrastructure | AWS Elastic Beanstalk (ap-northeast-2), AWS ECR (multi-stage Docker), GitHub Actions (test → build → ECR push → S3 → EB deploy) |

---

## Role and Ownership

| Area | What I did |
|---|---|
| System design | Integrated 3 news sources + 3 OpenAI-family APIs, normalized 10 department ENUM x 4 categories |
| Backend impl | collect→analyze→embed→score pipeline, JWT auth, per-department Top-5, retry/fallback/DLQ, transaction split, parallelization |
| AI / Vector | classification, embedding, cosine scoring, Qdrant VectorDB ANN search, GPT output validation |
| Deploy / Ops | AWS EB + GitHub Actions ECR, daily collection cron |
| Review absorption | landed 9 senior items as code PRs, measured the effect, recorded the decisions |

> SK mySUNI Sunny-C cohort 4 v1/v2 team member; sole owner from v3 onward.

---

## How to Run Locally

### Prerequisites
- Java 21, MySQL 8.0 (`insk_db`), OpenAI API Key, Naver Developers ID/Secret
- (optional) Redis, Qdrant for cache and VectorDB. Start with Docker:
  ```bash
  docker run -d --name insk-redis  -p 6379:6379 redis:7-alpine
  docker run -d --name insk-qdrant -p 6333:6333 qdrant/qdrant
  ```

### 1. Backend
```bash
cd insk-backend/backend
# write application.properties (see BACKEND_SETUP_GUIDE.md)
./gradlew bootRun     # Windows: .\gradlew.bat bootRun
```
> On startup, `VectorIndexInitializer` backfills MySQL embeddings into Qdrant.

### 2. Frontend
```bash
cd insk-frontend
npm install
# .env.local with NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
npm run dev
```

### 3. Trigger the pipeline (PowerShell)
```powershell
$body = @{ email = "your-email"; password = "your-password" } | ConvertTo-Json
$token = (Invoke-RestMethod "http://localhost:8080/api/v1/auth/login" -Method POST -ContentType "application/json" -Body $body).accessToken
Invoke-RestMethod "http://localhost:8080/api/v1/articles/run-pipeline" -Method POST -Headers @{ Authorization = "Bearer $token" }
```
Scheduled run: `@Scheduled(cron = "0 0 8 * * *")`, daily 08:00 KST. DLQ reprocess every 6 hours.

---

## In-Repo References

- [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md) | 9 senior items → 1:1 code mapping
- [docs/TECHNICAL_DECISIONS.md](docs/TECHNICAL_DECISIONS.md) | 7 technical decisions (alternatives, rationale)
- [docs/benchmark/](docs/benchmark/) | JMeter `.jmx`, results, measurement reports
- [insk-backend/BACKEND_SETUP_GUIDE.md](insk-backend/BACKEND_SETUP_GUIDE.md) | Local setup
- [README.md](README.md) | Korean master, [archive/README_v3_legacy.ko.md](archive/README_v3_legacy.ko.md) | v3 snapshot

---

## Contact

**Gunwoo Park | Backend Engineer (Sangmyung University, Software, 4th year, graduating 2027.02)**

- Email: gunwoo363@gmail.com
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)

# INSK : 뉴스 인텔리전스 플랫폼

> 3개 외부 뉴스 소스에서 기사를 수집해 OpenAI 분석·임베딩을 거쳐, SK 10개 부서별로 가장 관련성 높은 기사를 추천하는 Spring Boot 3 / Next.js 15 플랫폼.
>
> **문제를 기능 구현으로 끝내지 않고, 운영·정합성·확장성 관점에서 다시 설계하는 백엔드 작업.** v3 AWS EB 배포 후 SKT 시니어 코드 리뷰 9건을 받아 v4 비용·신뢰성 리아키텍처로 진화 중.

🎬 [Demo 영상](https://www.youtube.com/watch?v=WlKGbvbxHik) · 🇬🇧 [README.en.md](README.en.md) · 📜 [v3 보존본](README_v3_legacy.ko.md)

---

## 프로젝트 요약

| 항목 | 내용 |
|---|---|
| 한 줄 | 3개 뉴스 소스(Naver News API · AI Times RSS · The Guru RSS) 수집, OpenAI 분석, 10개 부서 ENUM × 4 카테고리 분류, 부서별 Top-5 추천 |
| 활동 기간 | 2025.07.01 ~ 진행 중 |
| 역할 | 팀 리드 (SK mySUNI 써니C 4기 출발, v3·v4 단독 고도화) |
| 기술 스택 | Java 21 · Spring Boot 3.5.6 · MySQL 8 · Next.js 15 · OpenAI GPT-4o · text-embedding-3-small · AWS Elastic Beanstalk · GitHub Actions |
| 상태 | v3 AWS EB 배포 완료, v4 비용 사다리 1·2·4단계 코드 반영, 3단계 ANN·Fallback·DLQ는 설계 단계 |
| 핵심 자산 | [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md) ｜ SKT 시니어 9건 리뷰 → v4 재설계 1:1 매핑 |

### 데이터 플로우 (v3 + v4 비용 사다리)

![INSK 4단계 비용 사다리 아키텍처](docs/img/insk-cost-ladder.png)

---

## 어떤 문제를 풀고 있나

SK 10개 부서의 IT/AI 직원은 매일 산업 뉴스를 수동으로 클리핑·중복 제거·요약한다. 2024년 기준 SK 계열사 직원들은 **하루 약 1.5시간, 주당 7~8시간**을 뉴스 클리핑에 쓰고 있다고 보고됐고, 2025년에는 몇몇 팀이 이 활동을 주 1회로 다운그레이드했다. 수작업 비용이 전략 업무를 잡아먹어서다.

INSK는 이 수집·분석 루프를 자동화하고, 부서별로 정말 관련 있는 기사만 다시 돌려준다. LLM 기반으로 분류·요약·추천을 처리하기 때문에 두 가지 엔지니어링 도전 과제가 생긴다.

1. **비용**: LLM 호출당 단가를 조직 규모에서 경제성이 성립할 만큼 낮게 유지하는 것.
2. **신뢰성**: 단일 LLM API 실패가 기사를 통째로 버리지 않도록, 폴백·재처리 경로를 보존하는 것.

### 버전 진화

| 버전 | 시기 | 스택 | 위치 |
|---|---|---|---|
| v1 (써니C) | 2024 | Make + Streamlit | 사내 PoC |
| v2 (Phase 2) | 2025 상반기 | Python + Streamlit | 운영 검증 |
| **v3** | 2025.07 ~ 2026.04 | **Spring Boot 3 + Next.js 15**, AWS EB + GitHub Actions ECR, 매일 08시 cron, OpenAI 분석 + 임베딩 + 부서별 Top-5 | AWS 배포 완료 |
| **v4** | 2026.05 ~ 진행 중 | v3 위에 SKT 시니어 9건 리뷰 흡수: 4단계 비용 사다리 (URL · 제목 Jaccard 0.85 · 벡터 ANN · GPT-4o), `@Transactional` 분해, Redis MD5 prompt 캐싱 | 1·2·4단계 Done / 3단계·Fallback·DLQ Designed |

### 핵심 기여 (박건우)

1. **분류 정합성 회복**: gpt-4o-mini가 LLM 기사를 AI Business로 분류해 한쪽 쏠림(71%)이 발생 → LLM · INFRA · Telco · AI Business 4 카테고리 정의·분리 기준을 다시 설계하고 SYSTEM_PROMPT 재구성 + DB 마이그레이션으로 회복.
2. **v4 4단계 비용 사다리 설계**: SKT 시니어 9건 지적을 코드 PR 단위로 정리, URL → 제목 Jaccard 0.85 → 벡터 ANN → GPT-4o 단계로 분기시켜 LLM 호출을 신규 기사로만 한정.
3. **LLM 호출 비용·신뢰성 양면 재설계**: 모델 외부화(analysis / simple / embedding), 폴백(gpt-4o → gpt-4o-mini), DLQ 상태 머신(ANALYSIS_FAILED + 별도 재처리)을 설계.

---

## 핵심 측정 결과

### v3 운영 측정 (gpt-4o-mini 기반, 2026-05 실측)

| 지표 | 값 | 출처 |
|---|:---:|---|
| 누적 article | 약 320건 | DB 본인 시야 (2026-05-24) |
| OpenAI 누적 비용 | $0.19 | OpenAI usage 4회 trigger |
| **1건당 실측 단가 (gpt-4o-mini)** | **약 $0.0005** | usage 페이지 / 신규 건수 |
| 매일 평균 수집 | 30~80건 | 4회 trigger 평균 |

### v4 분류 정합성 회복 (taxonomy 재설계, 2026-05-22)

| 카테고리 | 마이그레이션 전 | 마이그레이션 후 | 변화 |
|---|:---:|:---:|:---:|
| AI Business (구 AI Ecosystem) | 71% | **55%** | **-16%p 완화** |
| LLM | 4% | **20%** | **5배 회복** |

> 분류 결과를 SQL로 조회한 뒤 71% 한쪽 쏠림을 발견, 카테고리 정의·분리 기준을 다시 설계해 SYSTEM_PROMPT 재구성 + DB SQL 마이그레이션으로 36건 재분류. 응답 코드·스키마는 모두 정상이었지만 분류 결과가 의미적으로 망가져 있던 사례.

### 비용 사다리 예상 효과 (v4 청사진, 실측 아님)

| 단계 | 비용 | 거르는 비율 (예측) |
|:---:|:---:|:---:|
| 1. URL 정확 매칭 | $0 | 약 40% |
| 2. 제목 Jaccard ≥ 0.85 | $0 | 약 8% |
| 3. 벡터 ANN | 약 $0 | 약 18% |
| 4. GPT-4o (또는 gpt-4o-mini) | $0.05 / $0.0005 | 통과한 신규 기사만 |

> 거르는 비율은 [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md) 예측치. 실측은 3단계 ANN 구현 + 부하 테스트 후 추가.

---

## 아키텍처

### v3 데이터 플로우 (배포 완료)

```
3개 뉴스 소스
  Naver News API (REST + Jsoup 본문 스크래핑)
  AI Times (RSS XML 파싱)
  The Guru (RSS XML 파싱)
        │
        ▼
Spring Boot 3.5.6 (Java 21)
  NewsPipelineService (@Async)
    수집 → OpenAI 분석 → 임베딩 → 스코어링
  Spring Security + JWT (1시간 TTL)
  부서별 Top-5 추천
        │
        ▼
MySQL 8.0
  users · keywords · articles · article_analyses
  article_embeddings · article_feedbacks · article_scores
        │
        ▼
Next.js 15.5.4 (App Router) + Tailwind CSS 4
  / · /articles/[id] · /keywords · /departments · /favorites

배포: GitHub Actions → AWS ECR → Elastic Beanstalk
     (multi-stage Dockerfile · EB Ready-state polling guard)
```

### v4 비용 사다리

```
새 기사 입력
    │
    ▼ Layer 1 ｜ URL 매칭                ($0)         ✅ Done
    │  DB 인덱스 O(1) 조회
    ▼ Layer 2 ｜ 제목 Jaccard ≥ 0.85    ($0)         ✅ Done
    │  최근 2일 윈도우 + 토큰 집합 비교
    ▼ Layer 3 ｜ 벡터 ANN                (약 $0)      🟡 Designed
    │  임베딩 1회, 사전 인덱싱
    ▼ Layer 4 ｜ GPT-4o (또는 mini)      ($0.05 / $0.0005)  ✅ Done
       1~3단계 통과한 신규 기사만

실패 처리 (둘 다 🟡 Designed)
    LLM 실패 → gpt-4o-mini Fallback
    Fallback 실패 → ANALYSIS_FAILED 상태 + 별도 재처리
```

---

## 구현 완료 (Implemented)

- **v3 운영**: AWS Elastic Beanstalk 배포, GitHub Actions ECR 파이프라인, 3개 소스 수집, GPT-4o 분석 + 임베딩, 부서별 Top-5 추천, JWT 인증, 좋아요·피드백, PDF 내보내기
- **v4 부분 구현**: 비용 사다리 1·2·4단계 (URL 매칭 + 제목 Jaccard + LLM 호출), OpenAI 모델 외부화 (analysis / simple / embedding), taxonomy 재설계 (AI Ecosystem → AI Business + LLM 정의 강화), 임계치·dedup 윈도우 application.properties 외부화

---

## 설계 완료 / 진행 중 (In Progress)

- **v4 비용 사다리 3단계**: 벡터 ANN (FAISS 또는 HNSW 인덱스 검토)
- **재시도·폴백**: `@Retryable(maxAttempts=5, backoff=@Backoff(delay=1000, multiplier=2.0))` + `@Recover` smaller-model fallback
- **DLQ 메커니즘**: ANALYSIS_FAILED 상태 + 별도 재처리 잡
- **트랜잭션 분해**: ArticleSaveService 분리, 외부 API 호출을 DB 커넥션 밖으로
- **Redis CacheManager**: 기사 본문 MD5 키 prompt 캐싱, 분산 캐시 일관성
- **키워드 병렬화**: `CompletableFuture.runAsync` 도입

---

## 로드맵

### v4 구현
- [x] 비용 사다리 1·2단계 (URL 매칭 + 제목 Jaccard)
- [x] OpenAI 모델 외부화 (analysis / simple / embedding)
- [x] taxonomy 재설계 (AI Business + LLM 정의 강화)
- [x] 임계치·dedup 윈도우 외부화
- [ ] 비용 사다리 3단계 (벡터 ANN)
- [ ] `@Retryable` + `@Recover` fallback
- [ ] DLQ 메커니즘 (ANALYSIS_FAILED + 별도 재처리)
- [ ] 트랜잭션 범위 분해
- [ ] Redis CacheManager (분산 캐시)
- [ ] 키워드 병렬화 (`CompletableFuture.runAsync`)

### v4 검증
- [ ] 비용 사다리 실 운영 수치 측정 (현재는 설계 예측치)
- [ ] retry / fallback 카오스 테스트

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| Backend (v3) | Spring Boot 3.5.6 · Java 21 · Gradle · Spring Data JPA · Hibernate · MySQL 8.0 · Spring Security · jjwt 0.12.x · BCrypt · Jsoup 1.17.2 · Spring WebFlux · PDFBox 2.0.30 · iText 7.2.5 · `@Async` ThreadPoolTaskExecutor |
| Backend (v4 예정) | Spring Retry · Resilience4j · RedisCacheManager |
| Frontend | Next.js 15.5.4 (App Router) · React 19.1 · TypeScript 5 · Tailwind CSS 4 · Axios |
| AI / Data | OpenAI GPT-4o (기사 분석) · text-embedding-3-small (의미 임베딩) · gpt-4o-mini (저비용 분기) |
| Infrastructure | AWS Elastic Beanstalk (ap-northeast-2) · AWS ECR (multi-stage Docker) · GitHub Actions (test → build → ECR push → S3 → EB deploy with Ready-state polling) |

---

## 역할 및 담당

| 영역 | 담당 내용 |
|---|---|
| 시스템 설계 | 3개 뉴스 소스 + OpenAI 계열 3개 API 통합, 10개 부서 ENUM × 4 카테고리 정규화 구조 |
| 백엔드 구현 | Spring Boot 파이프라인 (수집 → 분석 → 임베딩 → 스코어링), JWT 인증, 부서별 Top-5 알고리즘 |
| AI 통합 | GPT-4o 분류 + text-embedding-3-small 임베딩 + 코사인 유사도 스코어링 + GPT 출력 검증 5계층 |
| 배포·운영 | AWS EB + GitHub Actions ECR 파이프라인, 매일 수집 cron 운영 |
| 리뷰 흡수 | SK 시니어 9건 지적을 매트릭스로 정리, v4 리팩토링 계획서 작성, 일부 항목 코드 반영 |

> 팀 리드: SK mySUNI 써니C 4기 v1/v2 출발 단계. v3부터는 단독 고도화로 진행.

---

## 로컬 실행 방법

### 사전 요구사항
- Java 21
- MySQL 8.0 (database: `insk_db`)
- OpenAI API Key
- Naver Developers Client ID / Secret

### 1. 백엔드

```bash
cd insk-backend/backend
# application.properties 작성 (BACKEND_SETUP_GUIDE.md 참고)
./gradlew bootRun
# Windows: .\gradlew.bat bootRun
```

### 2. 프론트엔드

```bash
cd insk-frontend
npm install
# .env.local 에 NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
npm run dev
```

### 3. 파이프라인 실행 (PowerShell)

```powershell
# 로그인 → 토큰 받기
$body = @{ email = "본인이메일"; password = "본인비번" } | ConvertTo-Json
$token = (Invoke-RestMethod "http://localhost:8080/api/v1/auth/login" -Method POST -ContentType "application/json" -Body $body).accessToken

# 수집·분석 trigger (비동기, 2~5분 소요)
Invoke-RestMethod "http://localhost:8080/api/v1/articles/run-pipeline" `
  -Method POST -Headers @{ Authorization = "Bearer $token" }
```

자동 실행은 `@Scheduled(cron = "0 0 8 * * *")` 매일 오전 8시 (KST).

---

## 저장소 안 참고 문서

- [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md) ｜ SKT 시니어 9건 지적 → v4 재설계 1:1 매핑
- [PROJECT_SPECIFICATION.md](PROJECT_SPECIFICATION.md) ｜ 기능 명세
- [insk-backend/BACKEND_SETUP_GUIDE.md](insk-backend/BACKEND_SETUP_GUIDE.md) ｜ 로컬 환경 셋업 상세
- [README.en.md](README.en.md) ｜ 영문 버전 (한글 마스터 기준 번역)
- [README_v3_legacy.ko.md](README_v3_legacy.ko.md) ｜ v3 시점 보존본

---

## 연락처

**박건우 ｜ Backend Engineer (상명대 소프트웨어학과 4학년, 2027.02 졸업 예정)**

- 이메일: gunwoo363@gmail.com
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)

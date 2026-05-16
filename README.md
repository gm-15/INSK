# INSK — 뉴스 인텔리전스 플랫폼

> 여러 외부 뉴스 소스에서 기사를 수집해 GPT-4o 분석·임베딩을 거쳐, 사용자 부서별로 가장 관련성 높은 기사를 추천하는 Spring Boot 3 / Next.js 15 플랫폼.
> **현재 v4 재설계 단계 — 시니어 코드 리뷰 기반의 비용·신뢰성 리아키텍처.**

🇬🇧 영문 버전: [README.en.md](README.en.md) · 📜 v3 시점 보존본: [README_v3_legacy.ko.md](README_v3_legacy.ko.md)

---

## 🚀 30초 요약 (면접관용)

| 항목 | 내용 |
|---|---|
| 한 줄 | 7개 외부 소스 → 10개 부서 ENUM × 4 카테고리로 정규화하는 뉴스 인텔리전스 플랫폼 |
| 박건우 역할 | 팀 리드 (SK mySUNI 써니C 4기 출발 → 개인 고도화) |
| 핵심 기술 | Spring Boot 3 · Java 21 · OpenAI GPT-4o · text-embedding-3-small · Spring Retry · Resilience4j · AWS Elastic Beanstalk |
| v3 (배포 완료) | AWS EB 배포, GitHub Actions ECR 파이프라인, JWT 인증, 부서별 Top-5 추천 |
| v4 (설계 완료, 구현 진행 중) | SKT AI Data Engineering 시니어 **9건 코드 리뷰 지적**을 매트릭스로 정리, 비용·신뢰성 재설계 원칙 결정 |
| 정직 노트 | v4 cost ladder의 수치는 **설계 목표**. 운영비 실측은 아직 (실 사용자 받지 않음) |

---

## 📰 어떤 문제를 풀고 있나? (도메인 설명)

대기업 line-of-business 직원은 매일 산업 뉴스를 수동으로 클리핑·중복 제거·요약합니다. 2024년 기준 SK 계열사 직원들은 **하루 약 1.5시간, 주당 7~8시간**을 뉴스 클리핑에 쓰고 있다고 보고됐고, 2025년에는 몇몇 팀이 이 활동을 주 1회로 다운그레이드했습니다 — 수작업 비용이 전략 업무를 잡아먹어서요.

INSK는 이 ingestion-and-analysis 루프를 자동화하고 부서별 고관련성 기사만 직원에게 다시 보내줍니다. **엔지니어링 도전 과제**는 — LLM 호출당 비용을 조직 규모에서 경제성이 성립할 만큼 낮게 유지하면서도, 단일 LLM API 실패가 기사를 통째로 버리지 않도록 신뢰성을 보존하는 것. v4 재설계의 다섯 가지 운영 원칙은 모두 이 제약에 대한 직접적 답변입니다.

---

## 🏛️ v3 vs v4 한눈에 비교

| 항목 | v3 (배포 완료) | v4 (설계 완료, 구현 진행 중) |
|---|---|---|
| **상태** | AWS Elastic Beanstalk 배포 | 마이그레이션 계획은 [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md) |
| **LLM 비용 전략** | 모든 기사에 GPT-4o 직접 호출 | **LLM 호출 전 저비용 중복 체크를 우선 통과시키는 비용 체계화** — $0 URL → $0 title Jaccard → ~$0 vector ANN → $0.05 GPT-4o |
| **트랜잭션 범위** | 파이프라인 전체 `@Transactional` | per-write 최소 트랜잭션; 외부 API 호출은 DB 커넥션 밖으로 |
| **재시도·폴백** | 없음 — silent failure | `@Retryable(maxAttempts=5)` + `@Recover` smaller-model fallback |
| **캐시** | 인메모리 `ConcurrentMapCacheManager` | `RedisCacheManager` + 기사 본문 MD5 키 prompt 캐싱 |
| **동시성** | 키워드별 순차 루프 | `CompletableFuture.runAsync` 키워드 병렬 |
| **설정** | 하드코드 상수 | `application.properties`로 외부화, 런타임 튜닝 |

> **💡 정직 노트.** 위 표의 cost 수치는 **v4 설계 목표** (per-call OpenAI pricing × 계획된 필터 비율). 운영비 실측·집계 벤치마크는 **v4 구현 + 실 부하 테스트 후에야 추가됩니다.** 현재까지 INSK는 실 사용자를 받은 적이 없으며, GPT-4o 운영비가 실제로 발생한 적도 없습니다.

---

## 🎯 v3 핵심 기능 (배포 완료)

### 1. 다중 소스 뉴스 수집
- 네이버 뉴스 API 검색 + 본문 스크래핑
- AI Times · The Guru 직접 ingestion
- URL 기반 중복 제거
- 403-방어 헤더 (User-Agent, Referrer)

### 2. AI 분석 파이프라인
- GPT-4o 한국어 요약 생성
- 인사이트 추출
- 강제 JSON-schema 카테고리 분류: **Telco / LLM / INFRA / AI Ecosystem**
- 태그 생성 (JSON array)
- HTML 태그 정리

### 3. 임베딩 & 스코어링
- 기사별 임베딩 (`text-embedding-3-small`)
- 사용자 키워드 vs 기사 코사인 유사도
- 0~10 점수, 피드백 가중치로 자동 조정

### 4. 부서별 Top-5 추천
- **10개 조직 단위**: T_CLOUD, T_NETWORK_INFRA, T_HR, T_AI_SERVICE, T_MARKETING, T_STRATEGY, T_ENTERPRISE_B2B, T_PLATFORM_DEV, T_TELCO_MNO, T_FINANCE
- 부서별 키워드 집계 → 랭킹 Top-5

### 5. 피드백 루프
- 좋아요 / 싫어요 토글
- 텍스트 피드백 (익명 가능)
- 피드백 시 점수 자동 재계산

### 6. 인증 · 보안
- JWT (1시간 TTL)
- BCrypt 비밀번호 해싱
- 비밀번호 재설정 토큰 (1시간, 일회용)
- Spring Security endpoint 보호

### 7. PDF 내보내기
- 기사 상세 PDF 생성 (PDFBox / iText)

---

## 📚 v4 스토리 — 왜 이 프로젝트가 의미 있는가

### v3 배포 후 멘토 코드 리뷰

INSK v3을 AWS Elastic Beanstalk에 배포한 뒤, **SK AI Data Engineering 팀 시니어 엔지니어**에게 코드 리뷰를 부탁했습니다. 9건의 critical/major 지적이 돌아왔는데, 키워드 4가지만 짚으면:

| 지적 영역 | 핵심 내용 |
|---|---|
| **OOM 위험** | 파이프라인 전체를 하나의 `@Transactional`로 묶어 메모리·커넥션 점유가 길어짐 |
| **트랜잭션 범위 과도** | 외부 LLM 호출이 DB 커넥션 안에 있어 외부 지연이 DB 락에 전파 |
| **Retry 부재** | LLM API 실패 시 silent failure → 기사 통째로 손실 |
| **CORS 하드코딩** | 운영 환경에서 origin 변경 시 재배포 필요 |

### 매트릭스로 정리하는 습관

9건 지적을 회의록 한 줄로 두지 않고, [`MENTOR_FEEDBACK_CHANGELOG.md`](MENTOR_FEEDBACK_CHANGELOG.md)에 **"피드백 → 영향 파일 → 변경 유형 → 심각도"** 매트릭스로 정리했습니다. 단순 응답이 아니라 **추후 PR로 닫을 수 있는 형태로 문서화**하는 것이 핵심이라 배웠고, 이 매트릭스가 v4 리팩토링 계획서의 기반이 되었습니다.

### v4 재설계 핵심 원칙

> **"LLM 호출 전에 저비용 중복 체크를 우선 통과시키는 비용 체계화"**

가장 비싼 자원(LLM 호출) 앞에 단계적 필터를 세우고, 각 단계에서 떨어트릴 케이스를 측정으로 정의한다는 원칙. 모든 싼 필터가 통과되기 전에는 LLM이 호출되지 않습니다.

```
새 기사 입력
  │
  ▼ Layer 1 — URL 매칭 ($0)
  │  이미 처리한 URL이면 즉시 dedup
  │
  ▼ Layer 2 — title Jaccard 유사도 ($0)
  │  기존 기사 제목과 토큰 유사도 0.9 이상이면 dedup
  │
  ▼ Layer 3 — vector ANN (~$0)
  │  기존 임베딩과 코사인 유사도 0.95 이상이면 dedup
  │
  ▼ Layer 4 — GPT-4o 분석 ($0.05)
     이 단계까지 살아남은 새 기사만 LLM에 보냄
```

이 ladder는 단순한 비용 절감이 아니라 **시스템의 사고 방식 전환**입니다 — 비싼 자원 앞에는 측정 가능한 단계적 필터를 세우고, 각 단계에서 떨어트릴 케이스를 데이터로 정의한다는 원칙.

---

## 🔧 기술 스택

### Backend
- **Spring Boot 3.5.6** · **Java 21** · Gradle
- Spring Data JPA · Hibernate · MySQL 8.0
- Spring Security · jjwt 0.12.x · BCrypt
- Jsoup 1.17.2 · Spring WebFlux
- PDFBox 2.0.30 · iText 7.2.5
- Spring `@Async` (ThreadPoolTaskExecutor)
- *(v4)* Spring Retry · Resilience4j · RedisCacheManager

### Frontend
- **Next.js 15.5.4** (App Router) · React 19.1 · TypeScript 5
- Tailwind CSS 4 · Axios

### AI / Data
- **OpenAI GPT-4o** (기사 분석)
- **text-embedding-3-small** (의미 임베딩)
- **gpt-4o-mini** (v4 저비용 분기 계획)

### Infrastructure
- **AWS Elastic Beanstalk** (`ap-northeast-2`)
- **AWS ECR** (multi-stage Docker images)
- **GitHub Actions** (test → build → ECR push → S3 → EB deploy with Ready-state polling)

---

## 🏛️ 아키텍처 (v3, 배포 완료)

```
┌──────────────────────────────────────────────────────────────────┐
│  외부 소스                                                       │
│  네이버 뉴스 API · AI Times · The Guru                            │
└──────────────────────────────────────────────────────────────────┘
              │
              ▼  Jsoup 스크래퍼 · Spring WebFlux 클라이언트
┌──────────────────────────────────────────────────────────────────┐
│  Spring Boot 3.5.6 (Java 21)                                     │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ NewsPipelineService (@Async)                             │    │
│  │   ingestion → OpenAI 분석 → 임베딩 → 스코어링            │    │
│  └──────────────────────────────────────────────────────────┘    │
│  Spring Security + JWT (1시간 TTL)                               │
│  부서별 Top-5 추천                                               │
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
│  / · /articles/[id] · /keywords · /departments · /favorites      │
└──────────────────────────────────────────────────────────────────┘

배포: GitHub Actions → AWS ECR → Elastic Beanstalk
     (multi-stage Dockerfile · EB Ready-state polling guard)
```

---

## 👤 박건우 역할

| 영역 | 박건우 담당 |
|---|---|
| 시스템 설계 | 7개 외부 소스 통합 + 10개 부서 ENUM × 4 카테고리 정규화 구조 |
| 백엔드 구현 | Spring Boot 파이프라인 (ingestion → 분석 → 임베딩 → 스코어링) · JWT 인증 · 부서별 Top-5 알고리즘 |
| AI 통합 | GPT-4o 분류 + text-embedding-3-small 임베딩 + 코사인 유사도 스코어링 |
| 배포 | AWS EB + GitHub Actions ECR 파이프라인 |
| 시니어 피드백 흡수 | SK 시니어 9건 지적을 매트릭스로 정리 → v4 리팩토링 계획서 작성 |

---

## 🗺 로드맵

### v4 구현 (진행 중)

- [ ] **4-tier cost ladder** 실 코드 적용 (URL · Jaccard · vector ANN · GPT-4o)
- [ ] **`@Retryable` + `@Recover` smaller-model fallback** 구현
- [ ] **Redis Prompt 캐시** 도입 (기사 본문 MD5 키)
- [ ] **트랜잭션 범위 분해** — per-write 최소 트랜잭션, 외부 API 분리
- [ ] **`CompletableFuture.runAsync`** 키워드 병렬화
- [ ] **`application.properties` 외부화** — 모든 하드코드 상수 추출

### v4 검증

- [ ] cost ladder 실 운영 수치 측정 (현재는 설계 목표일 뿐)
- [ ] retry / fallback 카오스 테스트

---

## 📚 저장소 안 참고 문서

- [`MENTOR_FEEDBACK_CHANGELOG.md`](MENTOR_FEEDBACK_CHANGELOG.md) — SK 시니어 9건 지적 → v4 재설계 1:1 매핑 매트릭스
- [README.en.md](README.en.md) — 영문 버전
- [README_v3_legacy.ko.md](README_v3_legacy.ko.md) — v3 시점 보존본 (역사적 자료)

---

## 🔗 연락처

**박건우 ｜ Backend Engineer**

- 이메일: Gunwoo363@gmail.com
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)

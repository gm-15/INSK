# INSK v3.0 - 뉴스 트렌드 센싱 자동화 플랫폼

> 🇬🇧 **English version**: see [README.md](README.md)
> 📌 **이 파일은 v3 시점 한글 README의 보존본입니다.** 일부 항목(Redis 캐싱·재시도 로직·구조화 로깅 등)은 코드에 미구현 상태이며, v4 재설계에서 다뤄집니다. 자세한 내용은 [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md) 참조.

## 📋 프로젝트 개요

INSK는 AI 기반 뉴스 수집, 분석, 추천 시스템을 제공하는 플랫폼입니다. 다중 뉴스 소스에서 기사를 수집하고, OpenAI GPT 모델을 활용하여 기사를 분석·분류하며, 사용자 맞춤형 키워드 기반 필터링과 부서별 추천 시스템을 제공합니다.

### 핵심 가치 제안
- **자동화된 뉴스 수집**: Naver News, AI Times, The Guru 등 다중 소스에서 자동 수집
- **AI 기반 분석**: OpenAI GPT-4를 활용한 기사 요약, 인사이트 추출, 카테고리 분류, 태그 생성
- **맞춤형 필터링**: 사용자별 키워드 기반 기사 필터링 및 점수 계산
- **부서별 추천**: 10개 부서별 Top 5 기사 추천 시스템
- **실시간 피드백**: 좋아요/싫어요 및 텍스트 피드백을 통한 점수 개선

---

## 🏗️ 기술 아키텍처

### 백엔드 스택
- **Framework**: Spring Boot 3.5.6
- **Language**: Java 21
- **ORM**: JPA/Hibernate
- **Database**: MySQL 8.0
- **Security**: Spring Security + JWT
- **External APIs**:
  - OpenAI API (GPT-4o) — 기사 분석, 키워드 추천
  - Naver News API — 뉴스 수집
  - AI Times, The Guru — 뉴스 수집
- **Web Scraping**: Jsoup
- **Async Processing**: Spring `@Async`

### 프론트엔드 스택
- **Framework**: Next.js 15.5.4 (App Router)
- **Language**: TypeScript
- **Styling**: Tailwind CSS 4
- **HTTP Client**: Axios
- **State Management**: React Hooks + Local Storage

### 인프라
- **Build Tool**: Gradle / npm
- **Deployment**: AWS Elastic Beanstalk + ECR (백엔드), Vercel/Netlify (프론트엔드)
- **CI/CD**: GitHub Actions

---

## 🎯 v3에서 구현된 기능

### 1. 사용자 인증
- 회원가입·로그인·비밀번호 재설정·부서 변경
- JWT 토큰 (1시간 TTL), BCrypt 암호화

### 2. 뉴스 수집 및 분석 파이프라인
- Naver News / AI Times / The Guru 다중 소스
- URL 기반 중복 제거
- OpenAI GPT-4o 요약·인사이트·카테고리·태그
- text-embedding-3-small 임베딩 + 코사인 유사도 점수

### 3. 키워드 관리
- 사용자별 키워드 CRUD
- AI 키워드 추천 (OpenAI 부서 기반)
- 다른 사용자 키워드 조회

### 4. 기사 조회·필터링
- 페이지네이션, 카테고리·출처 필터
- N+1 쿼리 최적화 (배치 조회)

### 5. 피드백 시스템
- 좋아요/싫어요 토글
- 텍스트 피드백 (익명 가능)

### 6. 부서별 Top 5 추천
- 10개 부서 (T_CLOUD, T_NETWORK_INFRA, T_HR, T_AI_SERVICE, T_MARKETING, T_STRATEGY, T_ENTERPRISE_B2B, T_PLATFORM_DEV, T_TELCO_MNO, T_FINANCE)

### 7. PDF 리포트 생성
- 기사 상세 PDF 다운로드 (PDFBox / iText)

---

## 📚 v4 재설계 (계획)

v3 배포 후 SKT AI Data Engineering팀 시니어 엔지니어 코드 리뷰를 받았고, **9개 critical/major 지적**을 1:1로 추적·문서화하여 v4를 설계했습니다. 자세한 v3→v4 변경 대조는 [MENTOR_FEEDBACK_CHANGELOG.md](MENTOR_FEEDBACK_CHANGELOG.md) 참조.

### 핵심 변경 9가지
1. **비용 계층 중복 필터링**: URL → Title Jaccard → Vector ANN → GPT-4o
2. **`@Transactional` 범위 분해**: 외부 API 호출 동안 DB 커넥션 미점유
3. **`@Retryable` + `@Recover` Fallback**: Exponential Backoff + 모델 폴백
4. **모델 외부화**: gpt-4o (분석) / gpt-4o-mini (단순 작업) 분리
5. **보안 강화**: Score API permitAll 제거, CORS 환경변수화
6. **설정 외부화**: 임계치·윈도우·타임아웃 `application.properties` 이관
7. **Redis CacheManager**: 분산 캐시 + MD5 키 프롬프트 캐싱
8. **CompletableFuture 병렬 처리**: 키워드별 동시 처리
9. **자가 발견 추가**: FakeKeywordEmbedding 차원 불일치 수정, 4개 부서 키워드 누락 수정, 점수 범위 통일

---

## 📊 데이터베이스 주요 엔티티
User · Article · ArticleAnalysis · ArticleEmbedding · Keyword · ArticleFeedback · ArticleScore

---

## 🚀 개발 환경 설정

### 백엔드
```bash
cd insk-backend/backend
./gradlew bootRun
```

### 프론트엔드
```bash
cd insk-frontend
npm install
npm run dev
```

---

## 📖 관련 블로그 글 (velog.io/@gm-15)

- [INSK 개발편 1 — 프로토타입을 버리고 처음으로 서비스를 만들겠다고 결정한 순간](https://velog.io/@gm-15/INSK-%EA%B0%9C%EB%B0%9C%ED%8E%B81%ED%94%84%EB%A1%9C%ED%86%A0%ED%83%80%EC%9E%85%EC%9D%84-%EB%B2%84%EB%A6%AC%EA%B3%A0-%EC%B2%98%EC%9D%8C%EC%9C%BC%EB%A1%9C-%EC%84%9C%EB%B9%84%EC%8A%A4%EB%A5%BC-%EB%A7%8C%EB%93%A4%EA%B2%A0%EB%8B%A4%EA%B3%A0-%EA%B2%B0%EC%A0%95%ED%95%9C-%EC%88%9C%EA%B0%84)
- [INSK 개발편 2 — 사용자 중심 v3.0 재설계](https://velog.io/@gm-15/INSK-%EA%B0%9C%EB%B0%9C%ED%8E%B8-2)
- [INSK 배포편 — 무엇을 당연하다고 가정하고 배포했는가](https://velog.io/@gm-15/INSK-%EB%B0%B0%ED%8F%AC%ED%8E%B8)
- [INSK 트러블슈팅편 — 5가지 운영 문제와 그것이 드러낸 설계의 한계](https://velog.io/@gm-15/INSK-%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85%ED%8E%B8)

---

## 👤 작성자

**박건우 (gm-15)** — 상명대학교 소프트웨어학과
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)

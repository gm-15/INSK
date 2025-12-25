# INSK v3.0 Frontend

INSK v3.0 뉴스 트렌드 센싱 자동화 플랫폼 프론트엔드

## 기술 스택

- **Framework**: Next.js 15.5.4
- **Language**: TypeScript
- **Styling**: Tailwind CSS 4
- **HTTP Client**: Axios

## 시작하기

### 1. 의존성 설치

```bash
npm install
```

### 2. 환경 변수 설정

프로젝트 루트에 `.env.local` 파일을 생성하고 다음 내용을 추가하세요:

```env
# 백엔드 API Base URL
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

**주의**: 프로덕션 환경에서는 실제 배포된 백엔드 URL로 변경하세요.

### 3. 개발 서버 실행

```bash
npm run dev
```

브라우저에서 [http://localhost:3000](http://localhost:3000)을 열어 확인하세요.

## 프로젝트 구조

```
src/
├── app/              # Next.js App Router 페이지
├── lib/
│   ├── api/         # API 클라이언트
│   │   ├── client.ts      # Axios 인스턴스 설정
│   │   ├── auth.ts        # 인증 API
│   │   ├── articles.ts    # 기사 API
│   │   ├── keywords.ts    # 키워드 API
│   │   └── feedback.ts    # 피드백 API
│   └── auth.ts      # 인증 유틸리티 (토큰 관리)
└── types/
    └── index.ts     # TypeScript 타입 정의
```

## 백엔드 API 연동

프론트엔드는 백엔드 API와 완전히 연동 가능하도록 구성되어 있습니다.

### 주요 기능

- ✅ **인증**: 로그인, 회원가입, JWT 토큰 관리
- ✅ **기사**: 목록 조회, 상세 조회, 점수 조회/업데이트, 필터링 (카테고리/출처)
- ✅ **피드백**: 좋아요/싫어요, 텍스트 피드백, 피드백 요약 조회
- ✅ **키워드**: 조회, 생성, 삭제, AI 추천, 승인/거부
- ✅ **부서별 Top5**: 부서별 인기 기사 조회 (10개 부서 지원)
- ✅ **PDF 다운로드**: 기사 PDF 리포트 다운로드
- ✅ **뉴스 파이프라인**: 수동 실행 기능

## 구현된 페이지

- `/` - 기사 목록 (메인 페이지)
- `/login` - 로그인
- `/signup` - 회원가입
- `/articles/[id]` - 기사 상세 (피드백 포함)
- `/keywords` - 키워드 관리 (인증 필요)
- `/departments` - 부서별 Top5 기사

### API 사용 예시

```typescript
import { login } from "@/lib/api/auth";
import { getArticles } from "@/lib/api/articles";
import { getApprovedKeywords } from "@/lib/api/keywords";

// 로그인
const token = await login({ email: "user@example.com", password: "password" });

// 기사 목록 조회
const articles = await getArticles({ page: 0, size: 10 });

// 승인된 키워드 조회
const keywords = await getApprovedKeywords();
```

## 빌드

```bash
npm run build
npm start
```

## 배포

Vercel, Netlify 등 Next.js를 지원하는 플랫폼에 배포할 수 있습니다.

**중요**: 배포 시 `.env.local`의 `NEXT_PUBLIC_API_BASE_URL`을 프로덕션 백엔드 URL로 설정하세요.

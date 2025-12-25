# INSK v3.0 백엔드 API 연동 가이드

## 📋 개요

이 문서는 프론트엔드에서 백엔드 API를 사용하는 방법을 정리한 가이드입니다.

## 🔗 API Base URL

- **로컬 개발**: `http://localhost:8080`
- **프로덕션**: 환경 변수 `NEXT_PUBLIC_API_BASE_URL`로 설정

## 🔐 인증 API

### 로그인
```typescript
import { login } from "@/lib/api/auth";

const response = await login({
  email: "user@example.com",
  password: "password123"
});
// response.accessToken 자동으로 localStorage에 저장됨
```

### 회원가입
```typescript
import { signUp } from "@/lib/api/auth";

const response = await signUp({
  email: "user@example.com",
  password: "password123",
  department: "T_CLOUD"
});
```

### 로그아웃
```typescript
import { logout } from "@/lib/api/auth";

logout(); // 토큰 제거
```

### 부서 변경
```typescript
import { updateDepartment } from "@/lib/api/auth";

await updateDepartment("T_AI_SERVICE");
```

---

## 📰 기사 API

### 기사 목록 조회
```typescript
import { getArticles } from "@/lib/api/articles";

// 전체 기사
const articles = await getArticles();

// 필터링 (카테고리, 소스)
const filtered = await getArticles({
  category: "LLM",
  source: "Naver",
  page: 0,
  size: 20,
  sort: "publishedAt,desc"
});
```

### 기사 상세 조회
```typescript
import { getArticleDetail } from "@/lib/api/articles";

const article = await getArticleDetail(123);
```

### 기사 점수 조회
```typescript
import { getArticleScore } from "@/lib/api/articles";

const score = await getArticleScore(123);
// { articleId, score, likeCount, dislikeCount, textRelevanceScore, viewCount }
```

### 기사 점수 업데이트
```typescript
import { updateArticleScore } from "@/lib/api/articles";

const updatedScore = await updateArticleScore(123);
```

### 부서별 Top5 기사
```typescript
import { getTop5ByDepartment } from "@/lib/api/articles";

const top5 = await getTop5ByDepartment("T_CLOUD");
```

### PDF 다운로드
```typescript
import { downloadArticlePdf } from "@/lib/api/articles";

await downloadArticlePdf(123);
// 자동으로 파일 다운로드 시작
```

### 뉴스 파이프라인 수동 실행
```typescript
import { runPipeline } from "@/lib/api/articles";

const message = await runPipeline();
// "뉴스 파이프라인 실행 시작"
```

---

## 👍 피드백 API

### 피드백 생성
```typescript
import { createFeedback } from "@/lib/api/feedback";

// 좋아요
await createFeedback(123, { liked: true });

// 싫어요
await createFeedback(123, { liked: false });

// 텍스트 피드백
await createFeedback(123, {
  liked: null,
  feedbackText: "유용한 정보입니다."
});
```

### 피드백 목록 조회
```typescript
import { getFeedbacks } from "@/lib/api/feedback";

const feedbacks = await getFeedbacks(123);
```

### 피드백 요약 조회
```typescript
import { getFeedbackSummary } from "@/lib/api/feedback";

const summary = await getFeedbackSummary(123);
// { articleId, likes, dislikes, recentComments, myFeedback }
```

---

## 🏷️ 키워드 API

### 승인된 키워드 조회
```typescript
import { getApprovedKeywords } from "@/lib/api/keywords";

const keywords = await getApprovedKeywords();
```

### 전체 키워드 조회
```typescript
import { getAllKeywords } from "@/lib/api/keywords";

const allKeywords = await getAllKeywords();
```

### 키워드 생성
```typescript
import { createKeyword } from "@/lib/api/keywords";

const keyword = await createKeyword({
  keyword: "클라우드 네이티브"
});
```

### 키워드 삭제
```typescript
import { deleteKeyword } from "@/lib/api/keywords";

await deleteKeyword(123);
```

### 키워드 추천
```typescript
import { recommendKeywords } from "@/lib/api/keywords";

const recommendations = await recommendKeywords({
  department: "T_CLOUD",
  limit: 10
});
// { recommended: [{ keyword, category }, ...] }
```

### 추천 키워드 승인
```typescript
import { approveKeyword } from "@/lib/api/keywords";

await approveKeyword({
  keyword: "클라우드 네이티브",
  category: "INFRA"
});
```

### 키워드 거부
```typescript
import { rejectKeyword } from "@/lib/api/keywords";

await rejectKeyword({
  keyword: "불필요한 키워드"
});
```

---

## 🔧 인증 상태 관리

### 토큰 확인
```typescript
import { isAuthenticated, getToken } from "@/lib/auth";

if (isAuthenticated()) {
  const token = getToken();
  // 토큰 사용
}
```

### 사용자 정보 추출
```typescript
import { getUserFromToken } from "@/lib/auth";

const user = getUserFromToken();
// { email: "user@example.com" } 또는 null
```

---

## ⚠️ 에러 처리

모든 API 호출은 try-catch로 감싸서 에러를 처리하세요:

```typescript
try {
  const articles = await getArticles();
} catch (error: any) {
  console.error("에러 발생:", error.message);
  // error.status, error.data도 사용 가능
}
```

**에러 응답 구조:**
```typescript
{
  message: string;    // 에러 메시지
  status?: number;    // HTTP 상태 코드
  data?: any;         // 백엔드 에러 응답 데이터
}
```

---

## 📝 타입 정의

모든 타입은 `src/types/index.ts`에 정의되어 있습니다:

```typescript
import type {
  ArticleResponse,
  ArticleDetailResponse,
  KeywordResponse,
  ArticleFeedbackResponse,
  // ... 기타 타입들
} from "@/types";
```

---

## 🚀 사용 예시 (React 컴포넌트)

```typescript
"use client";

import { useState, useEffect } from "react";
import { getArticles } from "@/lib/api/articles";
import type { ArticleResponse } from "@/types";

export default function ArticleList() {
  const [articles, setArticles] = useState<ArticleResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchArticles = async () => {
      try {
        const response = await getArticles({ page: 0, size: 10 });
        setArticles(response.content);
      } catch (error: any) {
        console.error("기사 조회 실패:", error.message);
      } finally {
        setLoading(false);
      }
    };

    fetchArticles();
  }, []);

  if (loading) return <div>로딩 중...</div>;

  return (
    <div>
      {articles.map((article) => (
        <div key={article.articleId}>
          <h2>{article.title}</h2>
          <p>{article.summary}</p>
        </div>
      ))}
    </div>
  );
}
```

---

## ✅ 연동 확인 체크리스트

- [ ] `.env.local` 파일 생성 및 `NEXT_PUBLIC_API_BASE_URL` 설정
- [ ] `npm install` 실행 (axios 설치 확인)
- [ ] 백엔드 서버 실행 확인 (`http://localhost:8080`)
- [ ] CORS 설정 확인 (백엔드 `SecurityConfig`에서 `http://localhost:3000` 허용)
- [ ] 로그인 API 테스트
- [ ] 기사 목록 조회 API 테스트
- [ ] JWT 토큰이 자동으로 헤더에 추가되는지 확인

---

## 🔍 문제 해결

### CORS 에러
백엔드 `SecurityConfig.java`에서 프론트엔드 URL이 허용되어 있는지 확인:
```java
configuration.setAllowedOrigins(List.of("http://localhost:3000"));
```

### 401 Unauthorized
- JWT 토큰이 localStorage에 저장되어 있는지 확인
- 토큰이 만료되었는지 확인 (재로그인 필요)

### 네트워크 에러
- 백엔드 서버가 실행 중인지 확인
- `NEXT_PUBLIC_API_BASE_URL`이 올바른지 확인


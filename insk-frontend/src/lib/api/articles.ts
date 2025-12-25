// ============================================
// 기사 API
// ============================================

import apiClient from "./client";
import type {
  ArticleResponse,
  ArticleDetailResponse,
  ArticlePageResponse,
  ArticleSimpleResponse,
  ArticleScoreResponse,
  Pageable,
} from "@/types";

/**
 * 기사 목록 조회
 */
export const getArticles = async (
  params?: {
    category?: string;
    source?: string;
    page?: number;
    size?: number;
    sort?: string;
  }
): Promise<ArticlePageResponse> => {
  const response = await apiClient.get<ArticlePageResponse>("/articles", {
    params,
  });
  return response.data;
};

/**
 * 기사 상세 조회
 */
export const getArticleDetail = async (
  articleId: number
): Promise<ArticleDetailResponse> => {
  const response = await apiClient.get<ArticleDetailResponse>(
    `/articles/${articleId}`
  );
  return response.data;
};

/**
 * 뉴스 파이프라인 수동 실행
 */
export const runPipeline = async (): Promise<string> => {
  const response = await apiClient.post<string>("/articles/run-pipeline");
  return response.data;
};

/**
 * 기사 점수 조회
 */
export const getArticleScore = async (
  articleId: number
): Promise<ArticleScoreResponse> => {
  const response = await apiClient.get<ArticleScoreResponse>(
    `/articles/${articleId}/score`
  );
  return response.data;
};

/**
 * 기사 점수 업데이트
 */
export const updateArticleScore = async (
  articleId: number
): Promise<ArticleScoreResponse> => {
  const response = await apiClient.post<ArticleScoreResponse>(
    `/articles/${articleId}/score/update`
  );
  return response.data;
};

/**
 * 부서별 Top5 기사 조회
 */
export const getTop5ByDepartment = async (
  department: string
): Promise<ArticleSimpleResponse[]> => {
  const response = await apiClient.get<ArticleSimpleResponse[]>(
    `/articles/top5/${department}`
  );
  return response.data;
};

/**
 * PDF 다운로드
 */
export const downloadArticlePdf = async (articleId: number): Promise<void> => {
  const response = await apiClient.get(`/articles/${articleId}/pdf`, {
    responseType: "blob",
  });

  // Blob을 다운로드 링크로 변환
  const url = window.URL.createObjectURL(new Blob([response.data]));
  const link = document.createElement("a");
  link.href = url;
  link.setAttribute("download", `article_${articleId}.pdf`);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

/**
 * 관심기사 조회 (좋아요한 기사)
 */
export const getFavoriteArticles = async (
  params?: {
    page?: number;
    size?: number;
    sort?: string;
  }
): Promise<ArticlePageResponse> => {
  const response = await apiClient.get<ArticlePageResponse>("/articles/favorites", {
    params,
  });
  return response.data;
};


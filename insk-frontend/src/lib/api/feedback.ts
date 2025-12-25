// ============================================
// 피드백 API
// ============================================

import apiClient from "./client";
import type {
  ArticleFeedbackCreateRequest,
  ArticleFeedbackResponse,
  ArticleFeedbackSummaryResponse,
} from "@/types";

/**
 * 피드백 생성
 */
export const createFeedback = async (
  articleId: number,
  request: ArticleFeedbackCreateRequest
): Promise<ArticleFeedbackResponse> => {
  const response = await apiClient.post<ArticleFeedbackResponse>(
    `/articles/${articleId}/feedbacks`,
    request
  );
  return response.data;
};

/**
 * 피드백 목록 조회
 */
export const getFeedbacks = async (
  articleId: number
): Promise<ArticleFeedbackResponse[]> => {
  const response = await apiClient.get<ArticleFeedbackResponse[]>(
    `/articles/${articleId}/feedbacks`
  );
  return response.data;
};

/**
 * 피드백 요약 조회 (좋아요/싫어요 수, 내 피드백 포함)
 */
export const getFeedbackSummary = async (
  articleId: number
): Promise<ArticleFeedbackSummaryResponse> => {
  const response = await apiClient.get<ArticleFeedbackSummaryResponse>(
    `/articles/${articleId}/feedbacks/summary`
  );
  return response.data;
};


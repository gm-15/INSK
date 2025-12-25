// ============================================
// 키워드 API
// ============================================

import apiClient from "./client";
import type {
  KeywordResponse,
  KeywordCreateRequest,
  KeywordRecommendRequest,
  KeywordRecommendResponse,
  KeywordApproveRequest,
  KeywordRejectRequest,
} from "@/types";

/**
 * 승인된 키워드 조회
 */
export const getApprovedKeywords = async (): Promise<KeywordResponse[]> => {
  const response = await apiClient.get<KeywordResponse[]>("/keywords/approved");
  return response.data;
};

/**
 * 전체 키워드 조회
 */
export const getAllKeywords = async (): Promise<KeywordResponse[]> => {
  const response = await apiClient.get<KeywordResponse[]>("/keywords");
  return response.data;
};

/**
 * 키워드 생성
 */
export const createKeyword = async (
  request: KeywordCreateRequest
): Promise<KeywordResponse> => {
  const response = await apiClient.post<KeywordResponse>(
    "/keywords",
    request
  );
  return response.data;
};

/**
 * 키워드 삭제
 */
export const deleteKeyword = async (keywordId: number): Promise<void> => {
  await apiClient.delete(`/keywords/${keywordId}`);
};

/**
 * 키워드 추천
 */
export const recommendKeywords = async (
  request: KeywordRecommendRequest
): Promise<KeywordRecommendResponse> => {
  const response = await apiClient.post<KeywordRecommendResponse>(
    "/keywords/recommend",
    request
  );
  return response.data;
};

/**
 * 추천 키워드 승인
 */
export const approveKeyword = async (
  request: KeywordApproveRequest
): Promise<void> => {
  await apiClient.post("/keywords/approve", request);
};

/**
 * 키워드 거부
 */
export const rejectKeyword = async (
  request: KeywordRejectRequest
): Promise<void> => {
  await apiClient.post("/keywords/reject", request);
};

/**
 * 관리용 승인된 키워드 조회
 */
export const getManagedApprovedKeywords = async (): Promise<
  KeywordResponse[]
> => {
  const response = await apiClient.get<KeywordResponse[]>(
    "/keywords/manage/approved"
  );
  return response.data;
};

/**
 * 다른 사용자가 추가한 키워드 조회 (중복 제거 및 카운트 포함)
 */
export interface OtherUsersKeywordResponse {
  keyword: string;
  approved: boolean;
  count: number;
}

export const getOtherUsersKeywords = async (): Promise<OtherUsersKeywordResponse[]> => {
  const response = await apiClient.get<OtherUsersKeywordResponse[]>("/keywords/others");
  return response.data;
};


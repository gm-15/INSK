// ============================================
// 인증 API
// ============================================

import apiClient from "./client";
import type {
  LoginRequest,
  LoginResponse,
  SignUpRequest,
  SignUpResponse,
  ForgotPasswordRequest,
  ForgotPasswordResponse,
  ResetPasswordRequest,
  ResetPasswordResponse,
} from "@/types";
import { setToken, removeToken } from "@/lib/auth";

/**
 * 로그인
 */
export const login = async (
  request: LoginRequest
): Promise<LoginResponse> => {
  const response = await apiClient.post<{ accessToken: string }>(
    "/auth/login",
    request
  );

  const token = response.data.accessToken;
  setToken(token);

  return { accessToken: token };
};

/**
 * 회원가입
 */
export const signUp = async (
  request: SignUpRequest
): Promise<SignUpResponse> => {
  const response = await apiClient.post<SignUpResponse>(
    "/auth/signup",
    request
  );
  return response.data;
};

/**
 * 로그아웃
 */
export const logout = (): void => {
  removeToken();
};

/**
 * 부서 변경
 */
export const updateDepartment = async (
  department: string
): Promise<void> => {
  await apiClient.put("/auth/me/department", { department });
};

/**
 * 비밀번호 찾기
 */
export const forgotPassword = async (
  request: ForgotPasswordRequest
): Promise<ForgotPasswordResponse> => {
  const response = await apiClient.post<ForgotPasswordResponse>(
    "/auth/forgot-password",
    request
  );
  return response.data;
};

/**
 * 비밀번호 재설정
 */
export const resetPassword = async (
  request: ResetPasswordRequest
): Promise<ResetPasswordResponse> => {
  const response = await apiClient.post<ResetPasswordResponse>(
    "/auth/reset-password",
    request
  );
  return response.data;
};


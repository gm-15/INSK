// 인증 관련 타입
export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
}

export interface SignUpRequest {
  email: string;
  password: string;
  department: DepartmentType;
}

export interface SignUpResponse {
  userId: number;
  email: string;
  department: DepartmentType;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ForgotPasswordResponse {
  resetToken: string;
  message: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface ResetPasswordResponse {
  message: string;
}

// 부서 타입
export type DepartmentType =
  | "T_CLOUD"
  | "T_NETWORK_INFRA"
  | "T_HR"
  | "T_AI_SERVICE"
  | "T_MARKETING"
  | "T_STRATEGY"
  | "T_ENTERPRISE_B2B"
  | "T_PLATFORM_DEV"
  | "T_TELCO_MNO"
  | "T_FINANCE";

// 기사 관련 타입
export interface ArticleSimpleResponse {
  articleId: number;
  title: string;
  url: string;
  score: number;
  source?: string;
  publishedAt?: string;
}

// 키워드 관련 타입
export interface KeywordResponse {
  keywordId: number;
  keyword: string;
  approved: boolean;
  category?: string;
}

export interface OtherUsersKeywordResponse {
  keyword: string;
  approved: boolean;
  count: number;
}

export interface KeywordRecommendResponse {
  keywords: string[];
}


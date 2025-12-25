// ============================================
// API Client Configuration
// ============================================

import axios, { AxiosInstance, AxiosRequestConfig } from "axios";

// API Base URL (환경 변수에서 가져오거나 기본값 사용)
const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

// Axios 인스턴스 생성
const apiClient: AxiosInstance = axios.create({
  baseURL: `${API_BASE_URL}/api/v1`,
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 30000, // 30초
});

// Request Interceptor: JWT 토큰 자동 추가
apiClient.interceptors.request.use(
  (config) => {
    // 클라이언트 사이드에서만 실행
    if (typeof window !== "undefined") {
      const token = localStorage.getItem("accessToken");
      if (token && config.headers) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: 에러 처리
apiClient.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    // 401 Unauthorized: 토큰 만료 또는 인증 실패
    // 403 Forbidden: 인증은 되었지만 권한 없음 (토큰이 유효하지 않거나 사용자가 없음)
    if (error.response?.status === 401 || error.response?.status === 403) {
      if (typeof window !== "undefined") {
        localStorage.removeItem("accessToken");
        // 로그인 페이지로 리다이렉트 (필요시)
        // window.location.href = "/login";
      }
    }

    // Network Error 처리 (서버 연결 실패)
    if (error.code === "ECONNABORTED" || error.code === "ERR_NETWORK" || error.message === "Network Error") {
      return Promise.reject({
        message: "서버에 연결할 수 없습니다. 백엔드 서버가 실행 중인지 확인해주세요.",
        status: null,
        data: null,
        isNetworkError: true,
      });
    }

    // 에러 응답 구조화
    let errorMessage = "알 수 없는 오류가 발생했습니다.";
    
    if (error.response?.data) {
      // 백엔드에서 반환한 에러 메시지 추출
      if (typeof error.response.data === 'string') {
        errorMessage = error.response.data;
      } else if (error.response.data.message) {
        errorMessage = error.response.data.message;
      } else if (error.response.data.error) {
        errorMessage = error.response.data.error;
      }
    } else if (error.message) {
      errorMessage = error.message;
    }

    return Promise.reject({
      message: errorMessage,
      status: error.response?.status,
      data: error.response?.data,
    });
  }
);

export default apiClient;


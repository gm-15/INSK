// ============================================
// 인증 관리 유틸리티
// ============================================

const TOKEN_KEY = "accessToken";

/**
 * JWT 토큰 저장
 */
export const setToken = (token: string): void => {
  if (typeof window !== "undefined") {
    localStorage.setItem(TOKEN_KEY, token);
    // 토큰 저장 시 커스텀 이벤트 발생 (Header 컴포넌트가 인증 상태 업데이트)
    window.dispatchEvent(new Event('auth-change'));
  }
};

/**
 * JWT 토큰 조회
 */
export const getToken = (): string | null => {
  if (typeof window !== "undefined") {
    return localStorage.getItem(TOKEN_KEY);
  }
  return null;
};

/**
 * JWT 토큰 삭제
 */
export const removeToken = (): void => {
  if (typeof window !== "undefined") {
    localStorage.removeItem(TOKEN_KEY);
    // 토큰 삭제 시 커스텀 이벤트 발생 (Header 컴포넌트가 인증 상태 업데이트)
    window.dispatchEvent(new Event('auth-change'));
  }
};

/**
 * 로그인 상태 확인
 */
export const isAuthenticated = (): boolean => {
  return getToken() !== null;
};

/**
 * 로그아웃 (토큰 삭제)
 */
export const logout = (): void => {
  removeToken();
};

/**
 * 앱 시작 시 초기화 (토큰 삭제하여 비로그인 상태로 시작)
 */
export const initializeApp = (): void => {
  if (typeof window !== "undefined") {
    // 앱 시작 시 항상 비로그인 상태로 시작
    removeToken();
  }
};

/**
 * 토큰에서 사용자 정보 추출 (선택적, JWT 디코딩 필요시)
 */
export const getUserFromToken = (): { email?: string } | null => {
  const token = getToken();
  if (!token) return null;

  try {
    // JWT는 base64로 인코딩된 3부분으로 구성: header.payload.signature
    const payload = token.split(".")[1];
    if (!payload) return null;

    const decoded = JSON.parse(atob(payload));
    return {
      email: decoded.sub || decoded.email,
    };
  } catch (error) {
    console.error("토큰 디코딩 실패:", error);
    return null;
  }
};


"use client";

import { useEffect } from "react";

/**
 * 앱 시작 시 초기화를 담당하는 컴포넌트
 * - 현재는 비어있지만 필요시 초기화 로직 추가 가능
 */
export default function AppInitializer() {
  useEffect(() => {
    // 앱 시작 시 초기화 로직 (필요시 추가)
    // 로그인 상태는 Header 컴포넌트에서 관리
  }, []);

  return null; // UI 렌더링 없음
}


"use client";

import Link from "next/link";
import { useRouter, usePathname } from "next/navigation";
import { isAuthenticated, logout } from "@/lib/auth";
import { useEffect, useState } from "react";

export default function Header() {
  const router = useRouter();
  const pathname = usePathname();
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    // 초기 로드 시 인증 상태 확인
    const checkAuth = () => {
      setAuthenticated(isAuthenticated());
    };
    checkAuth();
    
    // localStorage 변경 감지 (토큰 추가/삭제 시)
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === 'accessToken') {
        checkAuth();
      }
    };
    window.addEventListener('storage', handleStorageChange);
    
    // 커스텀 이벤트 감지 (같은 탭에서 토큰 변경 시)
    const handleAuthChange = () => {
      checkAuth();
    };
    window.addEventListener('auth-change', handleAuthChange);
    
    // 페이지 포커스 시 인증 상태 확인
    const handleFocus = checkAuth;
    window.addEventListener('focus', handleFocus);
    
    return () => {
      window.removeEventListener('storage', handleStorageChange);
      window.removeEventListener('auth-change', handleAuthChange);
      window.removeEventListener('focus', handleFocus);
    };
  }, []);
  
  // pathname 변경 시 인증 상태 확인
  useEffect(() => {
    setAuthenticated(isAuthenticated());
  }, [pathname]);

  const handleLogout = () => {
    logout();
    setAuthenticated(false);
    router.push("/");
    router.refresh();
  };

  return (
    <header className="w-full border-b border-gray-200 bg-white shadow-sm">
      <div className="container mx-auto px-4 py-4">
        <div className="flex items-center justify-between">
          <Link href="/" className="text-2xl font-bold text-[#ff6b35]">
            INSK v3.0
          </Link>
          
          <nav className="flex items-center gap-6">
            <Link href="/" className="text-gray-700 hover:text-[#ff6b35] transition-colors">
              기사 목록
            </Link>
            <Link href="/keywords" className="text-gray-700 hover:text-[#ff6b35] transition-colors">
              키워드 관리
            </Link>
            <Link href="/departments" className="text-gray-700 hover:text-[#ff6b35] transition-colors">
              부서별 Top5
            </Link>
            {authenticated && (
              <Link href="/favorites" className="text-gray-700 hover:text-[#ff6b35] transition-colors">
                관심기사
              </Link>
            )}
            
            {authenticated ? (
              <button
                onClick={handleLogout}
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors font-medium"
              >
                로그아웃
              </button>
            ) : (
              <div className="flex gap-2">
                <Link
                  href="/login"
                  className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors font-medium"
                >
                  로그인
                </Link>
                <Link
                  href="/signup"
                  className="px-4 py-2 bg-[#ff6b35] text-white rounded-lg hover:bg-[#e55a2b] transition-colors font-medium"
                >
                  회원가입
                </Link>
              </div>
            )}
          </nav>
        </div>
      </div>
    </header>
  );
}


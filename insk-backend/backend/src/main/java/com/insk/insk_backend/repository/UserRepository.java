package com.insk.insk_backend.repository;

import com.insk.insk_backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // JpaRepository를 상속받는 것만으로 기본적인 CRUD가 자동으로 구현됩니다.

    // 이메일로 사용자가 존재하는지 확인하는 메소드
    boolean existsByEmail(String email);

    // 이메일로 사용자를 찾는 메소드 (로그인 시 사용 예정)
    Optional<User> findByEmail(String email);

    // resetToken으로 사용자를 찾는 메소드
    Optional<User> findByResetToken(String resetToken);

}

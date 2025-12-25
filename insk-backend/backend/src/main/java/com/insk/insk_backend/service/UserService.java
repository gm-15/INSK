package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.User;
import com.insk.insk_backend.dto.UserDto;
import com.insk.insk_backend.jwt.JwtTokenProvider;
import com.insk.insk_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public UserDto.SignUpResponse signup(UserDto.SignUpRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User newUser = User.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .department(request.getDepartment())
                .build();

        User savedUser = userRepository.save(newUser);

        return new UserDto.SignUpResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getDepartment()
        );
    }

    @Transactional(readOnly = true)
    public UserDto.LoginResponse login(UserDto.LoginRequest requestDto) {
        try {
            User user = userRepository.findByEmail(requestDto.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

            if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
                throw new IllegalArgumentException("잘못된 비밀번호입니다.");
            }

            String token = jwtTokenProvider.createToken(user.getEmail());
            return new UserDto.LoginResponse(token);
        } catch (Exception e) {
            log.error("로그인 처리 중 예외 발생!", e);
            throw e;
        }
    }

    @Transactional
    public void updateDepartment(Long userId, UserDto.DepartmentUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다: " + userId));

        user.changeDepartment(request.getDepartment());
    }

    @Transactional
    public UserDto.ForgotPasswordResponse forgotPassword(UserDto.ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        // 재설정 토큰 생성
        String resetToken = generateResetToken();
        LocalDateTime expiry = LocalDateTime.now().plusHours(1); // 1시간 유효

        user.setResetToken(resetToken, expiry);
        userRepository.save(user);

        log.info("비밀번호 재설정 토큰 생성: email={}, token={}", user.getEmail(), resetToken);

        return new UserDto.ForgotPasswordResponse(
                resetToken,
                "비밀번호 재설정 토큰이 생성되었습니다. 이 토큰을 사용하여 비밀번호를 재설정하세요."
        );
    }

    @Transactional
    public UserDto.ResetPasswordResponse resetPassword(UserDto.ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 토큰입니다."));

        // 토큰 유효성 검사
        if (!user.isResetTokenValid() || !user.getResetToken().equals(request.getToken())) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 토큰입니다.");
        }

        // 비밀번호 업데이트
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.changePassword(encodedPassword);
        user.clearResetToken();
        userRepository.save(user);

        log.info("비밀번호 재설정 완료: email={}", user.getEmail());

        return new UserDto.ResetPasswordResponse("비밀번호가 성공적으로 재설정되었습니다.");
    }

    private String generateResetToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

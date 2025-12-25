package com.insk.insk_backend.controller;

import com.insk.insk_backend.domain.User;
import com.insk.insk_backend.dto.UserDto;
import com.insk.insk_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<UserDto.SignUpResponse> signup(
            @Valid @RequestBody UserDto.SignUpRequest request
    ) {
        try {
            return ResponseEntity.ok(userService.signup(request));
        } catch (IllegalArgumentException e) {
            // 이미 사용 중인 이메일인 경우
            throw new IllegalArgumentException(e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("회원가입 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PutMapping("/me/department")
    public ResponseEntity<Void> updateDepartment(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UserDto.DepartmentUpdateRequest request
    ) {
        userService.updateDepartment(user.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<UserDto.ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody UserDto.ForgotPasswordRequest request
    ) {
        try {
            return ResponseEntity.ok(userService.forgotPassword(request));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("비밀번호 찾기 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<UserDto.ResetPasswordResponse> resetPassword(
            @Valid @RequestBody UserDto.ResetPasswordRequest request
    ) {
        try {
            return ResponseEntity.ok(userService.resetPassword(request));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("비밀번호 재설정 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}

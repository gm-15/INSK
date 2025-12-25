package com.insk.insk_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.insk.insk_backend.domain.DepartmentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

public class UserDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)  // 👈 name 등 불필요한 필드 무시
    public static class SignUpRequest {

        @Email
        @NotBlank
        private String email;

        @NotBlank
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        private String password;

        @NotNull
        private DepartmentType department;
    }

    @Getter
    @AllArgsConstructor
    public static class SignUpResponse {
        private Long userId;
        private String email;
        private DepartmentType department;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true) // 👈 login에도 잘못된 필드 들어오면 무시
    public static class LoginRequest {

        @Email
        @NotBlank
        private String email;

        @NotBlank
        private String password;
    }

    @Getter
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true) // 👈 부서 변경에도 적용
    public static class DepartmentUpdateRequest {

        @NotNull
        private DepartmentType department;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ForgotPasswordRequest {

        @Email
        @NotBlank
        private String email;
    }

    @Getter
    @AllArgsConstructor
    public static class ForgotPasswordResponse {
        private String resetToken;
        private String message;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResetPasswordRequest {

        @NotBlank
        private String token;

        @NotBlank
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        private String newPassword;
    }

    @Getter
    @AllArgsConstructor
    public static class ResetPasswordResponse {
        private String message;
    }
}

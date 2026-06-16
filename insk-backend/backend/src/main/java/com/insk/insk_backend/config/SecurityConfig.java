package com.insk.insk_backend.config;

import com.insk.insk_backend.jwt.JwtAuthenticationFilter;
import com.insk.insk_backend.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    // 멘토 #7: CORS 허용 출처 외부화 (배포 환경별로 프로퍼티/환경변수로 주입). 기본값은 로컬 개발 서버.
    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /** ▣ PasswordEncoder */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** ▣ DaoAuthenticationProvider 설정 */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /** ▣ AuthenticationManager */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /** 쉼표로 구분된 cors.allowed-origins 문자열을 trim 후 리스트로 변환. */
    private List<String> parseAllowedOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    /** ▣ CORS 설정 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /** ▣ Security 필터 체인 */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // CSRF off
                .csrf(csrf -> csrf.disable())

                // CORS on
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // AuthenticationProvider 설정
                .authenticationProvider(authenticationProvider())

                // 세션 미사용 (JWT 기반)
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 기본 로그인 비활성화
                .httpBasic(h -> h.disable())
                .formLogin(f -> f.disable())

                // URL 접근 제어
                .authorizeHttpRequests(auth -> auth

                        // 인증 불필요 영역
                        .requestMatchers("/api/v1/auth/**", "/error").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()

                        // 기사 조회(GET)는 Public
                        .requestMatchers(HttpMethod.GET, "/api/v1/articles/**").permitAll()

                        // 멘토 #6: 점수 재계산 POST는 유료 임베딩을 호출하므로 인증 필수
                        // (기존 permitAll 매처는 실제 경로 '/score/update'와 어긋난 dead config라 제거).
                        // 점수 변경 엔드포인트는 아래 anyRequest().authenticated()로 보호된다.

                        // 키워드 관련은 인증 필요
                        .requestMatchers("/api/v1/keywords/**").authenticated()

                        // 그 외 전체 보호
                        .anyRequest().authenticated()
                )

                // JWT 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

package com.insk.insk_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // JavaTimeModule import
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean; // Bean import
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // 스케줄링 활성화
@EnableJpaAuditing // JPA Auditing 활성화 (생성/수정 시간 자동 기록)
@SpringBootApplication
@EnableCaching
public class InskBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(InskBackendApplication.class, args);
	}

	// ObjectMapper를 Spring Bean으로 등록.
	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();
		// Java 8 날짜/시간 타입(LocalDateTime 등)을 Jackson이 인식하도록 모듈 등록
		objectMapper.registerModule(new JavaTimeModule());
		// 필요한 다른 설정 추가 가능 (예: 알 수 없는 속성 무시)
		// objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return objectMapper;
	}
}


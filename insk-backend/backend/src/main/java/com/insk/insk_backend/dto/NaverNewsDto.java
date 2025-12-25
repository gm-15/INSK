package com.insk.insk_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NaverNewsDto {

    private String title;
    private String originallink; // 실제 원문 링크
    private String link;         // 네이버 뉴스 링크
    private String description;
    private String pubDate;      // 원본 날짜 문자열 (RFC1123 포맷)

    public String getOriginalUrl() {
        return this.originallink;
    }

    /**
     * pubDate(String) → LocalDateTime(KST) 변환
     */
    public LocalDateTime getPubDate() {
        if (this.pubDate == null) {
            return LocalDateTime.now();
        }

        try {
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

            ZonedDateTime zoned = ZonedDateTime.parse(this.pubDate, formatter);

            return zoned.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                    .toLocalDateTime();

        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /**
     * Naver API 최상위 응답 구조
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResponse {
        private List<NaverNewsDto> items;
    }
}

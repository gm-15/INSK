package com.insk.insk_backend.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AITimesDto {
    private String title;
    private String originalUrl;
    private String summary;
    private String pubDate;

    /**
     * RSS pubDate(String, RFC 1123) → LocalDateTime(KST) 변환.
     * 파싱 실패 시 null 반환 (호출부에서 now() fallback 적용 가능).
     */
    public LocalDateTime getPublishedAt() {
        if (this.pubDate == null || this.pubDate.isBlank()) {
            return null;
        }
        try {
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
            ZonedDateTime zoned = ZonedDateTime.parse(this.pubDate, formatter);
            return zoned.withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }
}

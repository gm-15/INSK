package com.insk.insk_backend.dto;

import lombok.*;

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
}

package com.insk.insk_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TheGuruDto {
    private String title;
    private String originalUrl;
    private String summary;
    private String pubDate;
}

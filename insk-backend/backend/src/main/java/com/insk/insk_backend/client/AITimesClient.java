package com.insk.insk_backend.client;

import com.insk.insk_backend.dto.AITimesDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AITimesClient {

    private static final String RSS_URL = "https://www.aitimes.com/rss/allArticle.xml";

    public List<AITimesDto> fetchNews(int limit) {

        List<AITimesDto> result = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(RSS_URL).get();

            for (Element item : doc.select("item")) {

                if (result.size() >= limit) break;

                AITimesDto dto = AITimesDto.builder()
                        .title(item.selectFirst("title").text())
                        .originalUrl(item.selectFirst("link").text())
                        .summary(item.selectFirst("description") != null
                                ? item.selectFirst("description").text()
                                : "")
                        .pubDate(item.selectFirst("pubDate") != null
                                ? item.selectFirst("pubDate").text()
                                : "")
                        .build();

                result.add(dto);
            }

        } catch (Exception e) {
            log.error("AITimes RSS 파싱 오류", e);
        }

        return result;
    }
}

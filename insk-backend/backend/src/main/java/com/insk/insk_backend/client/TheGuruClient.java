package com.insk.insk_backend.client;

import com.insk.insk_backend.dto.TheGuruDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TheGuruClient {

    private static final String RSS_URL = "http://www.theguru.co.kr/data/rss/news.xml";

    public List<TheGuruDto> fetchNews(int limit) {

        List<TheGuruDto> result = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(RSS_URL).get();

            for (Element item : doc.select("item")) {

                if (result.size() >= limit) break;

                TheGuruDto dto = TheGuruDto.builder()
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
            log.error("TheGuru RSS 파싱 오류", e);
        }

        return result;
    }
}

package com.insk.insk_backend.controller;

import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleAnalysis;
import com.insk.insk_backend.repository.ArticleAnalysisRepository;
import com.insk.insk_backend.repository.ArticleRepository;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class PdfController {

    private final ArticleRepository articleRepository;
    private final ArticleAnalysisRepository analysisRepository;

    @GetMapping("/{articleId}/pdf")
    public void downloadPdf(
            @PathVariable Long articleId,
            HttpServletResponse response
    ) throws IOException {

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("기사 없음"));

        ArticleAnalysis analysis = analysisRepository.findByArticle(article).orElse(null);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"article_" + articleId + ".pdf\"");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        // ⭐ iText 7.2.x 시그니처: 리소스 경로로 접근
        // 폰트 파일 위치: src/main/resources/fonts/NotoSansKR-Regular.ttf
        PdfFont font;
        try {
            // 리소스 경로로 접근
            java.io.InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSansKR-Regular.ttf");
            if (fontStream == null) {
                // 폰트 파일이 없으면 기본 폰트 사용
                font = PdfFontFactory.createFont();
            } else {
                byte[] fontBytes = fontStream.readAllBytes();
                font = PdfFontFactory.createFont(fontBytes, "Identity-H");
                fontStream.close();
            }
        } catch (Exception e) {
            // 폰트 로드 실패 시 기본 폰트 사용
            font = PdfFontFactory.createFont();
        }
        document.setFont(font);

        String title = clean(article.getTitle());
        String url = clean(article.getOriginalUrl());
        String publishedAt = article.getPublishedAt() != null
                ? article.getPublishedAt().toString()
                : "(발행일자 없음)";

        document.add(new Paragraph("제목").setBold());
        document.add(new Paragraph(title));
        document.add(new Paragraph("\nURL").setBold());
        document.add(new Paragraph(url));
        document.add(new Paragraph("\n발행일").setBold());
        document.add(new Paragraph(publishedAt));

        if (analysis != null) {
            document.add(new Paragraph("\n요약").setBold());
            document.add(new Paragraph(clean(analysis.getSummary())));
            document.add(new Paragraph("\n인사이트").setBold());
            document.add(new Paragraph(clean(analysis.getInsight())));
        } else {
            document.add(new Paragraph("\n(분석 데이터 없음)").setBold());
        }

        document.close();

        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
        response.getOutputStream().close();
    }

    private String clean(String v) {
        if (v == null) return "(없음)";
        return v.replaceAll("<[^>]+>", "").trim();
    }
}

package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.*;
import com.insk.insk_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ArticlePdfService {

    private final ArticleRepository articleRepository;
    private final ArticleAnalysisRepository analysisRepository;
    private final ArticleScoreRepository scoreRepository;
    private final ArticleFeedbackRepository feedbackRepository;
    private final UserArticleLogRepository userArticleLogRepository;

    private static final float MARGIN = 50f;
    private static final float BOTTOM_MARGIN = 60f;
    private static final float START_Y = 750f;
    private static final float LINE_HEIGHT = 16f;

    @Transactional(readOnly = true)
    public byte[] generateArticlePdf(Long articleId) throws Exception {

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("기사 없음"));

        ArticleAnalysis analysis = analysisRepository.findByArticle_ArticleId(articleId)
                .orElse(null);

        ArticleScore score = scoreRepository.findByArticle_ArticleId(articleId)
                .orElse(null);

        List<ArticleFeedback> feedbackList =
                feedbackRepository.findByArticle_ArticleId(articleId);

        List<String> recentFeedbacks = feedbackList.stream()
                .filter(f -> f.getFeedbackText() != null && !f.getFeedbackText().isBlank())
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .limit(5)
                .map(ArticleFeedback::getFeedbackText)
                .toList();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {

            InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSansCJK-Regular.otf");
            if (fontStream == null) {
                throw new IllegalStateException("폰트 파일을 찾을 수 없습니다: /fonts/NotoSansCJK-Regular.otf");
            }
            PDType0Font font = PDType0Font.load(document, fontStream);

            PdfContext ctx = new PdfContext(document, font);
            ctx.newPage();

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            writeWrapped(ctx, 18, "INSK 뉴스 분석 리포트");
            ctx.y -= 10;

            writeWrapped(ctx, 14, "제목: " + article.getTitle());
            writeWrapped(ctx, 12, "출처: " + Objects.toString(article.getSource(), "-"));
            writeWrapped(ctx, 12, "발행일: " + article.getPublishedAt().format(fmt));
            ctx.y -= 10;

            if (analysis != null) {
                writeWrapped(ctx, 12, "[AI 요약]");
                writeWrapped(ctx, 11, analysis.getSummary());
                ctx.y -= 5;

                writeWrapped(ctx, 12, "[AI 인사이트]");
                writeWrapped(ctx, 11, analysis.getInsight());
                ctx.y -= 5;

                if (analysis.getCategory() != null) {
                    writeWrapped(ctx, 11, "카테고리: " + analysis.getCategory());
                }
                if (analysis.getTags() != null) {
                    writeWrapped(ctx, 11, "태그: " + analysis.getTags());
                }
                ctx.y -= 10;
            }

            if (score != null) {
                writeWrapped(ctx, 12, "[종합 점수] : " + score.getScore());
                writeWrapped(ctx, 11, "좋아요: " + score.getLikeCount()
                        + ", 싫어요: " + score.getDislikeCount()
                        + ", 텍스트 점수: " + score.getTextRelevanceScore()
                        + ", 조회수: " + score.getViewCount());
                ctx.y -= 10;
            }

            writeWrapped(ctx, 12, "[최근 피드백]");
            if (recentFeedbacks.isEmpty()) {
                writeWrapped(ctx, 11, "- 등록된 피드백이 없습니다.");
            } else {
                for (String fb : recentFeedbacks) {
                    writeWrapped(ctx, 11, "- " + fb);
                }
            }

            ctx.closeContent();
            document.save(baos);
        }

        return baos.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generateDepartmentTop5Pdf(DepartmentType department, int days, int limit) throws Exception {

        if (days <= 0) days = 7;
        if (limit <= 0) limit = 5;

        LocalDateTime from = LocalDateTime.now().minusDays(days);

        List<Object[]> rows = userArticleLogRepository.findTopArticlesByDepartment(department, from);
        List<Object[]> limited = rows.stream().limit(limit).toList();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {

            InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSansCJK-Regular.otf");
            if (fontStream == null) {
                throw new IllegalStateException("폰트 파일을 찾을 수 없습니다: /fonts/NotoSansCJK-Regular.otf");
            }
            PDType0Font font = PDType0Font.load(document, fontStream);

            PdfContext ctx = new PdfContext(document, font);
            ctx.newPage();

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            writeWrapped(ctx, 18, "INSK 부서별 TOP 리포트");
            writeWrapped(ctx, 12, "부서: " + (department != null ? department.name() : "-"));
            writeWrapped(ctx, 12, "기간: 최근 " + days + "일");
            ctx.y -= 10;

            if (limited.isEmpty()) {
                writeWrapped(ctx, 12, "조회 데이터가 없습니다.");
            } else {
                int rank = 1;
                for (Object[] row : limited) {
                    Long articleId = (Long) row[0];
                    Long viewCount = (Long) row[1];

                    Article article = articleRepository.findById(articleId).orElse(null);
                    if (article == null) continue;

                    ArticleScore score = scoreRepository.findByArticle_ArticleId(articleId)
                            .orElse(null);

                    writeWrapped(ctx, 14, "[#" + rank + "] " + article.getTitle());
                    writeWrapped(ctx, 11, "출처: " + Objects.toString(article.getSource(), "-"));
                    writeWrapped(ctx, 11, "발행일: " + article.getPublishedAt().format(fmt));
                    writeWrapped(ctx, 11, "조회수: " + viewCount);
                    if (score != null) {
                        writeWrapped(ctx, 11, "점수: " + score.getScore()
                                + " (좋아요 " + score.getLikeCount()
                                + ", 싫어요 " + score.getDislikeCount() + ")");
                    }
                    ctx.y -= 10;
                    rank++;
                }
            }

            ctx.closeContent();
            document.save(baos);
        }

        return baos.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] generateWeeklyDigestPdf(int days, int limit) throws Exception {

        if (days <= 0) days = 7;
        if (limit <= 0) limit = 10;

        LocalDateTime from = LocalDateTime.now().minusDays(days);

        List<ArticleAnalysis> analyses =
                analysisRepository.findByCreatedAtAfterOrderByCreatedAtDesc(from);

        List<ArticleAnalysis> limited = analyses.stream().limit(limit).toList();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {

            InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSansCJK-Regular.otf");
            if (fontStream == null) {
                throw new IllegalStateException("폰트 파일을 찾을 수 없습니다: /fonts/NotoSansCJK-Regular.otf");
            }
            PDType0Font font = PDType0Font.load(document, fontStream);

            PdfContext ctx = new PdfContext(document, font);
            ctx.newPage();

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            writeWrapped(ctx, 18, "INSK Weekly Digest");
            writeWrapped(ctx, 12, "기간: 최근 " + days + "일");
            ctx.y -= 10;

            if (limited.isEmpty()) {
                writeWrapped(ctx, 12, "요약할 뉴스가 없습니다.");
            } else {
                int idx = 1;
                for (ArticleAnalysis analysis : limited) {
                    Article article = analysis.getArticle();

                    writeWrapped(ctx, 14, "[" + idx + "] " + article.getTitle());
                    writeWrapped(ctx, 11, "출처: " + Objects.toString(article.getSource(), "-"));
                    writeWrapped(ctx, 11, "발행일: " + article.getPublishedAt().format(fmt));

                    ArticleScore score = scoreRepository.findByArticle_ArticleId(article.getArticleId())
                            .orElse(null);
                    if (score != null) {
                        writeWrapped(ctx, 11, "점수: " + score.getScore());
                    }

                    writeWrapped(ctx, 12, "[요약]");
                    writeWrapped(ctx, 11, analysis.getSummary());

                    writeWrapped(ctx, 12, "[인사이트]");
                    writeWrapped(ctx, 11, analysis.getInsight());

                    ctx.y -= 10;
                    idx++;
                }
            }

            ctx.closeContent();
            document.save(baos);
        }

        return baos.toByteArray();
    }

    private void writeWrapped(PdfContext ctx, int fontSize, String text) throws Exception {
        if (text == null || text.isBlank()) {
            return;
        }

        float usableWidth = ctx.page.getMediaBox().getWidth() - (MARGIN * 2);
        List<String> lines = PdfTextWrapper.wrap(text, ctx.font, fontSize, usableWidth);

        for (String line : lines) {

            if (ctx.y < BOTTOM_MARGIN) {
                ctx.newPage();
            }

            ctx.content.beginText();
            ctx.content.setFont(ctx.font, fontSize);
            ctx.content.newLineAtOffset(MARGIN, ctx.y);
            ctx.content.showText(line);
            ctx.content.endText();

            ctx.y -= LINE_HEIGHT;
        }
    }

    private static class PdfContext {
        private final PDDocument document;
        private final PDType0Font font;
        private PDPage page;
        private PDPageContentStream content;
        private float y;

        private PdfContext(PDDocument document, PDType0Font font) {
            this.document = document;
            this.font = font;
        }

        private void newPage() throws Exception {
            if (content != null) {
                content.close();
            }
            this.page = new PDPage();
            this.document.addPage(this.page);
            this.content = new PDPageContentStream(this.document, this.page);
            this.y = START_Y;
        }

        private void closeContent() throws Exception {
            if (content != null) {
                content.close();
            }
        }
    }
}
package com.insk.insk_backend.service;

import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.util.ArrayList;
import java.util.List;

public class PdfTextWrapper {

    public static List<String> wrap(String text, PDType0Font font, int fontSize, float maxWidth) throws Exception {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;

        StringBuilder current = new StringBuilder();

        for (char c : text.toCharArray()) {
            String test = current.toString() + c;
            float width = font.getStringWidth(test) / 1000f * fontSize;

            if (width > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder();
                current.append(c);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }

        return lines;
    }
}
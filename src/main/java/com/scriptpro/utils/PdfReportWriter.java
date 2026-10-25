package com.scriptpro.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PdfReportWriter {
    private static final Charset PDF_CHARSET = Charset.forName("windows-1252");
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int LEFT = 46;
    private static final int TOP = 736;
    private static final int MAX_CONTENT_HEIGHT = 660;
    private static final String FOOTER = "© 2026 LM-Code — Développé par Michael — lm-code.be";

    private PdfReportWriter() {
    }

    public static Path write(Path file, String title, String subtitle, List<String> lines) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        List<StyledLine> styledLines = prepareLines(lines);
        List<List<StyledLine>> pages = paginate(styledLines);
        Files.write(file, buildPdf(title, subtitle, pages));
        return file;
    }

    private static List<StyledLine> prepareLines(List<String> lines) {
        List<StyledLine> prepared = new ArrayList<>();
        for (String rawLine : lines) {
            String[] splitLines = (rawLine == null ? "" : rawLine).split("\\R", -1);
            for (String splitLine : splitLines) {
                if (splitLine.isBlank()) {
                    prepared.add(StyledLine.blank());
                    continue;
                }
                Style style = Style.body();
                String text = splitLine;
                if (splitLine.startsWith("## ")) {
                    style = Style.subHeading();
                    text = splitLine.substring(3);
                } else if (splitLine.startsWith("# ")) {
                    style = Style.heading();
                    text = splitLine.substring(2);
                }
                for (String wrapped : wrap(text, style.maxChars())) {
                    prepared.add(new StyledLine(wrapped, style));
                }
            }
        }
        return prepared;
    }

    private static List<List<StyledLine>> paginate(List<StyledLine> lines) {
        List<List<StyledLine>> pages = new ArrayList<>();
        List<StyledLine> page = new ArrayList<>();
        int height = 0;
        for (StyledLine line : lines) {
            if (!page.isEmpty() && height + line.style().lineHeight() > MAX_CONTENT_HEIGHT) {
                pages.add(page);
                page = new ArrayList<>();
                height = 0;
            }
            page.add(line);
            height += line.style().lineHeight();
        }
        if (page.isEmpty()) {
            page.add(new StyledLine("Aucune donnée à exporter.", Style.body()));
        }
        pages.add(page);
        return pages;
    }

    private static byte[] buildPdf(String title, String subtitle, List<List<StyledLine>> pages) throws IOException {
        List<byte[]> objects = new ArrayList<>();
        int pageCount = pages.size();
        int fontRegularObject = 3 + pageCount * 2;
        int fontBoldObject = fontRegularObject + 1;

        objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids = new StringBuilder();
        for (int index = 0; index < pageCount; index++) {
            kids.append(3 + index * 2).append(" 0 R ");
        }
        objects.add(bytes("<< /Type /Pages /Kids [ " + kids + "] /Count " + pageCount + " >>"));

        for (int index = 0; index < pageCount; index++) {
            int pageObject = 3 + index * 2;
            int contentObject = pageObject + 1;
            String pageDictionary = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " +
                    PAGE_WIDTH + " " + PAGE_HEIGHT +
                    "] /Resources << /Font << /F1 " + fontRegularObject +
                    " 0 R /F2 " + fontBoldObject +
                    " 0 R >> >> /Contents " + contentObject + " 0 R >>";
            objects.add(bytes(pageDictionary));
            objects.add(streamObject(renderPage(title, subtitle, pages.get(index), index + 1, pageCount)));
        }

        objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n".getBytes(PDF_CHARSET));
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.size());
            output.write((index + 1 + " 0 obj\n").getBytes(PDF_CHARSET));
            output.write(objects.get(index));
            output.write("\nendobj\n".getBytes(PDF_CHARSET));
        }
        int xrefOffset = output.size();
        output.write(("xref\n0 " + (objects.size() + 1) + "\n").getBytes(PDF_CHARSET));
        output.write("0000000000 65535 f \n".getBytes(PDF_CHARSET));
        for (int index = 1; index < offsets.size(); index++) {
            output.write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(index)).getBytes(PDF_CHARSET));
        }
        output.write(("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" +
                xrefOffset + "\n%%EOF\n").getBytes(PDF_CHARSET));
        return output.toByteArray();
    }

    private static String renderPage(String title, String subtitle, List<StyledLine> lines, int pageNumber, int pageCount) {
        StringBuilder content = new StringBuilder();
        content.append("0.965 0.980 1 rg 0 0 ").append(PAGE_WIDTH).append(' ').append(PAGE_HEIGHT).append(" re f\n");
        content.append("0.070 0.100 0.180 rg 0 786 ").append(PAGE_WIDTH).append(" 56 re f\n");
        content.append("0.145 0.388 0.922 rg 0 786 10 56 re f\n");
        text(content, "/F2", 20, 1, 1, 1, LEFT, 818, "ScriptPro");
        text(content, "/F1", 10, 0.78, 0.84, 0.95, LEFT, 800, FOOTER);
        text(content, "/F2", 18, 0.07, 0.10, 0.18, LEFT, 762, title);
        text(content, "/F1", 10, 0.34, 0.41, 0.52, LEFT, 746, subtitle);

        int y = TOP - 28;
        for (StyledLine line : lines) {
            if (!line.text().isBlank()) {
                Style style = line.style();
                text(content, style.font(), style.fontSize(), style.red(), style.green(), style.blue(), LEFT, y, line.text());
            }
            y -= line.style().lineHeight();
        }

        content.append("0.860 0.900 0.960 rg 46 44 503 1 re f\n");
        text(content, "/F1", 9, 0.37, 0.44, 0.55, LEFT, 27, FOOTER);
        text(content, "/F1", 9, 0.37, 0.44, 0.55, 505, 27, "Page " + pageNumber + "/" + pageCount);
        return content.toString();
    }

    private static void text(StringBuilder content, String font, int size, double red, double green,
                             double blue, int x, int y, String text) {
        content.append("BT ")
                .append(font).append(' ').append(size).append(" Tf ")
                .append(number(red)).append(' ').append(number(green)).append(' ').append(number(blue)).append(" rg ")
                .append(x).append(' ').append(y).append(" Td (")
                .append(escape(text)).append(") Tj ET\n");
    }

    private static byte[] streamObject(String stream) throws IOException {
        byte[] streamBytes = stream.getBytes(PDF_CHARSET);
        ByteArrayOutputStream object = new ByteArrayOutputStream();
        object.write(("<< /Length " + streamBytes.length + " >>\nstream\n").getBytes(PDF_CHARSET));
        object.write(streamBytes);
        object.write("\nendstream".getBytes(PDF_CHARSET));
        return object.toByteArray();
    }

    private static String escape(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private static List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (remaining.length() > maxChars) {
            int cut = remaining.lastIndexOf(' ', maxChars);
            if (cut < maxChars / 2) {
                cut = maxChars;
            }
            lines.add(remaining.substring(0, cut).trim());
            remaining = remaining.substring(cut).trim();
        }
        lines.add(remaining);
        return lines;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(PDF_CHARSET);
    }

    private record StyledLine(String text, Style style) {
        static StyledLine blank() {
            return new StyledLine("", Style.blank());
        }
    }

    private record Style(String font, int fontSize, int lineHeight, int maxChars,
                         double red, double green, double blue) {
        static Style heading() {
            return new Style("/F2", 13, 22, 78, 0.08, 0.18, 0.38);
        }

        static Style subHeading() {
            return new Style("/F2", 11, 18, 88, 0.15, 0.39, 0.92);
        }

        static Style body() {
            return new Style("/F1", 9, 14, 112, 0.09, 0.12, 0.18);
        }

        static Style blank() {
            return new Style("/F1", 9, 8, 112, 0.09, 0.12, 0.18);
        }
    }
}

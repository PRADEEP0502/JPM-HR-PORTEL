package com.jpm.hrms.report;

import com.jpm.hrms.common.ApiException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Exports a table as Excel (.xlsx, default) or CSV. */
public final class TableExporter {
    private TableExporter() {
    }

    public static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");

    public static ResponseEntity<byte[]> export(String format, String sheetName, String title, List<String> headers,
                                                List<List<Object>> rows, String baseName) {
        if (format == null || format.isBlank() || format.equalsIgnoreCase("xlsx")) {
            return ExcelExporter.response(baseName, ExcelExporter.build(sheetName, title, headers, rows));
        }
        if (!format.equalsIgnoreCase("csv")) throw ApiException.badRequest("Export format must be xlsx or csv");
        String fileName = baseName + "-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv(headers, rows));
    }

    /** UTF-8 with a byte-order mark so Excel shows Indian names and ₹ correctly. */
    static byte[] csv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        line(sb, headers.stream().map(h -> (Object) h).toList());
        for (List<Object> r : rows) line(sb, r);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder sb, List<Object> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            Object v = values.get(i);
            if (v == null) continue;
            String s = v.toString();
            // Text starting with = + - @ would run as a formula in a spreadsheet: prefix it with a quote.
            if (!(v instanceof Number) && ExcelExporter.looksLikeFormula(s)) s = "'" + s;
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r") || s.startsWith("'")) {
                s = "\"" + s.replace("\"", "\"\"") + "\"";
            }
            sb.append(s);
        }
        sb.append("\r\n");
    }
}

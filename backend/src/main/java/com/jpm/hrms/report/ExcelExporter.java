package com.jpm.hrms.report;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Renders a simple tabular report into an .xlsx workbook. */
public final class ExcelExporter {
    private ExcelExporter() {
    }

    public static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public static byte[] build(String sheetName, String title, List<String> headers, List<List<Object>> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName);

            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd-mmm-yyyy"));
            CellStyle textStyle = wb.createCellStyle();
            textStyle.setQuotePrefixed(true); // user text is never interpreted as a formula

            Row titleRow = sheet.createRow(0);
            Cell t = titleRow.createCell(0);
            t.setCellValue(title);
            t.setCellStyle(titleStyle);

            Row header = sheet.createRow(2);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headerStyle);
            }
            int r = 3;
            for (List<Object> values : rows) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < values.size(); i++) {
                    Object v = values.get(i);
                    Cell c = row.createCell(i);
                    if (v == null) continue;
                    if (v instanceof Number n && !(v instanceof BigDecimal)) c.setCellValue(n.doubleValue());
                    else if (v instanceof BigDecimal bd) c.setCellValue(bd.doubleValue());
                    else if (v instanceof LocalDate d) {
                        c.setCellValue(d);
                        c.setCellStyle(dateStyle);
                    } else {
                        String s = v.toString();
                        c.setCellValue(s);
                        if (looksLikeFormula(s)) c.setCellStyle(textStyle);
                    }
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 60 * 256));
            }
            sheet.createFreezePane(0, 3);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Text starting with these characters could be treated as a formula if a user re-enters the cell. */
    static boolean looksLikeFormula(String s) {
        return !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0;
    }

    public static ResponseEntity<byte[]> response(String baseName, byte[] bytes) {
        String fileName = baseName + "-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }
}

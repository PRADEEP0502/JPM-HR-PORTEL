package com.jpm.hrms.report;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.report.ReportService.Report;
import com.jpm.hrms.report.ReportService.ReportType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class ReportController {
    private final ReportService service;
    private final AuditService audit;

    public ReportController(ReportService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/{type}")
    public Report report(@PathVariable ReportType type,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.build(type, from, to);
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable ReportType type,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        Report r = service.build(type, from, to);
        List<String> headers = r.columns().stream().map(ReportService.Column::label).toList();
        List<List<Object>> rows = new ArrayList<>();
        for (var row : r.rows()) {
            List<Object> values = new ArrayList<>();
            for (var c : r.columns()) values.add(row.get(c.key()));
            rows.add(values);
        }
        String range = (r.from() == null && r.to() == null) ? "All dates"
                : (r.from() == null ? "…" : r.from().toString()) + " to " + (r.to() == null ? "today" : r.to().toString());
        var response = TableExporter.export(format, type.name(), "JPM HR Portal - " + r.title() + " (" + range + ")", headers, rows,
                type.name().toLowerCase().replace('_', '-') + "-report");
        audit.record("EXPORT", "REPORT", null, "Exported " + r.title() + " (" + rows.size() + " rows, " + format.toUpperCase() + ")");
        return response;
    }
}

package com.jpm.hrms.analytics;

import com.jpm.hrms.analytics.SourceAnalyticsService.Analytics;
import com.jpm.hrms.analytics.SourceAnalyticsService.Granularity;
import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.report.TableExporter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/analytics/sources")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class SourceAnalyticsController {
    private final SourceAnalyticsService service;
    private final AuditService audit;

    public SourceAnalyticsController(SourceAnalyticsService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public Analytics get(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(required = false) Long departmentId,
                         @RequestParam(required = false) String source,
                         @RequestParam(required = false) Granularity granularity) {
        return service.build(from, to, departmentId, source, granularity);
    }

    /** The source comparison table, plus the trend by period, as Excel or CSV. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Long departmentId,
                                         @RequestParam(required = false) String source,
                                         @RequestParam(required = false) Granularity granularity,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        Analytics a = service.build(from, to, departmentId, source, granularity);
        List<List<Object>> rows = new ArrayList<>();
        for (var s : a.sources()) {
            rows.add(List.of(s.source(), s.applications(), s.share() + "%", s.selected(), s.joined(), s.rejected(), s.pending(),
                    s.selectionRate() + "%", s.requirementHires()));
        }
        rows.add(List.of());
        List<Object> head = new ArrayList<>(List.of(a.granularity() == Granularity.MONTH ? "Month" : "Date"));
        List<String> shown = a.sources().stream().map(SourceAnalyticsService.SourceRow::source).toList();
        shown.forEach(s -> head.add(s + " (applications)"));
        rows.add(head);
        for (var p : a.trend()) {
            List<Object> r = new ArrayList<>(List.of(p.label()));
            shown.forEach(s -> r.add(p.applications().getOrDefault(s, 0L)));
            rows.add(r);
        }
        String range = (from == null ? "all dates" : from + " to " + (to == null ? "today" : to));
        audit.record("EXPORT", "REPORT", null, "Exported Source Analytics (" + range + ", " + format.toUpperCase() + ")");
        return TableExporter.export(format, "Source Analytics", "JPM HR Portal - Source Analytics (" + range + ")",
                List.of("Source", "Applications", "Share", "Selected (incl. joined)", "Joined", "Rejected", "Pending",
                        "Selection %", "Hires toward requirements"),
                rows, "source-analytics");
    }
}

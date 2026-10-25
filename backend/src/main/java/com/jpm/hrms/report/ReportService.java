package com.jpm.hrms.report;

import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentRepository;
import com.jpm.hrms.interview.Interview;
import com.jpm.hrms.interview.InterviewRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All reports are computed from live database rows at request time. */
@Service
public class ReportService {

    public enum ReportType {
        DEPARTMENT("Department-wise Candidate Report"),
        MONTHLY("Monthly Application Report"),
        SELECTED("Selected Candidate Report"),
        REJECTED("Rejected Candidate Report"),
        PENDING_VERIFICATION("Pending Verification Report"),
        INTERVIEW_STATUS("Interview Status Report");

        public final String title;

        ReportType(String title) {
            this.title = title;
        }
    }

    public record Column(String key, String label) {
    }

    public record ChartPoint(String label, long value) {
    }

    public record Report(ReportType type, String title, LocalDate from, LocalDate to, Instant generatedAt,
                         List<Column> columns, List<Map<String, Object>> rows, List<ChartPoint> chart) {
    }

    private final CandidateRepository candidates;
    private final InterviewRepository interviews;
    private final DepartmentRepository departments;
    private final Clock clock;
    private final ZoneId zone;

    public ReportService(CandidateRepository candidates, InterviewRepository interviews, DepartmentRepository departments,
                         Clock clock, ZoneId businessZone) {
        this.candidates = candidates;
        this.interviews = interviews;
        this.departments = departments;
        this.clock = clock;
        this.zone = businessZone;
    }

    @Transactional(readOnly = true)
    public Report build(ReportType type, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw com.jpm.hrms.common.ApiException.badRequest("'From' date must be on or before 'To' date");
        }
        if (type == ReportType.MONTHLY && from == null && to == null) {
            to = LocalDate.now(clock);
            from = YearMonth.from(to).minusMonths(11).atDay(1);
        }
        return switch (type) {
            case DEPARTMENT -> department(from, to);
            case MONTHLY -> monthly(from, to);
            case SELECTED -> candidateList(type, from, to, statusSpec(ApplicationStatus.SELECTED));
            case REJECTED -> candidateList(type, from, to, statusSpec(ApplicationStatus.REJECTED));
            case PENDING_VERIFICATION -> candidateList(type, from, to,
                    (root, q, cb) -> cb.equal(root.get("verificationStatus"), VerificationStatus.PENDING));
            case INTERVIEW_STATUS -> interviewReport(from, to);
        };
    }

    private Report department(LocalDate from, LocalDate to) {
        List<Candidate> all = candidates.findAll(dateRange(from, to), Sort.unsorted());
        Map<String, long[]> agg = new LinkedHashMap<>(); // total, new, shortlisted, selected, rejected, pending
        for (Department d : departments.findAllByOrderByNameAsc()) if (d.isActive()) agg.put(d.getName(), new long[6]);
        for (Candidate c : all) {
            String name = c.getDepartment() == null ? "Unassigned" : c.getDepartment().getName();
            long[] a = agg.computeIfAbsent(name, k -> new long[6]);
            a[0]++;
            switch (c.getApplicationStatus()) {
                case NEW -> a[1]++;
                case SHORTLISTED -> a[2]++;
                case SELECTED -> a[3]++;
                case REJECTED -> a[4]++;
                default -> { }
            }
            if (c.getApplicationStatus().isPending()) a[5]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ChartPoint> chart = new ArrayList<>();
        agg.forEach((name, a) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("department", name);
            r.put("total", a[0]);
            r.put("new", a[1]);
            r.put("shortlisted", a[2]);
            r.put("selected", a[3]);
            r.put("rejected", a[4]);
            r.put("pending", a[5]);
            rows.add(r);
            if (a[0] > 0) chart.add(new ChartPoint(name, a[0]));
        });
        return new Report(ReportType.DEPARTMENT, ReportType.DEPARTMENT.title, from, to, clock.instant(),
                List.of(new Column("department", "Department"), new Column("total", "Total Candidates"),
                        new Column("new", "New Applications"), new Column("shortlisted", "Shortlisted"),
                        new Column("selected", "Selected"), new Column("rejected", "Rejected"),
                        new Column("pending", "Pending Decision")),
                rows, chart);
    }

    private Report monthly(LocalDate from, LocalDate to) {
        List<Candidate> all = candidates.findAll(dateRange(from, to), Sort.unsorted());
        Map<YearMonth, long[]> agg = new LinkedHashMap<>(); // total, selected, rejected, shortlisted
        YearMonth start = from != null ? YearMonth.from(from) : all.stream().map(c -> YearMonth.from(c.getApplicationDate())).min(YearMonth::compareTo).orElse(YearMonth.now(clock));
        YearMonth end = to != null ? YearMonth.from(to) : YearMonth.now(clock);
        for (YearMonth m = start; !m.isAfter(end) && agg.size() < 120; m = m.plusMonths(1)) agg.put(m, new long[4]);
        for (Candidate c : all) {
            long[] a = agg.get(YearMonth.from(c.getApplicationDate()));
            if (a == null) continue;
            a[0]++;
            if (c.getApplicationStatus() == ApplicationStatus.SELECTED) a[1]++;
            if (c.getApplicationStatus() == ApplicationStatus.REJECTED) a[2]++;
            if (c.getApplicationStatus() == ApplicationStatus.SHORTLISTED) a[3]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ChartPoint> chart = new ArrayList<>();
        agg.forEach((m, a) -> {
            String label = m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + m.getYear();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("month", label);
            r.put("applications", a[0]);
            r.put("shortlisted", a[3]);
            r.put("selected", a[1]);
            r.put("rejected", a[2]);
            rows.add(r);
            chart.add(new ChartPoint(label, a[0]));
        });
        return new Report(ReportType.MONTHLY, ReportType.MONTHLY.title, from, to, clock.instant(),
                List.of(new Column("month", "Month"), new Column("applications", "Applications"),
                        new Column("shortlisted", "Currently Shortlisted"), new Column("selected", "Selected"),
                        new Column("rejected", "Rejected")),
                rows, chart);
    }

    private Report candidateList(ReportType type, LocalDate from, LocalDate to, Specification<Candidate> spec) {
        List<Candidate> list = candidates.findAll(spec.and(dateRange(from, to)), Sort.by(Sort.Direction.DESC, "appliedAt"));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Long> byDept = new LinkedHashMap<>();
        for (Candidate c : list) {
            String dept = c.getDepartment() != null ? c.getDepartment().getName() : c.getDepartmentRaw() != null ? c.getDepartmentRaw() : "Unassigned";
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", c.getId());
            r.put("candidateCode", c.getCandidateCode());
            r.put("fullName", c.getFullName());
            r.put("mobile", c.getMobile());
            r.put("department", dept);
            r.put("qualification", c.getQualification());
            r.put("experienceYears", c.getExperienceYears());
            r.put("applicationDate", c.getApplicationDate());
            r.put("applicationStatus", c.getApplicationStatus().label);
            r.put("interviewStatus", c.getInterviewStatus().label);
            if (type == ReportType.PENDING_VERIFICATION) r.put("dataIssues", c.getDataIssues());
            rows.add(r);
            byDept.merge(dept, 1L, Long::sum);
        }
        List<Column> cols = new ArrayList<>(List.of(new Column("candidateCode", "Candidate ID"), new Column("fullName", "Name"),
                new Column("mobile", "Mobile"), new Column("department", "Department"),
                new Column("qualification", "Qualification"), new Column("experienceYears", "Experience (yrs)"),
                new Column("applicationDate", "Application Date"), new Column("applicationStatus", "Application Status"),
                new Column("interviewStatus", "Interview Status")));
        if (type == ReportType.PENDING_VERIFICATION) cols.add(new Column("dataIssues", "Data Issues"));
        return new Report(type, type.title, from, to, clock.instant(), cols, rows,
                byDept.entrySet().stream().map(e -> new ChartPoint(e.getKey(), e.getValue())).toList());
    }

    private Report interviewReport(LocalDate from, LocalDate to) {
        Specification<Interview> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), from.atStartOfDay(zone).toInstant()));
            if (to != null) ps.add(cb.lessThan(root.get("scheduledAt"), to.plusDays(1).atStartOfDay(zone).toInstant()));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(zone);
        List<Interview> list = interviews.findAll(spec, Sort.by(Sort.Direction.DESC, "scheduledAt"));
        Map<InterviewStatus, Long> counts = new EnumMap<>(InterviewStatus.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Interview i : list) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", i.getCandidate().getId());
            r.put("candidateCode", i.getCandidate().getCandidateCode());
            r.put("fullName", i.getCandidate().getFullName());
            r.put("round", i.getRoundName());
            r.put("interviewer", i.getInterviewer());
            r.put("scheduledAt", fmt.format(i.getScheduledAt()));
            r.put("mode", i.getMode().name().replace('_', ' '));
            r.put("status", i.getStatus().label);
            r.put("remarks", i.getRemarks());
            rows.add(r);
            counts.merge(i.getStatus(), 1L, Long::sum);
        }
        List<ChartPoint> chart = new ArrayList<>();
        for (InterviewStatus s : InterviewStatus.values()) {
            if (s != InterviewStatus.NOT_SCHEDULED) chart.add(new ChartPoint(s.label, counts.getOrDefault(s, 0L)));
        }
        return new Report(ReportType.INTERVIEW_STATUS, ReportType.INTERVIEW_STATUS.title, from, to, clock.instant(),
                List.of(new Column("candidateCode", "Candidate ID"), new Column("fullName", "Name"),
                        new Column("round", "Round"), new Column("interviewer", "Interviewer"),
                        new Column("scheduledAt", "Date & Time"), new Column("mode", "Mode"),
                        new Column("status", "Status"), new Column("remarks", "Remarks")),
                rows, chart);
    }

    private static Specification<Candidate> statusSpec(ApplicationStatus s) {
        return (root, q, cb) -> cb.equal(root.get("applicationStatus"), s);
    }

    private static Specification<Candidate> dateRange(LocalDate from, LocalDate to) {
        return (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("applicationDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("applicationDate"), to));
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }
}

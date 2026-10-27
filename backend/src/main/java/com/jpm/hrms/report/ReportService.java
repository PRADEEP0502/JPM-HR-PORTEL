package com.jpm.hrms.report;

import com.jpm.hrms.audit.AuditLog;
import com.jpm.hrms.audit.AuditLogRepository;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentDirectory;
import com.jpm.hrms.department.DepartmentRepository;
import com.jpm.hrms.interview.Interview;
import com.jpm.hrms.interview.InterviewRepository;
import com.jpm.hrms.interview.InterviewViews;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

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
        INTERVIEW_STATUS("Interview Status Report"),
        APPLICATIONS_BY_DATE("Applications by Date"),
        STATUS("Candidates by Recruitment Status"),
        RATING_DISTRIBUTION("Profile Match Rating Distribution"),
        SELECTED_JOINED("Selected and Joined Candidates"),
        ACTIVITY("Recruitment Activity History");

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
    private final DepartmentDirectory depts;
    private final InterviewViews views;
    private final AuditLogRepository auditLogs;
    private final Clock clock;
    private final ZoneId zone;

    public ReportService(CandidateRepository candidates, InterviewRepository interviews, DepartmentRepository departments,
                         DepartmentDirectory depts, InterviewViews views, AuditLogRepository auditLogs, Clock clock,
                         ZoneId businessZone) {
        this.auditLogs = auditLogs;
        this.depts = depts;
        this.views = views;
        this.candidates = candidates;
        this.interviews = interviews;
        this.departments = departments;
        this.clock = clock;
        this.zone = businessZone;
    }

    public Report build(ReportType type, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw com.jpm.hrms.common.ApiException.badRequest("'From' date must be on or before 'To' date");
        }
        if (type == ReportType.MONTHLY && from == null && to == null) {
            to = LocalDate.now(clock);
            from = YearMonth.from(to).minusMonths(11).atDay(1);
        }
        if ((type == ReportType.APPLICATIONS_BY_DATE || type == ReportType.ACTIVITY) && from == null && to == null) {
            to = LocalDate.now(clock);
            from = to.minusDays(29);
        }
        return switch (type) {
            case DEPARTMENT -> department(from, to);
            case MONTHLY -> monthly(from, to);
            case SELECTED -> candidateList(type, from, to, statusSpec(ApplicationStatus.SELECTED));
            case REJECTED -> candidateList(type, from, to, statusSpec(ApplicationStatus.REJECTED));
            case PENDING_VERIFICATION -> candidateList(type, from, to,
                    Criteria.where("verificationStatus").is(VerificationStatus.PENDING));
            case INTERVIEW_STATUS -> interviewReport(from, to);
            case APPLICATIONS_BY_DATE -> byDate(from, to);
            case STATUS -> byStatus(from, to);
            case RATING_DISTRIBUTION -> ratingDistribution(from, to);
            case SELECTED_JOINED -> candidateList(type, from, to,
                    Criteria.where("applicationStatus").in(ApplicationStatus.SELECTED, ApplicationStatus.JOINED));
            case ACTIVITY -> activity(from, to);
        };
    }

    private Report department(LocalDate from, LocalDate to) {
        List<Candidate> all = candidates.findAll(dateRange(from, to), Sort.unsorted());
        DepartmentDirectory.Names names = depts.snapshot();
        Map<String, long[]> agg = new LinkedHashMap<>(); // total, new, shortlisted, selected, rejected, pending
        for (Department d : departments.findAllByOrderByNameAsc()) if (d.isActive()) agg.put(d.getName(), new long[6]);
        for (Candidate c : all) {
            String name = names.name(c) == null ? "Unassigned" : names.name(c);
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

    private Report candidateList(ReportType type, LocalDate from, LocalDate to, Criteria filter) {
        List<Candidate> list = candidates.findAll(new Criteria().andOperator(filter, dateRange(from, to)),
                Sort.by(Sort.Direction.DESC, "appliedAt"));
        DepartmentDirectory.Names names = depts.snapshot();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Long> byDept = new LinkedHashMap<>();
        for (Candidate c : list) {
            String dept = names.nameOrRaw(c) != null ? names.nameOrRaw(c) : "Unassigned";
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
        Criteria range = Criteria.where("scheduledAt");
        if (from != null) range = range.gte(from.atStartOfDay(zone).toInstant());
        if (to != null) range = range.lt(to.plusDays(1).atStartOfDay(zone).toInstant());
        Criteria spec = from == null && to == null ? new Criteria() : range;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(zone);
        List<Interview> list = interviews.findAll(spec, Sort.by(Sort.Direction.DESC, "scheduledAt"));
        Map<Long, Candidate> byId = views.candidatesOf(list);
        Map<InterviewStatus, Long> counts = new EnumMap<>(InterviewStatus.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Interview i : list) {
            Map<String, Object> r = new LinkedHashMap<>();
            Candidate c = byId.get(i.getCandidateId());
            r.put("id", i.getCandidateId());
            r.put("candidateCode", c == null ? null : c.getCandidateCode());
            r.put("fullName", c == null ? null : c.getFullName());
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

    /** Applications received per day (default: the last 30 days). */
    private Report byDate(LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : to.minusDays(29);
        LocalDate end = to != null ? to : LocalDate.now(clock);
        if (start.plusDays(366).isBefore(end)) throw com.jpm.hrms.common.ApiException.badRequest("Choose a range of at most one year");
        Map<LocalDate, long[]> perDay = new LinkedHashMap<>(); // received, still pending
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) perDay.put(d, new long[2]);
        for (Candidate c : candidates.findAll(dateRange(start, end), Sort.unsorted())) {
            long[] a = perDay.get(c.getApplicationDate());
            if (a == null) continue;
            a[0]++;
            if (c.getApplicationStatus().isPending()) a[1]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ChartPoint> chart = new ArrayList<>();
        perDay.forEach((d, a) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", d);
            r.put("applications", a[0]);
            r.put("pending", a[1]);
            rows.add(r);
            chart.add(new ChartPoint(d.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)), a[0]));
        });
        return new Report(ReportType.APPLICATIONS_BY_DATE, ReportType.APPLICATIONS_BY_DATE.title, start, end, clock.instant(),
                List.of(new Column("date", "Date"), new Column("applications", "Applications"),
                        new Column("pending", "Still pending")), rows, chart);
    }

    private Report byStatus(LocalDate from, LocalDate to) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        List<Candidate> all = candidates.findAll(dateRange(from, to), Sort.unsorted());
        for (Candidate c : all) counts.merge(c.getApplicationStatus(), 1L, Long::sum);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ChartPoint> chart = new ArrayList<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            long n = counts.getOrDefault(s, 0L);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", s.label);
            r.put("candidates", n);
            r.put("share", all.isEmpty() ? "0%" : Math.round(n * 1000.0 / all.size()) / 10.0 + "%");
            rows.add(r);
            chart.add(new ChartPoint(s.label, n));
        }
        return new Report(ReportType.STATUS, ReportType.STATUS.title, from, to, clock.instant(),
                List.of(new Column("status", "Recruitment Status"), new Column("candidates", "Candidates"),
                        new Column("share", "Share")), rows, chart);
    }

    /** Ratings 1-10 plus Not Rated, with the guideline band of each value. */
    private Report ratingDistribution(LocalDate from, LocalDate to) {
        long[] counts = new long[11]; // index 0 = not rated
        for (Candidate c : candidates.findAll(dateRange(from, to), Sort.unsorted())) {
            Integer r = c.getProfileRating();
            counts[r == null ? 0 : r]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ChartPoint> chart = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("rating", i + "/10");
            r.put("band", com.jpm.hrms.candidate.CandidateService.ratingLabel(i));
            r.put("candidates", counts[i]);
            rows.add(r);
            chart.add(new ChartPoint(String.valueOf(i), counts[i]));
        }
        Map<String, Object> unrated = new LinkedHashMap<>();
        unrated.put("rating", "Not Rated");
        unrated.put("band", "-");
        unrated.put("candidates", counts[0]);
        rows.add(unrated);
        chart.add(new ChartPoint("Not Rated", counts[0]));
        return new Report(ReportType.RATING_DISTRIBUTION, ReportType.RATING_DISTRIBUTION.title, from, to, clock.instant(),
                List.of(new Column("rating", "Profile Match Rating"), new Column("band", "Guideline"),
                        new Column("candidates", "Candidates")), rows, chart);
    }

    /** Recruitment actions on candidates from the audit log (sign-ins and settings changes are not included). */
    private Report activity(LocalDate from, LocalDate to) {
        int limit = 2000;
        Criteria when = Criteria.where("occurredAt").gte(from.atStartOfDay(zone).toInstant()).lt(to.plusDays(1).atStartOfDay(zone).toInstant());
        List<AuditLog> logs = auditLogs.findAll(new Criteria().andOperator(Criteria.where("entityType").is("CANDIDATE"),
                        Criteria.where("action").nin("DOCUMENT_DOWNLOADED", "DOCUMENT_VIEWED"), when),
                org.springframework.data.domain.PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "occurredAt"))).getContent();
        Map<Long, Candidate> byId = new java.util.HashMap<>();
        candidates.findAllById(logs.stream().map(AuditLog::getEntityId).filter(java.util.Objects::nonNull).distinct().toList())
                .forEach(c -> byId.put(c.getId(), c));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(zone);
        Map<String, Long> byAction = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AuditLog a : logs) {
            Candidate c = a.getEntityId() == null ? null : byId.get(a.getEntityId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", a.getEntityId());
            r.put("when", fmt.format(a.getOccurredAt()));
            r.put("candidateCode", c == null ? null : c.getCandidateCode());
            r.put("fullName", c == null ? null : c.getFullName());
            r.put("action", a.getAction().replace('_', ' ').toLowerCase(Locale.ROOT));
            r.put("details", a.getDetails());
            r.put("actor", a.getActor());
            rows.add(r);
            byAction.merge((String) r.get("action"), 1L, Long::sum);
        }
        return new Report(ReportType.ACTIVITY, ReportType.ACTIVITY.title + (logs.size() == limit ? " (latest " + limit + ")" : ""),
                from, to, clock.instant(),
                List.of(new Column("when", "Date & Time"), new Column("candidateCode", "Candidate ID"), new Column("fullName", "Name"),
                        new Column("action", "Action"), new Column("details", "Details"), new Column("actor", "By")),
                rows, byAction.entrySet().stream().map(e -> new ChartPoint(e.getKey(), e.getValue())).toList());
    }

    private static Criteria statusSpec(ApplicationStatus s) {
        return Criteria.where("applicationStatus").is(s);
    }

    private static Criteria dateRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) return new Criteria();
        Criteria c = Criteria.where("applicationDate");
        if (from != null) c = c.gte(from);
        if (to != null) c = c.lte(to);
        return c;
    }
}

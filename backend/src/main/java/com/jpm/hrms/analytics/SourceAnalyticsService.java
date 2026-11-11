package com.jpm.hrms.analytics;

import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.department.DepartmentDirectory;
import com.jpm.hrms.requirement.HiringRequirement;
import com.jpm.hrms.requirement.HiringRequirementRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where candidates come from: applications, outcomes and requirement hires per hiring source, computed from the
 * live candidate records on every request (so Google Form responses count as soon as they are imported).
 */
@Service
public class SourceAnalyticsService {

    /** The Source choices of the application form, in form order, plus a separate bucket for no answer. */
    public static final List<String> SOURCES = List.of("Indeed", "Man Power Agencies", "Street Boards", "Paper Advertisements",
            "Social Media", "Nithra Jobs", "Others");
    public static final String NOT_SPECIFIED = "Not specified";
    public static final List<String> CATEGORIES;

    static {
        List<String> all = new ArrayList<>(SOURCES);
        all.add(NOT_SPECIFIED);
        CATEGORIES = List.copyOf(all);
    }

    public enum Granularity { DAY, MONTH }

    public record SourceRow(String source, long applications, double share, long selected, long joined, long rejected,
                            long pending, double selectionRate, long requirementHires) {
    }

    /** One point per period; values are applications per source in CATEGORIES order (only the chosen source when filtered). */
    public record Period(String label, LocalDate from, LocalDate to, Map<String, Long> applications, Map<String, Long> selected) {
    }

    public record RequirementRow(Long requirementId, String department, String position, int needed, long filled,
                                 Map<String, Long> filledBySource) {
    }

    public record Analytics(LocalDate from, LocalDate to, Long departmentId, String source, Granularity granularity,
                            long totalApplications, String topSource, String topRequirementSource,
                            List<SourceRow> sources, List<Period> trend, List<RequirementRow> requirements) {
    }

    private final CandidateRepository candidates;
    private final HiringRequirementRepository requirements;
    private final DepartmentDirectory depts;
    private final Clock clock;

    public SourceAnalyticsService(CandidateRepository candidates, HiringRequirementRepository requirements,
                                  DepartmentDirectory depts, Clock clock) {
        this.candidates = candidates;
        this.requirements = requirements;
        this.depts = depts;
        this.clock = clock;
    }

    /** "indeed ", "Other", "Manpower agencies" → the form value; anything else typed in → Others; blank → Not specified. */
    public static String category(String raw) {
        if (raw == null || raw.isBlank()) return NOT_SPECIFIED;
        String key = normalize(raw);
        for (String s : SOURCES) if (normalize(s).equals(key)) return s;
        if (key.equals("manpoweragency") || key.equals("manpower")) return "Man Power Agencies";
        if (key.equals("notspecified")) return NOT_SPECIFIED;
        return "Others";
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public Analytics build(LocalDate from, LocalDate to, Long departmentId, String source, Granularity granularity) {
        if (from != null && to != null && from.isAfter(to)) throw ApiException.badRequest("'From' date must be on or before 'To' date");
        String onlySource = source == null || source.isBlank() ? null : CATEGORIES.stream()
                .filter(c -> c.equalsIgnoreCase(source.trim())).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown source: " + source));

        List<Criteria> cs = new ArrayList<>();
        if (from != null || to != null) {
            Criteria d = Criteria.where("applicationDate");
            if (from != null) d = d.gte(from);
            if (to != null) d = d.lte(to);
            cs.add(d);
        }
        if (departmentId != null) cs.add(Criteria.where("departmentId").is(departmentId == 0 ? null : departmentId));
        List<Candidate> all = candidates.findAll(cs.isEmpty() ? new Criteria() : new Criteria().andOperator(cs), Sort.unsorted())
                .stream().filter(c -> c.getApplicationDate() != null)
                .filter(c -> onlySource == null || onlySource.equals(category(c.getHiringSource()))).toList();

        List<String> shown = onlySource == null ? CATEGORIES : List.of(onlySource);

        // requirement hires: selected / joined candidates in a requirement's department and sub-work, up to the number needed
        Map<String, Long> reqHires = zeros(shown);
        List<RequirementRow> reqRows = new ArrayList<>();
        for (HiringRequirement r : requirements.findAllByOrderByIdAsc()) {
            if (departmentId != null && !departmentId.equals(r.getDepartmentId())) continue;
            List<Candidate> hired = all.stream()
                    .filter(c -> isHired(c) && r.getDepartmentId().equals(c.getDepartmentId())
                            && (r.getPosition() == null || r.getPosition().equalsIgnoreCase(c.getPosition() == null ? "" : c.getPosition().trim())))
                    .sorted((a, b) -> a.getApplicationDate().compareTo(b.getApplicationDate()))
                    .limit(r.getNeeded()).toList();
            Map<String, Long> bySource = zeros(shown);
            for (Candidate c : hired) bySource.merge(category(c.getHiringSource()), 1L, Long::sum);
            bySource.forEach((k, v) -> reqHires.merge(k, v, Long::sum));
            String dept = depts.find(r.getDepartmentId()).map(d -> d.getName()).orElse("Department " + r.getDepartmentId());
            reqRows.add(new RequirementRow(r.getId(), dept, r.getPosition(), r.getNeeded(), hired.size(), bySource));
        }

        Map<String, long[]> agg = new LinkedHashMap<>(); // applications, selected (incl. joined), joined, rejected, pending
        for (String s : shown) agg.put(s, new long[5]);
        for (Candidate c : all) {
            long[] a = agg.get(category(c.getHiringSource()));
            a[0]++;
            ApplicationStatus st = c.getApplicationStatus();
            if (isHired(c)) a[1]++;
            if (st == ApplicationStatus.JOINED) a[2]++;
            if (st == ApplicationStatus.REJECTED) a[3]++;
            if (st.isPending()) a[4]++;
        }
        List<SourceRow> rows = new ArrayList<>();
        agg.forEach((s, a) -> rows.add(new SourceRow(s, a[0], pct(a[0], all.size()), a[1], a[2], a[3], a[4], pct(a[1], a[0]),
                reqHires.getOrDefault(s, 0L))));

        String top = rows.stream().filter(r -> r.applications() > 0)
                .max((x, y) -> Long.compare(x.applications(), y.applications())).map(SourceRow::source).orElse(null);
        String topReq = rows.stream().filter(r -> r.requirementHires() > 0)
                .max((x, y) -> Long.compare(x.requirementHires(), y.requirementHires())).map(SourceRow::source).orElse(null);

        // trend over the chosen range, or from the first application to today
        LocalDate start = from != null ? from : all.stream().map(Candidate::getApplicationDate).min(LocalDate::compareTo).orElse(LocalDate.now(clock));
        LocalDate end = to != null ? to : LocalDate.now(clock);
        if (start.isAfter(end)) start = end;
        Granularity g = granularity != null ? granularity : start.plusDays(62).isBefore(end) ? Granularity.MONTH : Granularity.DAY;
        if (g == Granularity.DAY && start.plusDays(366).isBefore(end)) {
            throw ApiException.badRequest("Choose a range of at most one year for the day-by-day view, or switch to monthly");
        }
        List<Period> trend = new ArrayList<>();
        Map<LocalDate, Period> byStart = new LinkedHashMap<>();
        if (g == Granularity.MONTH) {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
            for (YearMonth m = YearMonth.from(start); !m.isAfter(YearMonth.from(end)) && trend.size() < 240; m = m.plusMonths(1)) {
                Period p = new Period(m.format(f), m.atDay(1), m.atEndOfMonth(), zeros(shown), zeros(shown));
                trend.add(p);
                byStart.put(m.atDay(1), p);
            }
        } else {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                Period p = new Period(d.format(f), d, d, zeros(shown), zeros(shown));
                trend.add(p);
                byStart.put(d, p);
            }
        }
        for (Candidate c : all) {
            LocalDate key = g == Granularity.MONTH ? c.getApplicationDate().withDayOfMonth(1) : c.getApplicationDate();
            Period p = byStart.get(key);
            if (p == null) continue;
            String s = category(c.getHiringSource());
            p.applications().merge(s, 1L, Long::sum);
            if (isHired(c)) p.selected().merge(s, 1L, Long::sum);
        }

        return new Analytics(from, to, departmentId, onlySource, g, all.size(), top, topReq, rows, trend, reqRows);
    }

    private static boolean isHired(Candidate c) {
        return c.getApplicationStatus() == ApplicationStatus.SELECTED || c.getApplicationStatus() == ApplicationStatus.JOINED;
    }

    private static double pct(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static Map<String, Long> zeros(List<String> keys) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String k : keys) m.put(k, 0L);
        return m;
    }
}

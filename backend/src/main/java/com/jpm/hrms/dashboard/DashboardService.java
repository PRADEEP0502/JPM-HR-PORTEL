package com.jpm.hrms.dashboard;

import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateDtos.CandidateListItem;
import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.ExperienceBucket;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.candidate.CandidateSpecifications;
import com.jpm.hrms.candidate.CandidateSpecifications.Filter;
import com.jpm.hrms.config.AppProperties;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentRepository;
import com.jpm.hrms.interview.InterviewDtos.InterviewResponse;
import com.jpm.hrms.interview.InterviewRepository;
import com.jpm.hrms.interview.InterviewService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every number on the dashboard is computed from stored candidate / interview data on each request. */
@Service
public class DashboardService {

    public record Summary(long totalCandidates, long todayApplications, long shortlisted, long interviewsScheduled,
                          long selected, long pendingVerification) {
    }

    public record Count(String key, String label, long count) {
    }

    public record DepartmentCount(Long departmentId, String name, long total, long today, long open) {
    }

    /** A pipeline stage: its total plus the most recently updated candidates (preview only). */
    public record Spotlight(String key, String label, long count, List<CandidateListItem> recent) {
    }


    public record DashboardResponse(Instant generatedAt, boolean demoMode, long demoCandidates, Summary summary,
                                    List<Count> departmentWise, List<Count> monthly, List<Count> applicationStatus,
                                    List<Count> interviewStatus, List<Count> experience, List<Count> lastSevenDays,
                                    List<Spotlight> spotlights, List<InterviewResponse> todaysInterviews,
                                    List<InterviewResponse> upcomingInterviews, List<String> interviewersToday,
                                    List<DepartmentCount> departments, List<CandidateListItem> recentCandidates) {
    }

    private final CandidateRepository candidates;
    private final InterviewRepository interviews;
    private final DepartmentRepository departments;
    private final AppProperties props;
    private final Clock clock;
    private final ZoneId zone;

    public DashboardService(CandidateRepository candidates, InterviewRepository interviews, DepartmentRepository departments,
                            AppProperties props, Clock clock, ZoneId businessZone) {
        this.candidates = candidates;
        this.interviews = interviews;
        this.departments = departments;
        this.props = props;
        this.clock = clock;
        this.zone = businessZone;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);

        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (Object[] r : candidates.countGroupedByApplicationStatus()) byStatus.put((ApplicationStatus) r[0], (Long) r[1]);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long upcomingInterviews = interviews.countByStatusInAndScheduledAtAfter(InterviewService.ACTIVE, now);
        long pendingVerification = candidates.countByVerificationStatus(VerificationStatus.PENDING);

        Summary summary = new Summary(total, candidates.countByApplicationDate(today),
                byStatus.getOrDefault(ApplicationStatus.SHORTLISTED, 0L), upcomingInterviews,
                byStatus.getOrDefault(ApplicationStatus.SELECTED, 0L), pendingVerification);

        List<Count> statusCounts = new ArrayList<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            statusCounts.add(new Count(s.name(), s.label, byStatus.getOrDefault(s, 0L)));
        }

        Map<InterviewStatus, Long> byInterview = new EnumMap<>(InterviewStatus.class);
        for (Object[] r : candidates.countGroupedByInterviewStatus()) byInterview.put((InterviewStatus) r[0], (Long) r[1]);
        List<Count> interviewCounts = new ArrayList<>();
        for (InterviewStatus s : InterviewStatus.values()) {
            interviewCounts.add(new Count(s.name(), s.label, byInterview.getOrDefault(s, 0L)));
        }

        Map<ExperienceBucket, Long> byExp = new EnumMap<>(ExperienceBucket.class);
        for (BigDecimal y : candidates.findAllExperienceYears()) byExp.merge(ExperienceBucket.of(y), 1L, Long::sum);
        List<Count> expCounts = new ArrayList<>();
        for (ExperienceBucket b : ExperienceBucket.values()) expCounts.add(new Count(b.name(), b.label, byExp.getOrDefault(b, 0L)));

        YearMonth thisMonth = YearMonth.from(today);
        YearMonth firstMonth = thisMonth.minusMonths(11);
        Map<YearMonth, Long> perMonth = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) perMonth.put(firstMonth.plusMonths(i), 0L);
        Map<LocalDate, Long> perDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) perDay.put(today.minusDays(i), 0L);
        for (LocalDate d : candidates.findApplicationDatesSince(firstMonth.atDay(1))) {
            perMonth.computeIfPresent(YearMonth.from(d), (k, v) -> v + 1);
            perDay.computeIfPresent(d, (k, v) -> v + 1);
        }
        List<Count> monthly = perMonth.entrySet().stream()
                .map(e -> new Count(e.getKey().toString(), e.getKey().getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                        + " " + String.valueOf(e.getKey().getYear()).substring(2), e.getValue())).toList();
        List<Count> week = perDay.entrySet().stream()
                .map(e -> new Count(e.getKey().toString(), e.getKey().getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH), e.getValue()))
                .toList();

        List<DepartmentCount> deptCounts = departmentCounts(today);
        List<Count> deptChart = deptCounts.stream().filter(d -> d.total() > 0)
                .map(d -> new Count(d.departmentId() == null ? "0" : d.departmentId().toString(), d.name(), d.total())).toList();

        List<Spotlight> spotlights = List.of(
                spotlight("PENDING_VERIFICATION", "Pending Verification", pendingVerification,
                        new Filter(null, null, null, null, null, VerificationStatus.PENDING)),
                spotlight("INTERVIEW", "Interview Stage", byInterview.getOrDefault(InterviewStatus.SCHEDULED, 0L)
                                + byInterview.getOrDefault(InterviewStatus.RESCHEDULED, 0L),
                        new Filter(null, null, null, null, List.of(InterviewStatus.SCHEDULED, InterviewStatus.RESCHEDULED), null)),
                spotlight("SELECTED", "Selected", byStatus.getOrDefault(ApplicationStatus.SELECTED, 0L),
                        new Filter(null, null, null, ApplicationStatus.SELECTED, null, null)));

        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        var todayAll = interviews.findByStatusInAndScheduledAtBetweenOrderByScheduledAtAsc(
                List.of(InterviewStatus.values()), dayStart, dayEnd);
        List<InterviewResponse> todays = todayAll.stream().map(InterviewResponse::from).toList();
        List<String> interviewersToday = todayAll.stream().filter(i -> i.getStatus().isActive())
                .map(i -> i.getInterviewer()).distinct().toList();
        List<InterviewResponse> upcoming = interviews.findTop6ByStatusInAndScheduledAtAfterOrderByScheduledAtAsc(InterviewService.ACTIVE, now)
                .stream().map(InterviewResponse::from).toList();

        return new DashboardResponse(now, props.demoMode(),
                candidates.countBySource(com.jpm.hrms.candidate.CandidateEnums.CandidateSource.DEMO), summary,
                deptChart, monthly, statusCounts, interviewCounts, expCounts, week, spotlights, todays, upcoming,
                interviewersToday, deptCounts,
                candidates.findTop8ByOrderByAppliedAtDesc().stream().map(CandidateListItem::from).toList());
    }

    /** Totals, today's applications and still-open (no decision yet) counts per department. */
    public List<DepartmentCount> departmentCounts(LocalDate today) {
        Map<Long, long[]> agg = new HashMap<>(); // [total, open]
        for (Object[] r : candidates.countGroupedByDepartmentAndStatus()) {
            Long id = (Long) r[0];
            ApplicationStatus s = (ApplicationStatus) r[1];
            long n = (Long) r[3];
            long[] a = agg.computeIfAbsent(id, k -> new long[2]);
            a[0] += n;
            if (s.isPending()) a[1] += n;
        }
        Map<Long, Long> todayCounts = new HashMap<>();
        for (Object[] r : candidates.countOnDateByDepartment(today)) todayCounts.put((Long) r[0], (Long) r[1]);
        List<DepartmentCount> out = new ArrayList<>();
        for (Department d : departments.findAllByOrderByNameAsc()) {
            long[] a = agg.getOrDefault(d.getId(), new long[2]);
            if (!d.isActive() && a[0] == 0) continue;
            out.add(new DepartmentCount(d.getId(), d.getName(), a[0], todayCounts.getOrDefault(d.getId(), 0L), a[1]));
        }
        long[] none = agg.get(null);
        if (none != null && none[0] > 0) {
            out.add(new DepartmentCount(null, "Unassigned", none[0], todayCounts.getOrDefault(null, 0L), none[1]));
        }
        out.sort((x, y) -> Long.compare(y.total(), x.total()));
        return out;
    }

    private Spotlight spotlight(String key, String label, long count, Filter f) {
        var page = candidates.findAll(CandidateSpecifications.matching(f),
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return new Spotlight(key, label, count, page.getContent().stream().map(CandidateListItem::from).toList());
    }
}

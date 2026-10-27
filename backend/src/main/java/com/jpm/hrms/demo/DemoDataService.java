package com.jpm.hrms.demo;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.CandidateSource;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.candidate.CandidateService;
import com.jpm.hrms.candidate.HrRemark;
import com.jpm.hrms.candidate.HrRemarkRepository;
import com.jpm.hrms.candidate.RatingChangeRepository;
import com.jpm.hrms.candidate.StatusChangeRepository;
import com.jpm.hrms.communication.CommunicationRepository;
import com.jpm.hrms.config.AppProperties;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentRepository;
import com.jpm.hrms.document.CandidateDocument;
import com.jpm.hrms.document.CandidateDocumentRepository;
import com.jpm.hrms.document.storage.FileStorages;
import com.jpm.hrms.interview.Interview;
import com.jpm.hrms.interview.InterviewRepository;
import com.jpm.hrms.interview.InterviewService;
import com.jpm.hrms.user.AppUser;
import com.jpm.hrms.user.AppUserRepository;
import com.jpm.hrms.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds clearly-labelled sample data (source = DEMO) for demo mode, and removes it again.
 * Demo data is never created when demo mode is off.
 */
@Service
public class DemoDataService {
    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);
    private static final String DEMO_ACTOR = "demo-seed";

    private static final String[] FIRST = {"Aarav", "Diya", "Vihaan", "Ananya", "Arjun", "Ishita", "Karthik", "Meera", "Rohan",
            "Sneha", "Aditya", "Priya", "Siddharth", "Kavya", "Rahul", "Nisha", "Varun", "Pooja", "Nikhil", "Lakshmi",
            "Harish", "Divya", "Manoj", "Swathi", "Pradeep", "Revathi", "Suresh", "Keerthana", "Vignesh", "Aishwarya"};
    private static final String[] LAST = {"Sharma", "Iyer", "Reddy", "Nair", "Kumar", "Menon", "Patel", "Rao", "Gupta",
            "Krishnan", "Subramanian", "Pillai", "Das", "Joshi", "Balaji", "Natarajan", "Mehta", "Venkatesh"};
    private static final String[][] DEPTS = {
            {"Engineering", "Software development and QA"},
            {"Human Resources", "Recruitment, payroll and employee relations"},
            {"Finance", "Accounts, audit and taxation"},
            {"Sales & Marketing", "Business development and brand"},
            {"Operations", "Facilities, logistics and administration"},
            {"Customer Support", "Customer service and success"},
            {"Design", "UI/UX and visual design"}};
    private static final String[] QUALS = {"B.E. Computer Science", "B.Tech IT", "MBA (HR)", "B.Com", "M.Com", "BBA",
            "MCA", "B.Sc Mathematics", "B.Des", "M.Tech", "Diploma in Mechanical Eng.", "CA Inter"};
    private static final String[] COLLEGES = {"Anna University", "PSG College of Technology", "SRM Institute", "VIT Vellore",
            "Madras Christian College", "Loyola College", "Amrita Vishwa Vidyapeetham", "NIT Trichy", "Bharathiar University"};
    private static final String[] COMPANIES = {"Infosys", "TCS", "Zoho", "Freshworks", "HCL", "Wipro", "Cognizant", "L&T", "Ramco Systems"};
    private static final String[] DESIGNATIONS = {"Software Engineer", "HR Executive", "Accountant", "Sales Executive",
            "Operations Associate", "Support Engineer", "UI Designer", "Analyst", "Team Lead"};
    private static final String[] SKILLS = {"Java, Spring Boot, SQL", "React, TypeScript, CSS", "Recruitment, Onboarding, HRIS",
            "Tally, GST, Excel", "Negotiation, CRM, Lead generation", "Figma, Prototyping, User research",
            "Customer handling, Zendesk", "Supply chain, Inventory, SAP"};
    private static final String[] INTERVIEWERS = {"Ramesh Kumar", "Anitha Raj", "Sanjay Menon", "Deepa Srinivasan", "Vikram Shah"};
    private static final String[] ROUNDS = {"Screening", "Technical Round", "HR Round", "Final Round"};
    private static final String[] REMARKS = {"Strong communication skills.", "Good technical fundamentals; probe system design.",
            "Salary expectation slightly above band.", "Available to join in 30 days.", "Requested documents verification.",
            "Referred by an existing employee."};

    private final AppProperties props;
    private final CandidateRepository candidates;
    private final DepartmentRepository departments;
    private final InterviewRepository interviews;
    private final HrRemarkRepository remarks;
    private final CandidateDocumentRepository documents;
    private final AppUserRepository users;
    private final InterviewService interviewService;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final CommunicationRepository communications;
    private final FileStorages storages;
    private final StatusChangeRepository statusHistory;
    private final RatingChangeRepository ratingHistory;
    private final Clock clock;
    private final ZoneId zone;

    public DemoDataService(AppProperties props, CandidateRepository candidates, DepartmentRepository departments,
                           InterviewRepository interviews, HrRemarkRepository remarks, CandidateDocumentRepository documents,
                           AppUserRepository users, InterviewService interviewService, PasswordEncoder encoder,
                           AuditService audit, CommunicationRepository communications,
                           StatusChangeRepository statusHistory, RatingChangeRepository ratingHistory, FileStorages storages,
                           Clock clock, ZoneId businessZone) {
        this.storages = storages;
        this.statusHistory = statusHistory;
        this.ratingHistory = ratingHistory;
        this.props = props;
        this.candidates = candidates;
        this.departments = departments;
        this.interviews = interviews;
        this.remarks = remarks;
        this.documents = documents;
        this.users = users;
        this.interviewService = interviewService;
        this.encoder = encoder;
        this.audit = audit;
        this.communications = communications;
        this.clock = clock;
        this.zone = businessZone;
    }

    private static final String[][] FAMILY = {
            {"Father", "Ramesh", "Suresh", "Mohan", "Venkat", "Rajan"},
            {"Mother", "Lakshmi", "Meena", "Geetha", "Kamala", "Saroja"},
            {"Spouse", "Divya", "Karthik", "Priya", "Arun", "Anitha"},
            {"Brother", "Vijay", "Ganesh", "Ravi", "Ashok", "Senthil"},
            {"Sister", "Kavitha", "Revathi", "Deepa", "Sangeetha", "Uma"}};

    /** Fills a sample family reference on demo candidates that do not have one (never touches real candidates). */
    public void backfillDemoFamilyReferences() {
        if (!props.demoMode()) return;
        int filled = 0;
        for (Candidate c : candidates.findBySource(CandidateSource.DEMO)) {
            if (c.getFamilyRefName() != null) continue;
            if (applyFamilyReference(c, new Random(c.getId() * 31L))) {
                candidates.save(c);
                filled++;
            }
        }
        if (filled > 0) log.info("Demo mode: added sample family references to {} demo candidates", filled);
    }

    private static boolean applyFamilyReference(Candidate c, Random rnd) {
        if (rnd.nextInt(10) == 0) return false; // a few candidates leave it blank, as real applicants may
        String[] fam = FAMILY[rnd.nextInt(FAMILY.length)];
        String last = c.getFullName().contains(" ") ? c.getFullName().substring(c.getFullName().lastIndexOf(' ') + 1) : "";
        c.setFamilyRefName((fam[1 + rnd.nextInt(fam.length - 1)] + " " + last).trim());
        c.setFamilyRefRelation(fam[0]);
        c.setFamilyRefContact("9" + String.format("%09d", 100000000 + rnd.nextInt(899999999)));
        return true;
    }

    public void seedIfEmpty() {
        if (!props.demoMode() || candidates.count() > 0) return;
        log.info("Demo mode: seeding sample data (all rows are marked source=DEMO)");
        if (!users.existsByUsernameIgnoreCase("hr")) {
            AppUser hr = new AppUser();
            hr.setUsername("hr");
            hr.setFullName("Demo HR Manager");
            hr.setRole(Role.HR);
            hr.setPasswordHash(encoder.encode("Hr@12345"));
            users.save(hr);
        }
        List<Department> depts = new ArrayList<>();
        for (String[] d : DEPTS) {
            depts.add(departments.findByNameIgnoreCase(d[0]).orElseGet(() -> departments.save(new Department(d[0], d[1]))));
        }

        Random rnd = new Random(42);
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        int total = 64;
        for (int n = 0; n < total; n++) {
            Candidate c = new Candidate();
            String first = FIRST[rnd.nextInt(FIRST.length)];
            String last = LAST[rnd.nextInt(LAST.length)];
            c.setFullName(first + " " + last);
            c.setEmail((first + "." + last + n).toLowerCase() + "@example.com");
            c.setMobile("9" + String.format("%09d", 100000000 + rnd.nextInt(899999999)));
            c.setDateOfBirth(today.minusYears(21 + rnd.nextInt(18)).minusDays(rnd.nextInt(365)));
            c.setAddress((10 + rnd.nextInt(200)) + ", " + new String[]{"Anna Nagar", "T. Nagar", "Velachery", "Adyar", "Guindy"}[rnd.nextInt(5)] + ", Chennai");
            c.setQualification(QUALS[rnd.nextInt(QUALS.length)]);
            c.setInstitution(COLLEGES[rnd.nextInt(COLLEGES.length)]);
            c.setPassingYear(today.getYear() - rnd.nextInt(14));
            int expBucket = rnd.nextInt(10);
            BigDecimal exp = expBucket < 2 ? BigDecimal.ZERO : expBucket == 9 ? null
                    : BigDecimal.valueOf(rnd.nextInt(150) / 10.0 + 0.5).setScale(1, java.math.RoundingMode.HALF_UP);
            c.setExperienceYears(exp);
            if (exp != null && exp.signum() > 0) {
                c.setPreviousCompany(COMPANIES[rnd.nextInt(COMPANIES.length)]);
                c.setPreviousDesignation(DESIGNATIONS[rnd.nextInt(DESIGNATIONS.length)]);
            }
            c.setSkills(SKILLS[rnd.nextInt(SKILLS.length)]);
            c.setExpectedSalary("₹" + (3 + rnd.nextInt(18)) + " LPA");
            c.setDepartment(depts.get(n == 0 ? 0 : weightedDept(rnd)));
            if (rnd.nextInt(3) == 0) {
                c.setReferenceName(FIRST[rnd.nextInt(FIRST.length)] + " " + LAST[rnd.nextInt(LAST.length)]);
                c.setReferenceRelation("Former manager");
                c.setReferenceContact("98" + String.format("%08d", rnd.nextInt(99999999)));
            }
            // Spread applications over the last 12 months, weighted toward recent weeks; a few today.
            int daysAgo = n < 4 ? 0 : rnd.nextInt(10) < 4 ? rnd.nextInt(14) : rnd.nextInt(360);
            LocalDate applied = today.minusDays(daysAgo);
            c.setApplicationDate(applied);
            c.setAppliedAt(applied.atTime(LocalTime.of(9 + rnd.nextInt(9), rnd.nextInt(60))).atZone(zone).toInstant());
            ApplicationStatus status = daysAgo < 3 ? ApplicationStatus.NEW
                    : new ApplicationStatus[]{ApplicationStatus.NEW, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.UNDER_REVIEW,
                    ApplicationStatus.SHORTLISTED, ApplicationStatus.SHORTLISTED, ApplicationStatus.SHORTLISTED,
                    ApplicationStatus.SELECTED, ApplicationStatus.SELECTED, ApplicationStatus.REJECTED,
                    ApplicationStatus.REJECTED, ApplicationStatus.ON_HOLD}[rnd.nextInt(11)];
            c.setApplicationStatus(status);
            c.setVerificationStatus(status == ApplicationStatus.SELECTED || rnd.nextInt(3) == 0
                    ? VerificationStatus.VERIFIED : VerificationStatus.PENDING);
            if (rnd.nextInt(12) == 0) {
                c.setDataIssues("unreadable date of birth \"31/02/1998\"");
                c.setDateOfBirth(null);
            }
            applyFamilyReference(c, new Random(n * 31L + 7));
            c.setSource(CandidateSource.DEMO);
            candidates.save(c);
            CandidateService.assignCode(c, candidates);
            candidates.save(c);

            if (rnd.nextInt(4) != 0) {
                String file = c.getFullName().replace(' ', '_') + "_Resume.pdf";
                storeDemoPdf(c, file);
            }
            if (status != ApplicationStatus.NEW && rnd.nextInt(2) == 0) {
                remarks.save(new HrRemark(c, "hr", REMARKS[rnd.nextInt(REMARKS.length)]));
            }
            seedInterviews(c, status, rnd, today, now);
            interviewService.refreshCandidateInterviewStatus(c);
            audit.recordAs(DEMO_ACTOR, "CANDIDATE_IMPORTED", "CANDIDATE", c.getId(), "Demo candidate created (demo mode)");
        }
    }

    private void seedInterviews(Candidate c, ApplicationStatus status, Random rnd, LocalDate today, Instant now) {
        switch (status) {
            case SELECTED, REJECTED -> {
                addInterview(c, rnd, pastSlot(rnd, today, 5 + rnd.nextInt(40)), "Technical Round", InterviewStatus.COMPLETED,
                        status == ApplicationStatus.SELECTED ? "Cleared - recommended for HR round" : "Did not meet the technical bar");
                if (status == ApplicationStatus.SELECTED) {
                    addInterview(c, rnd, pastSlot(rnd, today, 1 + rnd.nextInt(4)), "HR Round", InterviewStatus.COMPLETED, "Offer discussed");
                }
            }
            case SHORTLISTED -> {
                int pick = rnd.nextInt(4);
                if (pick == 0) addInterview(c, rnd, todaySlot(rnd, today, now), ROUNDS[rnd.nextInt(3)], InterviewStatus.SCHEDULED, null);
                else if (pick == 1) addInterview(c, rnd, futureSlot(rnd, today, 1 + rnd.nextInt(10)), ROUNDS[rnd.nextInt(3)],
                        rnd.nextBoolean() ? InterviewStatus.SCHEDULED : InterviewStatus.RESCHEDULED, null);
                else if (pick == 2) addInterview(c, rnd, pastSlot(rnd, today, 2 + rnd.nextInt(10)), "Screening",
                        rnd.nextInt(3) == 0 ? InterviewStatus.CANCELLED : InterviewStatus.COMPLETED, "Candidate requested a later slot");
            }
            case ON_HOLD -> addInterview(c, rnd, pastSlot(rnd, today, 3 + rnd.nextInt(20)), "Screening", InterviewStatus.COMPLETED, "On hold pending headcount approval");
            default -> { }
        }
    }

    private void addInterview(Candidate c, Random rnd, Instant at, String round, InterviewStatus status, String remarksText) {
        Interview i = new Interview();
        i.setCandidate(c);
        i.setScheduledAt(at);
        i.setDurationMinutes(new int[]{30, 45, 60}[rnd.nextInt(3)]);
        i.setRoundName(round);
        i.setInterviewer(INTERVIEWERS[rnd.nextInt(INTERVIEWERS.length)]);
        i.setMode(Interview.Mode.values()[rnd.nextInt(3)]);
        i.setLocation(i.getMode() == Interview.Mode.IN_PERSON ? "JPM Office, Conference Room " + (1 + rnd.nextInt(4)) : null);
        i.setStatus(status);
        i.setRemarks(remarksText);
        i.setCreatedBy("hr");
        interviews.save(i);
    }

    private Instant pastSlot(Random rnd, LocalDate today, int daysAgo) {
        return today.minusDays(daysAgo).atTime(10 + rnd.nextInt(7), rnd.nextBoolean() ? 0 : 30).atZone(zone).toInstant();
    }

    private Instant futureSlot(Random rnd, LocalDate today, int daysAhead) {
        return today.plusDays(daysAhead).atTime(10 + rnd.nextInt(7), rnd.nextBoolean() ? 0 : 30).atZone(zone).toInstant();
    }

    /** A slot later today if office hours remain, otherwise tomorrow morning. */
    private Instant todaySlot(Random rnd, LocalDate today, Instant now) {
        Instant slot = today.atTime(15 + rnd.nextInt(4), rnd.nextBoolean() ? 0 : 30).atZone(zone).toInstant();
        return slot.isAfter(now) ? slot : futureSlot(rnd, today, 1);
    }

    private static int weightedDept(Random rnd) {
        int r = rnd.nextInt(100);
        if (r < 34) return 0;
        if (r < 44) return 1;
        if (r < 54) return 2;
        if (r < 72) return 3;
        if (r < 82) return 4;
        if (r < 92) return 5;
        return 6;
    }

    private void storeDemoPdf(Candidate c, String fileName) {
        String text = "DEMO RESUME - " + c.getFullName() + " - sample file generated in demo mode";
        String stream = "BT /F1 14 Tf 50 750 Td (" + text.replace("(", "").replace(")", "") + ") Tj ET";
        String pdf = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n"
                + "4 0 obj<</Length " + stream.length() + ">>stream\n" + stream + "\nendstream endobj\n"
                + "5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF";
        try {
            byte[] bytes = pdf.getBytes(StandardCharsets.ISO_8859_1);
            var storage = storages.forNewFiles();
            String key = storage.store(String.valueOf(c.getId()), "pdf", bytes);
            documents.save(new CandidateDocument(c, CandidateDocument.DocType.RESUME, storage.kind(),
                    fileName, "application/pdf", (long) bytes.length, key, DEMO_ACTOR));
        } catch (IOException e) {
            log.warn("Could not write demo resume file: {}", e.getMessage());
        }
    }

    /** Permanently removes every DEMO-sourced candidate (and their interviews, remarks and documents). */
    public int purge() {
        List<Candidate> demo = candidates.findBySource(CandidateSource.DEMO);
        if (demo.isEmpty()) return 0;
        List<Long> ids = demo.stream().map(Candidate::getId).toList();
        communications.deleteByCandidateIdIn(ids);
        interviews.deleteByCandidateIdIn(ids);
        remarks.deleteByCandidateIdIn(ids);
        statusHistory.deleteByCandidateIdIn(ids);
        ratingHistory.deleteByCandidateIdIn(ids);
        documents.deleteByCandidateIdIn(ids);
        candidates.deleteAllById(ids);
        audit.record("DEMO_DATA_PURGED", "CANDIDATE", null, "Removed " + ids.size() + " demo candidates");
        return ids.size();
    }
}

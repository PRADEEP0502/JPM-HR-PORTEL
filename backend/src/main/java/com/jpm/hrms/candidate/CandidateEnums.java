package com.jpm.hrms.candidate;

import java.math.BigDecimal;

/** Small value types shared across the candidate module. */
public final class CandidateEnums {
    private CandidateEnums() {
    }

    public enum ApplicationStatus {
        // NEW is shown as "Applied"; the stored value stays NEW so existing records need no migration.
        NEW("Applied"), UNDER_REVIEW("Under Review"), SHORTLISTED("Shortlisted"),
        INTERVIEW_SCHEDULED("Interview Scheduled"), INTERVIEW_COMPLETED("Interview Completed"),
        SELECTED("Selected"), REJECTED("Rejected"), ON_HOLD("On Hold"), JOINED("Joined");

        public final String label;

        ApplicationStatus(String label) {
            this.label = label;
        }

        /** Statuses where no final hiring decision (selected, rejected, joined) has been made yet. */
        public boolean isPending() {
            return this != SELECTED && this != REJECTED && this != JOINED;
        }
    }

    public enum InterviewStatus {
        NOT_SCHEDULED("Not Scheduled"), SCHEDULED("Scheduled"), COMPLETED("Completed"),
        RESCHEDULED("Rescheduled"), CANCELLED("Cancelled");

        public final String label;

        InterviewStatus(String label) {
            this.label = label;
        }

        public boolean isActive() {
            return this == SCHEDULED || this == RESCHEDULED;
        }
    }

    public enum VerificationStatus {
        PENDING("Pending"), VERIFIED("Verified");

        public final String label;

        VerificationStatus(String label) {
            this.label = label;
        }
    }

    /** Where a candidate record came from. DEMO rows exist only in demo mode. */
    public enum CandidateSource {
        GOOGLE_SHEETS, MANUAL, DEMO
    }

    /** Experience ranges in years: (minExclusive, maxInclusive]. FRESHER is exactly 0. */
    public enum ExperienceBucket {
        FRESHER("Fresher", null, BigDecimal.ZERO),
        Y0_2("Up to 2 yrs", BigDecimal.ZERO, new BigDecimal("2")),
        Y2_5("2–5 yrs", new BigDecimal("2"), new BigDecimal("5")),
        Y5_10("5–10 yrs", new BigDecimal("5"), new BigDecimal("10")),
        Y10_PLUS("10+ yrs", new BigDecimal("10"), null),
        UNKNOWN("Not provided", null, null);

        public final String label;
        public final BigDecimal minExclusive;
        public final BigDecimal maxInclusive;

        ExperienceBucket(String label, BigDecimal minExclusive, BigDecimal maxInclusive) {
            this.label = label;
            this.minExclusive = minExclusive;
            this.maxInclusive = maxInclusive;
        }

        public static ExperienceBucket of(BigDecimal years) {
            if (years == null) return UNKNOWN;
            if (years.signum() == 0) return FRESHER;
            for (ExperienceBucket b : values()) {
                if (b == FRESHER || b == UNKNOWN) continue;
                boolean aboveMin = b.minExclusive == null || years.compareTo(b.minExclusive) > 0;
                boolean belowMax = b.maxInclusive == null || years.compareTo(b.maxInclusive) <= 0;
                if (aboveMin && belowMax) return b;
            }
            return UNKNOWN;
        }
    }
}

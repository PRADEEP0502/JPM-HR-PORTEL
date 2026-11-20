package com.jpm.hrms.candidate;

import java.time.Instant;

/**
 * HR's record that they checked a candidate's Aadhaar by hand: how it was checked, what they found, who did it and
 * when. Stored inside the candidate. Only the last 4 digits may be kept (as on a masked Aadhaar), never the full number.
 */
public class AadhaarVerification {

    public enum Result {
        VERIFIED("Verified"), MISMATCH("Mismatch found");

        public final String label;

        Result(String label) {
            this.label = label;
        }
    }

    public enum Method {
        ORIGINAL_SEEN("Original card checked in person"),
        COPY_CHECKED("Uploaded copy checked"),
        DIGILOCKER("DigiLocker / e-Aadhaar checked"),
        QR_SCANNED("QR code scanned");

        public final String label;

        Method(String label) {
            this.label = label;
        }
    }

    private Result result;
    private Method method;
    /** Last 4 digits of the Aadhaar number as seen by HR; optional. */
    private String last4;
    private String note;
    private String verifiedBy;
    private Instant verifiedAt;

    public AadhaarVerification() {
    }

    public AadhaarVerification(Result result, Method method, String last4, String note, String verifiedBy, Instant verifiedAt) {
        this.result = result;
        this.method = method;
        this.last4 = last4;
        this.note = note;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = verifiedAt;
    }

    public Result getResult() { return result; }
    public Method getMethod() { return method; }
    public String getLast4() { return last4; }
    public String getNote() { return note; }
    public String getVerifiedBy() { return verifiedBy; }
    public Instant getVerifiedAt() { return verifiedAt; }
}

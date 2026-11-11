package com.jpm.hrms.candidate;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.communication.CommunicationRepository;
import com.jpm.hrms.document.CandidateDocumentRepository;
import com.jpm.hrms.interview.InterviewRepository;
import com.jpm.hrms.sync.IgnoredRow;
import com.jpm.hrms.sync.IgnoredRowRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Deletes every candidate with their interviews, remarks, status and rating history, messages and uploaded files.
 * A full copy goes to the recycle bin first, so the deletion can be undone from Settings for 30 days; stored files are
 * removed only when that copy expires. Departments, users, settings and templates are kept. Google Form rows already imported are remembered as ignored,
 * so the next sync imports only new responses instead of bringing the deleted candidates back.
 */
@Service
public class CandidateResetService {

    private final CandidateRepository candidates;
    private final InterviewRepository interviews;
    private final HrRemarkRepository remarks;
    private final StatusChangeRepository statusHistory;
    private final RatingChangeRepository ratingHistory;
    private final CommunicationRepository communications;
    private final CandidateDocumentRepository documents;
    private final IgnoredRowRepository ignoredRows;
    private final AuditService audit;
    private final com.jpm.hrms.backup.RecycleBinService recycleBin;

    public CandidateResetService(CandidateRepository candidates, InterviewRepository interviews, HrRemarkRepository remarks,
                                 StatusChangeRepository statusHistory, RatingChangeRepository ratingHistory,
                                 CommunicationRepository communications, CandidateDocumentRepository documents,
                                 IgnoredRowRepository ignoredRows, AuditService audit,
                                 com.jpm.hrms.backup.RecycleBinService recycleBin) {
        this.recycleBin = recycleBin;
        this.candidates = candidates;
        this.interviews = interviews;
        this.remarks = remarks;
        this.statusHistory = statusHistory;
        this.ratingHistory = ratingHistory;
        this.communications = communications;
        this.documents = documents;
        this.ignoredRows = ignoredRows;
        this.audit = audit;
    }

    public int deleteAll(String actor) {
        List<Candidate> all = candidates.findAll();
        if (all.isEmpty()) return 0;
        List<Long> ids = all.stream().map(Candidate::getId).toList();
        List<String> newlyIgnored = all.stream().map(Candidate::getSourceRowKey)
                .filter(k -> k != null && !ignoredRows.existsById(k)).distinct().toList();
        // copy first: if this fails nothing is deleted
        recycleBin.keep(actor, ids, newlyIgnored);
        for (String key : newlyIgnored) {
            ignoredRows.save(new IgnoredRow(key, "Candidate removed by \"Delete all candidates\" (" + actor + ")"));
        }
        communications.deleteByCandidateIdIn(ids);
        interviews.deleteByCandidateIdIn(ids);
        remarks.deleteByCandidateIdIn(ids);
        statusHistory.deleteByCandidateIdIn(ids);
        ratingHistory.deleteByCandidateIdIn(ids);
        documents.deleteByCandidateIdIn(ids);
        candidates.deleteAllById(ids);
        audit.record("CANDIDATES_RESET", "CANDIDATE", null, "Deleted all " + ids.size() + " candidates");
        return ids.size();
    }
}

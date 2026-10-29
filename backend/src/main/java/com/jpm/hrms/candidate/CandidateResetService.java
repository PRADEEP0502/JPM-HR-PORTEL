package com.jpm.hrms.candidate;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.communication.CommunicationRepository;
import com.jpm.hrms.document.CandidateDocument;
import com.jpm.hrms.document.CandidateDocumentRepository;
import com.jpm.hrms.document.storage.FileStorages;
import com.jpm.hrms.interview.InterviewRepository;
import com.jpm.hrms.sync.IgnoredRow;
import com.jpm.hrms.sync.IgnoredRowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Deletes every candidate with their interviews, remarks, status and rating history, messages and uploaded files.
 * Departments, users, settings and templates are kept. Google Form rows already imported are remembered as ignored,
 * so the next sync imports only new responses instead of bringing the deleted candidates back.
 */
@Service
public class CandidateResetService {
    private static final Logger log = LoggerFactory.getLogger(CandidateResetService.class);

    private final CandidateRepository candidates;
    private final InterviewRepository interviews;
    private final HrRemarkRepository remarks;
    private final StatusChangeRepository statusHistory;
    private final RatingChangeRepository ratingHistory;
    private final CommunicationRepository communications;
    private final CandidateDocumentRepository documents;
    private final FileStorages storages;
    private final IgnoredRowRepository ignoredRows;
    private final AuditService audit;

    public CandidateResetService(CandidateRepository candidates, InterviewRepository interviews, HrRemarkRepository remarks,
                                 StatusChangeRepository statusHistory, RatingChangeRepository ratingHistory,
                                 CommunicationRepository communications, CandidateDocumentRepository documents,
                                 FileStorages storages, IgnoredRowRepository ignoredRows, AuditService audit) {
        this.candidates = candidates;
        this.interviews = interviews;
        this.remarks = remarks;
        this.statusHistory = statusHistory;
        this.ratingHistory = ratingHistory;
        this.communications = communications;
        this.documents = documents;
        this.storages = storages;
        this.ignoredRows = ignoredRows;
        this.audit = audit;
    }

    public int deleteAll(String actor) {
        List<Candidate> all = candidates.findAll();
        if (all.isEmpty()) return 0;
        List<Long> ids = all.stream().map(Candidate::getId).toList();
        for (Candidate c : all) {
            if (c.getSourceRowKey() != null && !ignoredRows.existsById(c.getSourceRowKey())) {
                ignoredRows.save(new IgnoredRow(c.getSourceRowKey(), "Candidate removed by \"Delete all candidates\" (" + actor + ")"));
            }
        }
        for (CandidateDocument d : documents.findAll()) {
            if (!ids.contains(d.getCandidateId())) continue;
            try {
                storages.of(d.getStorage()).delete(d.getStorageRef());
            } catch (IOException | RuntimeException e) {
                log.warn("Could not delete stored file {} of candidate {}: {}", d.getStorageRef(), d.getCandidateId(), e.getMessage());
            }
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

package com.jpm.hrms.candidate;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.CandidateDtos.ArchiveRequest;
import com.jpm.hrms.candidate.CandidateDtos.CandidateDetail;
import com.jpm.hrms.candidate.CandidateDtos.CandidateListItem;
import com.jpm.hrms.candidate.CandidateDtos.DocumentResponse;
import com.jpm.hrms.candidate.CandidateDtos.RatingRequest;
import com.jpm.hrms.candidate.CandidateDtos.RemarkRequest;
import com.jpm.hrms.candidate.CandidateDtos.RemarkResponse;
import com.jpm.hrms.candidate.CandidateDtos.StatusUpdateRequest;
import com.jpm.hrms.candidate.CandidateDtos.UpdateCandidateRequest;
import com.jpm.hrms.common.PageResponse;
import com.jpm.hrms.department.DepartmentDirectory;
import com.jpm.hrms.document.CandidateDocument.DocType;
import com.jpm.hrms.document.DocumentService;
import com.jpm.hrms.report.TableExporter;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class CandidateController {
    private final CandidateService service;
    private final DocumentService documents;
    private final AuditService audit;
    private final DepartmentDirectory depts;

    public CandidateController(CandidateService service, DocumentService documents, AuditService audit,
                               DepartmentDirectory depts) {
        this.depts = depts;
        this.service = service;
        this.documents = documents;
        this.audit = audit;
    }

    @GetMapping("/candidates")
    public PageResponse<CandidateListItem> list(CandidateQuery query,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(defaultValue = "applicationDate") String sort,
                                                @RequestParam(defaultValue = "desc") String dir) {
        return service.list(query.toFilter(), page, size, sort, dir);
    }

    /**
     * Exports the currently filtered list as Excel or CSV ({@code format=csv}). Personal details beyond contact info
     * (DOB, address, salary), internal rating notes and credentials are never exported.
     */
    @GetMapping("/candidates/export")
    public ResponseEntity<byte[]> export(CandidateQuery query,
                                         @RequestParam(defaultValue = "applicationDate") String sort,
                                         @RequestParam(defaultValue = "desc") String dir,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        List<Candidate> all = service.listAll(query.toFilter(), sort, dir);
        DepartmentDirectory.Names names = depts.snapshot();
        List<List<Object>> rows = new ArrayList<>();
        for (Candidate c : all) {
            List<Object> r = new ArrayList<>();
            r.add(c.getCandidateCode());
            r.add(c.getFullName());
            r.add(c.getMobile());
            r.add(c.getEmail());
            r.add(names.nameOrRaw(c));
            r.add(c.getQualification());
            r.add(c.getExperienceYears());
            r.add(c.getApplicationDate());
            r.add(c.getProfileRating() == null ? "Not Rated" : c.getProfileRating());
            r.add(c.getApplicationStatus().label);
            r.add(c.getInterviewStatus().label);
            r.add(c.getVerificationStatus().label);
            rows.add(r);
        }
        var response = TableExporter.export(format, "Candidates", "JPM HR Portal - Candidate List (" + all.size() + ")",
                List.of("Candidate ID", "Name", "Mobile", "Email", "Department", "Qualification", "Experience (yrs)",
                        "Application Date", "Profile Match Rating", "Application Status", "Interview Status", "Verification"),
                rows, "candidates");
        audit.record("EXPORT", "CANDIDATE", null, "Exported " + all.size() + " candidates to " + format.toUpperCase());
        return response;
    }

    /** Archives (hides from the active list) or restores a candidate. Nothing is deleted. */
    @PatchMapping("/candidates/{id}/archive")
    public CandidateDetail archive(@PathVariable Long id, @Valid @RequestBody ArchiveRequest req, Authentication auth) {
        return service.archive(id, req, auth.getName());
    }

    @GetMapping("/candidates/{id}")
    public CandidateDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PutMapping("/candidates/{id}")
    public CandidateDetail update(@PathVariable Long id, @Valid @RequestBody UpdateCandidateRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/candidates/{id}/status")
    public CandidateDetail updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req, Authentication auth) {
        return service.updateStatus(id, req, auth.getName());
    }

    /** Profile Match Rating (1-10), set by HR / admin after reviewing the application. Never exposed to candidates. */
    @PatchMapping("/candidates/{id}/rating")
    public CandidateDetail rate(@PathVariable Long id, @Valid @RequestBody RatingRequest req, Authentication auth) {
        return service.rate(id, req, auth.getName());
    }

    @PostMapping("/candidates/{id}/remarks")
    @ResponseStatus(HttpStatus.CREATED)
    public RemarkResponse addRemark(@PathVariable Long id, @Valid @RequestBody RemarkRequest req, Authentication auth) {
        return service.addRemark(id, req.remark(), auth.getName());
    }

    @PostMapping(value = "/candidates/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@PathVariable Long id, @RequestParam DocType type,
                                   @RequestParam(defaultValue = "false") boolean consent,
                                   @RequestPart("file") MultipartFile file, Authentication auth) {
        return documents.upload(id, type, file, consent, auth.getName());
    }

    @GetMapping("/candidates/{id}/resume")
    public ResponseEntity<InputStreamResource> resume(@PathVariable Long id) {
        return documents.downloadLatestResume(id);
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable Long documentId) {
        documents.delete(documentId);
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long documentId) {
        return documents.download(documentId);
    }
}

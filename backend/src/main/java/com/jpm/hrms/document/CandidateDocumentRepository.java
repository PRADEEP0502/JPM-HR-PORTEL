package com.jpm.hrms.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateDocumentRepository extends JpaRepository<CandidateDocument, Long> {
    List<CandidateDocument> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    Optional<CandidateDocument> findFirstByCandidateIdAndDocTypeOrderByCreatedAtDesc(Long candidateId, CandidateDocument.DocType type);
}

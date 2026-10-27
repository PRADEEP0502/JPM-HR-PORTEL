package com.jpm.hrms.document;

import com.jpm.hrms.common.CriteriaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateDocumentRepository extends CriteriaRepository<CandidateDocument, Long> {
    List<CandidateDocument> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    Optional<CandidateDocument> findFirstByCandidateIdAndDocTypeOrderByCreatedAtDesc(Long candidateId, CandidateDocument.DocType type);

    void deleteByCandidateIdIn(List<Long> candidateIds);
}

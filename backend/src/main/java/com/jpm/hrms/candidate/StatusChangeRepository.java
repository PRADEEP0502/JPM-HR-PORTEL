package com.jpm.hrms.candidate;

import com.jpm.hrms.common.CriteriaRepository;

import java.util.Collection;
import java.util.List;

public interface StatusChangeRepository extends CriteriaRepository<StatusChange, Long> {
    List<StatusChange> findByCandidateIdOrderByChangedAtDesc(Long candidateId);

    void deleteByCandidateIdIn(Collection<Long> candidateIds);
}

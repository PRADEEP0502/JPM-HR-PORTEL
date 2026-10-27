package com.jpm.hrms.candidate;

import com.jpm.hrms.common.CriteriaRepository;

import java.util.List;

public interface HrRemarkRepository extends CriteriaRepository<HrRemark, Long> {
    List<HrRemark> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    void deleteByCandidateIdIn(List<Long> candidateIds);
}

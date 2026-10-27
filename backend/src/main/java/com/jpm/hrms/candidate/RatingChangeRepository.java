package com.jpm.hrms.candidate;

import com.jpm.hrms.common.CriteriaRepository;

import java.util.Collection;
import java.util.List;

public interface RatingChangeRepository extends CriteriaRepository<RatingChange, Long> {
    List<RatingChange> findByCandidateIdOrderByRatedAtDesc(Long candidateId);

    void deleteByCandidateIdIn(Collection<Long> candidateIds);
}

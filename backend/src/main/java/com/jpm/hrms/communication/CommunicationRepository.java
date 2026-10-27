package com.jpm.hrms.communication;

import com.jpm.hrms.common.CriteriaRepository;

import java.util.Collection;
import java.util.List;

public interface CommunicationRepository extends CriteriaRepository<Communication, Long> {
    List<Communication> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    long deleteByCandidateIdIn(Collection<Long> ids);
}

package com.jpm.hrms.candidate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HrRemarkRepository extends JpaRepository<HrRemark, Long> {
    List<HrRemark> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);
}

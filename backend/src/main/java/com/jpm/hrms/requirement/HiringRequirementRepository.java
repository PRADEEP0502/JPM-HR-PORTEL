package com.jpm.hrms.requirement;

import com.jpm.hrms.common.CriteriaRepository;

import java.util.List;

public interface HiringRequirementRepository extends CriteriaRepository<HiringRequirement, Long> {
    List<HiringRequirement> findAllByOrderByIdAsc();

    List<HiringRequirement> findByDepartmentId(Long departmentId);
}

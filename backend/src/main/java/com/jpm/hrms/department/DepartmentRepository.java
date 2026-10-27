package com.jpm.hrms.department;

import com.jpm.hrms.common.CriteriaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends CriteriaRepository<Department, Long> {
    Optional<Department> findByNameIgnoreCase(String name);

    List<Department> findAllByOrderByNameAsc();
}

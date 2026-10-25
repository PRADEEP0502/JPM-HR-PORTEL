package com.jpm.hrms.sync;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IgnoredRowRepository extends JpaRepository<IgnoredRow, String> {
}

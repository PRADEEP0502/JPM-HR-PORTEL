package com.jpm.hrms.user;

import com.jpm.hrms.common.CriteriaRepository;

import java.util.Optional;

public interface AppUserRepository extends CriteriaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleAndEnabledTrue(Role role);
}

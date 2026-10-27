package com.jpm.hrms.department;

import com.jpm.hrms.candidate.Candidate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves department names for candidates. Departments are a small collection, so a request loads them once
 * with {@link #snapshot()} instead of looking one up per candidate.
 */
@Component
public class DepartmentDirectory {
    private final DepartmentRepository departments;

    public DepartmentDirectory(DepartmentRepository departments) {
        this.departments = departments;
    }

    public Names snapshot() {
        Map<Long, Department> byId = new HashMap<>();
        departments.findAll().forEach(d -> byId.put(d.getId(), d));
        return new Names(byId);
    }

    public Optional<Department> find(Long id) {
        return id == null ? Optional.empty() : departments.findById(id);
    }

    /** Department lookups for one request. */
    public record Names(Map<Long, Department> byId) {
        /** The configured department's name, or null when the candidate has none. */
        public String name(Candidate c) {
            Department d = c.getDepartmentId() == null ? null : byId.get(c.getDepartmentId());
            return d == null ? null : d.getName();
        }

        /** The configured name, falling back to the department text the candidate submitted. */
        public String nameOrRaw(Candidate c) {
            String n = name(c);
            return n != null ? n : c.getDepartmentRaw();
        }
    }
}

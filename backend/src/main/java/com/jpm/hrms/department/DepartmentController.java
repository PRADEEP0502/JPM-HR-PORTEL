package com.jpm.hrms.department;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    public record DepartmentRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description, boolean active,
                                    @Size(max = 50) List<@Size(max = 100) String> positions) {
    }

    public record DepartmentResponse(Long id, String name, String description, boolean active, List<String> positions) {
        static DepartmentResponse from(Department d) {
            return new DepartmentResponse(d.getId(), d.getName(), d.getDescription(), d.isActive(), d.getPositions());
        }
    }

    /** Trimmed, blanks dropped, duplicates (ignoring case) removed; order kept. */
    static List<String> cleanPositions(List<String> raw) {
        Map<String, String> byKey = new LinkedHashMap<>();
        if (raw != null) {
            for (String p : raw) {
                if (StringUtils.hasText(p)) byKey.putIfAbsent(p.trim().toLowerCase(Locale.ROOT), p.trim());
            }
        }
        return List.copyOf(byKey.values());
    }

    private final DepartmentRepository repo;
    private final CandidateRepository candidates;
    private final AuditService audit;

    public DepartmentController(DepartmentRepository repo, CandidateRepository candidates, AuditService audit) {
        this.repo = repo;
        this.candidates = candidates;
        this.audit = audit;
    }

    @GetMapping
    public List<DepartmentResponse> list() {
        return repo.findAllByOrderByNameAsc().stream().map(DepartmentResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse create(@Valid @RequestBody DepartmentRequest req) {
        String name = req.name().trim();
        if (repo.findByNameIgnoreCase(name).isPresent()) throw ApiException.conflict("A department with this name already exists");
        Department d = new Department(name, StringUtils.hasText(req.description()) ? req.description().trim() : null);
        d.setActive(req.active());
        d.setPositions(cleanPositions(req.positions()));
        repo.save(d);
        audit.record("DEPARTMENT_CREATED", "DEPARTMENT", d.getId(), "Created department " + d.getName());
        return DepartmentResponse.from(d);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentResponse update(@PathVariable Long id, @Valid @RequestBody DepartmentRequest req) {
        Department d = repo.findById(id).orElseThrow(() -> ApiException.notFound("Department"));
        String name = req.name().trim();
        repo.findByNameIgnoreCase(name).filter(other -> !other.getId().equals(id))
                .ifPresent(o -> { throw ApiException.conflict("A department with this name already exists"); });
        String before = d.getName() + (d.isActive() ? "" : " (inactive)");
        d.setName(name);
        d.setDescription(StringUtils.hasText(req.description()) ? req.description().trim() : null);
        d.setActive(req.active());
        d.setPositions(cleanPositions(req.positions()));
        repo.save(d);
        audit.record("DEPARTMENT_UPDATED", "DEPARTMENT", d.getId(),
                "Updated department " + before + " → " + d.getName() + (d.isActive() ? "" : " (inactive)"));
        return DepartmentResponse.from(d);
    }

    /** Deletes an unused department. Departments with candidates must be deactivated instead. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Department d = repo.findById(id).orElseThrow(() -> ApiException.notFound("Department"));
        if (candidates.countByDepartmentId(id) > 0) {
            throw ApiException.conflict("This department has candidates. Deactivate it instead of deleting.");
        }
        repo.delete(d);
        audit.record("DEPARTMENT_DELETED", "DEPARTMENT", id, "Deleted department " + d.getName());
    }
}

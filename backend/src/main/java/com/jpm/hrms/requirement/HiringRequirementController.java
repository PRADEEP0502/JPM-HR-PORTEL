package com.jpm.hrms.requirement;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.department.Department;
import com.jpm.hrms.department.DepartmentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The saved hiring requirements. The Requirements page suggests candidates for each one. */
@RestController
@RequestMapping("/api/requirements")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class HiringRequirementController {

    public record CreateRequest(@NotNull Long departmentId, @Size(max = 100) String position, @Min(1) @Max(100) int needed) {
    }

    public record UpdateRequest(@Min(1) @Max(100) int needed) {
    }

    /** departmentName is null when the department has since been deleted. */
    public record RequirementResponse(Long id, Long departmentId, String departmentName, String position, int needed) {
    }

    private final HiringRequirementRepository repo;
    private final DepartmentRepository departments;
    private final AuditService audit;

    public HiringRequirementController(HiringRequirementRepository repo, DepartmentRepository departments, AuditService audit) {
        this.repo = repo;
        this.departments = departments;
        this.audit = audit;
    }

    @GetMapping
    public List<RequirementResponse> list() {
        Map<Long, String> names = departments.findAll().stream().collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
        return repo.findAllByOrderByIdAsc().stream().map(r -> toResponse(r, names::get)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementResponse create(@Valid @RequestBody CreateRequest req, Authentication auth) {
        Department d = departments.findById(req.departmentId()).orElseThrow(() -> ApiException.notFound("Department"));
        String position = StringUtils.hasText(req.position()) ? req.position().trim() : null;
        boolean exists = repo.findByDepartmentId(d.getId()).stream().anyMatch(r -> position == null ? r.getPosition() == null : position.equalsIgnoreCase(r.getPosition()));
        if (exists) throw ApiException.conflict("This requirement already exists. Change its number instead.");
        HiringRequirement r = repo.save(new HiringRequirement(d.getId(), position, req.needed(), auth.getName()));
        audit.record("REQUIREMENT_CREATED", "REQUIREMENT", r.getId(), label(d.getName(), position) + ": " + r.getNeeded() + " needed");
        return toResponse(r, id -> d.getName());
    }

    @PutMapping("/{id}")
    public RequirementResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        HiringRequirement r = repo.findById(id).orElseThrow(() -> ApiException.notFound("Requirement"));
        String name = departments.findById(r.getDepartmentId()).map(Department::getName).orElse(null);
        int before = r.getNeeded();
        r.setNeeded(req.needed());
        repo.save(r);
        audit.record("REQUIREMENT_UPDATED", "REQUIREMENT", r.getId(), label(name, r.getPosition()) + ": " + before + " → " + r.getNeeded() + " needed");
        return toResponse(r, x -> name);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        HiringRequirement r = repo.findById(id).orElseThrow(() -> ApiException.notFound("Requirement"));
        String name = departments.findById(r.getDepartmentId()).map(Department::getName).orElse(null);
        repo.delete(r);
        audit.record("REQUIREMENT_DELETED", "REQUIREMENT", id, "Deleted requirement " + label(name, r.getPosition()));
    }

    private static RequirementResponse toResponse(HiringRequirement r, Function<Long, String> departmentName) {
        return new RequirementResponse(r.getId(), r.getDepartmentId(), departmentName.apply(r.getDepartmentId()), r.getPosition(), r.getNeeded());
    }

    private static String label(String department, String position) {
        return (department == null ? "(deleted department)" : department) + (position == null ? "" : " - " + position);
    }
}

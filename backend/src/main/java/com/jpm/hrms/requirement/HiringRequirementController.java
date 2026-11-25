package com.jpm.hrms.requirement;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateRepository;
import com.jpm.hrms.candidate.CandidateSpecifications;
import com.jpm.hrms.candidate.CandidateSpecifications.ArchiveScope;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The saved hiring requirements, in priority order (1 = fill first). The Requirements page suggests candidates for each one.
 * Priorities are always kept as 1, 2, 3 … with no gaps or repeats: giving one requirement a number moves the others down.
 */
@RestController
@RequestMapping("/api/requirements")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class HiringRequirementController {

    /** priority is optional: without it the requirement goes last. */
    public record CreateRequest(@NotNull Long departmentId, @Size(max = 100) String position, @Min(1) @Max(100) int needed,
                                @Min(1) Integer priority) {
    }

    /** Send any of them. pending = true keeps the requirement pending (hiring paused, with an optional reason); false resumes it. */
    public record UpdateRequest(@Min(1) @Max(100) Integer needed, @Min(1) Integer priority, Boolean pending,
                                @Size(max = 300) String pendingReason) {
    }

    /** departmentName is null when the department has since been deleted; hired = active selected / joined candidates in its department and sub-work. */
    public record RequirementResponse(Long id, Long departmentId, String departmentName, String position, int needed, int priority, long hired,
                                      boolean pending, String pendingReason, String pendingBy, java.time.Instant pendingSince) {
    }

    private static final List<ApplicationStatus> HIRED = List.of(ApplicationStatus.SELECTED, ApplicationStatus.JOINED);

    private final HiringRequirementRepository repo;
    private final DepartmentRepository departments;
    private final CandidateRepository candidates;
    private final AuditService audit;

    public HiringRequirementController(HiringRequirementRepository repo, DepartmentRepository departments,
                                       CandidateRepository candidates, AuditService audit) {
        this.repo = repo;
        this.departments = departments;
        this.candidates = candidates;
        this.audit = audit;
    }

    @GetMapping
    public List<RequirementResponse> list() {
        Map<Long, String> names = departments.findAll().stream().collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
        return renumber(ordered(), null, 0).stream().map(r -> toResponse(r, names::get)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementResponse create(@Valid @RequestBody CreateRequest req, Authentication auth) {
        Department d = departments.findById(req.departmentId()).orElseThrow(() -> ApiException.notFound("Department"));
        String position = StringUtils.hasText(req.position()) ? req.position().trim() : null;
        boolean exists = repo.findByDepartmentId(d.getId()).stream().anyMatch(r -> position == null ? r.getPosition() == null : position.equalsIgnoreCase(r.getPosition()));
        if (exists) throw ApiException.conflict("This requirement already exists. Change its number instead.");
        HiringRequirement r = repo.save(new HiringRequirement(d.getId(), position, req.needed(), auth.getName()));
        renumber(ordered(), r, req.priority() == null ? Integer.MAX_VALUE : req.priority());
        audit.record("REQUIREMENT_CREATED", "REQUIREMENT", r.getId(), label(d.getName(), position) + ": " + r.getNeeded() + " needed, priority " + r.getPriority());
        return toResponse(r, id -> d.getName());
    }

    @PutMapping("/{id}")
    public RequirementResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req, Authentication auth) {
        if (req.needed() == null && req.priority() == null && req.pending() == null) throw ApiException.badRequest("Nothing to change");
        HiringRequirement r = repo.findById(id).orElseThrow(() -> ApiException.notFound("Requirement"));
        String name = departments.findById(r.getDepartmentId()).map(Department::getName).orElse(null);
        List<String> changes = new ArrayList<>();
        if (req.needed() != null && req.needed() != r.getNeeded()) {
            changes.add(r.getNeeded() + " → " + req.needed() + " needed");
            r.setNeeded(req.needed());
            repo.save(r);
        }
        if (req.priority() != null) {
            Integer before = r.getPriority();
            renumber(ordered(), r, req.priority());
            if (!r.getPriority().equals(before)) changes.add("priority " + before + " → " + r.getPriority());
        }
        if (req.pending() != null && req.pending() != r.isPending()) {
            String reason = StringUtils.hasText(req.pendingReason()) ? req.pendingReason().trim() : null;
            if (req.pending()) {
                r.keepPending(reason, auth.getName());
                audit.record("REQUIREMENT_PENDING", "REQUIREMENT", r.getId(), label(name, r.getPosition()) + ": kept pending" + (reason == null ? "" : " - " + reason));
            } else {
                r.resume();
                audit.record("REQUIREMENT_RESUMED", "REQUIREMENT", r.getId(), label(name, r.getPosition()) + ": hiring resumed");
            }
            repo.save(r);
        }
        if (!changes.isEmpty()) audit.record("REQUIREMENT_UPDATED", "REQUIREMENT", r.getId(), label(name, r.getPosition()) + ": " + String.join(", ", changes));
        return toResponse(r, x -> name);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        HiringRequirement r = repo.findById(id).orElseThrow(() -> ApiException.notFound("Requirement"));
        String name = departments.findById(r.getDepartmentId()).map(Department::getName).orElse(null);
        repo.delete(r);
        renumber(ordered(), null, 0);
        audit.record("REQUIREMENT_DELETED", "REQUIREMENT", id, "Deleted requirement " + label(name, r.getPosition()));
    }

    /** All requirements by priority; ones without a priority (older records) come last, oldest first. */
    private List<HiringRequirement> ordered() {
        List<HiringRequirement> all = new ArrayList<>(repo.findAllByOrderByIdAsc());
        all.sort(Comparator.comparing((HiringRequirement r) -> r.getPriority() == null ? Integer.MAX_VALUE : r.getPriority())
                .thenComparing(HiringRequirement::getId));
        return all;
    }

    /** Puts {@code moved} (if any) at position {@code target} (1-based, clamped) and saves 1, 2, 3 … for every record whose number changed. */
    private List<HiringRequirement> renumber(List<HiringRequirement> all, HiringRequirement moved, int target) {
        if (moved != null) {
            all.removeIf(x -> x.getId().equals(moved.getId()));
            all.add(Math.max(0, Math.min(target, all.size() + 1) - 1), moved);
        }
        List<HiringRequirement> changed = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            HiringRequirement x = all.get(i);
            if (x.getPriority() == null || x.getPriority() != i + 1) {
                x.setPriority(i + 1);
                changed.add(x);
            }
        }
        if (!changed.isEmpty()) repo.saveAll(changed);
        return all;
    }

    private RequirementResponse toResponse(HiringRequirement r, Function<Long, String> departmentName) {
        long hired = candidates.count(CandidateSpecifications.matching(new CandidateSpecifications.Filter(null, r.getDepartmentId(), r.getPosition(), null, null,
                HIRED, null, null, null, null, null, ArchiveScope.ACTIVE, null)));
        return new RequirementResponse(r.getId(), r.getDepartmentId(), departmentName.apply(r.getDepartmentId()), r.getPosition(), r.getNeeded(),
                r.getPriority() == null ? 0 : r.getPriority(), hired, r.isPending(), r.getPendingReason(), r.getPendingBy(), r.getPendingSince());
    }

    private static String label(String department, String position) {
        return (department == null ? "(deleted department)" : department) + (position == null ? "" : " - " + position);
    }
}

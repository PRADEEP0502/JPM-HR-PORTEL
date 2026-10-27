package com.jpm.hrms.audit;

import com.jpm.hrms.common.MongoText;
import com.jpm.hrms.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditLogRepository repo;

    public AuditController(AuditLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public PageResponse<AuditLog> list(@RequestParam(required = false) String action,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "occurredAt"));
        List<Criteria> cs = new ArrayList<>();
        if (StringUtils.hasText(action)) cs.add(Criteria.where("action").is(action));
        if (StringUtils.hasText(q)) {
            String text = MongoText.contains(q);
            cs.add(new Criteria().orOperator(Criteria.where("details").regex(text, "i"), Criteria.where("actor").regex(text, "i")));
        }
        return PageResponse.of(repo.findAll(cs.isEmpty() ? new Criteria() : new Criteria().andOperator(cs), pr), a -> a);
    }
}

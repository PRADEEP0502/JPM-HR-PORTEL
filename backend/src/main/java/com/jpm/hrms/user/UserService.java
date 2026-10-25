package com.jpm.hrms.user;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.user.UserDtos.CreateUserRequest;
import com.jpm.hrms.user.UserDtos.UpdateUserRequest;
import com.jpm.hrms.user.UserDtos.UserResponse;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(AppUserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll(Sort.by("username")).stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest req) {
        if (users.existsByUsernameIgnoreCase(req.username())) {
            throw ApiException.conflict("Username already exists");
        }
        AppUser u = new AppUser();
        u.setUsername(req.username().trim());
        u.setFullName(req.fullName().trim());
        u.setEmail(blankToNull(req.email()));
        u.setRole(req.role());
        u.setPasswordHash(encoder.encode(req.password()));
        users.save(u);
        audit.record("USER_CREATED", "USER", u.getId(), "Created user " + u.getUsername() + " with role " + u.getRole());
        return UserResponse.from(u);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req, String actingUsername) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        boolean selfEdit = u.getUsername().equalsIgnoreCase(actingUsername);
        if (selfEdit && (!req.enabled() || req.role() != Role.ADMIN) && u.getRole() == Role.ADMIN) {
            throw ApiException.badRequest("You cannot disable or demote your own administrator account");
        }
        boolean losingAdmin = u.getRole() == Role.ADMIN && u.isEnabled() && (req.role() != Role.ADMIN || !req.enabled());
        if (losingAdmin && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw ApiException.badRequest("At least one active administrator is required");
        }
        StringBuilder changes = new StringBuilder();
        if (u.getRole() != req.role()) changes.append("role ").append(u.getRole()).append("→").append(req.role()).append("; ");
        if (u.isEnabled() != req.enabled()) changes.append(req.enabled() ? "enabled; " : "disabled; ");
        u.setFullName(req.fullName().trim());
        u.setEmail(blankToNull(req.email()));
        u.setRole(req.role());
        u.setEnabled(req.enabled());
        if (StringUtils.hasText(req.newPassword())) {
            u.setPasswordHash(encoder.encode(req.newPassword()));
            changes.append("password reset; ");
        }
        audit.record("USER_UPDATED", "USER", u.getId(), "Updated user " + u.getUsername()
                + (changes.isEmpty() ? "" : " (" + changes.toString().trim() + ")"));
        return UserResponse.from(u);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}

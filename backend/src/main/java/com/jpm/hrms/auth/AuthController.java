package com.jpm.hrms.auth;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.config.AppProperties;
import com.jpm.hrms.user.AppUser;
import com.jpm.hrms.user.AppUserRepository;
import com.jpm.hrms.user.UserDtos;
import com.jpm.hrms.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 100) String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserResponse user) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Pattern(regexp = UserDtos.PASSWORD_REGEX, message = UserDtos.PASSWORD_MESSAGE) String newPassword) {
    }

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final LoginAttemptService attempts;
    private final AuditService audit;
    private final AppProperties props;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtEncoder jwtEncoder,
                          LoginAttemptService attempts, AuditService audit, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.attempts = attempts;
        this.audit = audit;
        this.props = props;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        String username = req.username().trim();
        if (attempts.isLocked(username)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in 15 minutes.");
        }
        AppUser user = users.findByUsernameIgnoreCase(username).orElse(null);
        if (user == null || !user.isEnabled() || !encoder.matches(req.password(), user.getPasswordHash())) {
            attempts.recordFailure(username);
            audit.recordAs(username, "LOGIN_FAILED", "USER", user == null ? null : user.getId(), "Failed login attempt");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        attempts.recordSuccess(username);
        user.setLastLoginAt(Instant.now());
        users.save(user);
        audit.recordAs(user.getUsername(), "LOGIN", "USER", user.getId(), "Signed in");

        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.security().tokenTtlMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("jpm-hrms")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, expiresAt, UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication auth) {
        return users.findByUsernameIgnoreCase(auth.getName()).map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User"));
    }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req, Authentication auth) {
        AppUser user = users.findByUsernameIgnoreCase(auth.getName()).orElseThrow(() -> ApiException.notFound("User"));
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(user);
        audit.record("PASSWORD_CHANGED", "USER", user.getId(), "Changed own password");
    }
}

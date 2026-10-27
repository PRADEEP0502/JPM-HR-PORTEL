package com.jpm.hrms.auth;

import com.jpm.hrms.config.AppProperties;
import com.jpm.hrms.user.AppUser;
import com.jpm.hrms.user.AppUserRepository;
import com.jpm.hrms.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Creates the first administrator from environment variables when the user table is empty. */
@Component
@Order(1)
public class BootstrapAdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public BootstrapAdminInitializer(AppUserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        String username = props.security().bootstrapAdminUsername();
        String password = props.security().bootstrapAdminPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("No users exist. Set BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD to create the first administrator.");
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(username.trim());
        admin.setFullName("Administrator");
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(encoder.encode(password));
        users.save(admin);
        log.info("Created bootstrap administrator '{}'. Change its password after first sign-in.", admin.getUsername());
    }
}

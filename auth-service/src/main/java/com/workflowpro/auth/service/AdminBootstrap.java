package com.workflowpro.auth.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.workflowpro.auth.config.BootstrapAdminProperties;
import com.workflowpro.auth.entity.RoleName;
import com.workflowpro.auth.entity.User;
import com.workflowpro.auth.repository.RoleRepository;
import com.workflowpro.auth.repository.UserRepository;

/**
 * Solves "who creates the first admin?" without editing the database by hand:
 * on startup, if BOOTSTRAP_ADMIN_EMAIL/PASSWORD are set and no user has that email, an ADMIN is created.
 * An existing user is never changed.
 */
@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(BootstrapAdminProperties properties, UserRepository userRepository,
                          RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            log.info("No bootstrap admin configured (BOOTSTRAP_ADMIN_EMAIL is empty) - skipping");
            return;
        }
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            log.info("Bootstrap admin {} already exists - nothing to do", email);
            return;
        }
        User admin = new User(email, passwordEncoder.encode(properties.password()),
                valueOr(properties.firstName(), "System"), valueOr(properties.lastName(), "Admin"));
        admin.addRole(roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Role ADMIN missing - check Flyway migration V2")));
        userRepository.save(admin);
        log.info("Created bootstrap admin {}", email);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

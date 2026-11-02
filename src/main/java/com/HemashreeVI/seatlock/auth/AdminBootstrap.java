package com.HemashreeVI.seatlock.auth;

import com.HemashreeVI.seatlock.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates (or updates) the organizer account from ADMIN_EMAIL / ADMIN_PASSWORD at startup. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AuthProperties properties;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.adminEmail();
        String password = properties.adminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("No organizer account configured (set ADMIN_EMAIL and ADMIN_PASSWORD to create one)");
            return;
        }
        if (password.length() < 8) {
            log.warn("ADMIN_PASSWORD is shorter than 8 characters; organizer account not created");
            return;
        }
        AppUser admin = users.findByEmail(AuthService.normalize(email)).orElseGet(AppUser::new);
        boolean created = admin.getId() == null;
        admin.setEmail(AuthService.normalize(email));
        admin.setName(admin.getName() == null ? properties.adminName() : admin.getName());
        if (created || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            admin.setPasswordHash(passwordEncoder.encode(password));
        }
        admin.setRole(AppUser.ROLE_ADMIN);
        users.save(admin);
        log.info("Organizer account {} {}", admin.getEmail(), created ? "created" : "updated");
    }
}

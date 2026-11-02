package com.HemashreeVI.seatlock.auth;

import com.HemashreeVI.seatlock.common.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final SecureRandom random = new SecureRandom();

    /** BCrypt hash of a random value: compared against when the email is unknown (see login). */
    private volatile String dummyHash;

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setName(request.name().strip());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("An account with this email already exists");   // concurrent sign-up
        }
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        AppUser user = users.findByEmail(normalize(request.email())).orElse(null);
        // Always run one BCrypt comparison, so "unknown email" and "wrong password" take the same
        // time and give the same answer: the response doesn't reveal which emails have accounts.
        String hash = user != null ? user.getPasswordHash() : dummyHash();
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return tokenFor(user);
    }

    /** A throwaway identity (no account, nothing stored), e.g. for the 50 racers on the demo page. */
    public AuthDtos.AuthResponse guest() {
        byte[] id = new byte[8];
        random.nextBytes(id);
        String subject = "guest-" + HexFormat.of().formatHex(id);
        TokenService.IssuedToken token = tokens.issue(subject, "Guest", null, List.of("GUEST"));
        return new AuthDtos.AuthResponse(token.token(), token.expiresAt(),
                new AuthDtos.UserView(subject, "Guest", null, List.of("GUEST"), true));
    }

    AuthDtos.AuthResponse tokenFor(AppUser user) {
        List<String> roles = user.isAdmin() ? List.of(AppUser.ROLE_USER, AppUser.ROLE_ADMIN) : List.of(AppUser.ROLE_USER);
        TokenService.IssuedToken token = tokens.issue(user.subject(), user.getName(), user.getEmail(), roles);
        return new AuthDtos.AuthResponse(token.token(), token.expiresAt(),
                new AuthDtos.UserView(user.subject(), user.getName(), user.getEmail(), roles, false));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode(HexFormat.of().formatHex(random.generateSeed(16)));
        }
        return dummyHash;
    }

    static String normalize(String email) {
        return email.strip().toLowerCase();
    }

    /** Wrong email or password (HTTP 401). Deliberately doesn't say which. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Wrong email or password");
        }
    }
}

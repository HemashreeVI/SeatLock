package com.HemashreeVI.seatlock.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/guest")
    public AuthDtos.AuthResponse guest() {
        return authService.guest();
    }

    /** Who am I? Read straight from the (already verified) token. */
    @GetMapping("/me")
    public AuthDtos.UserView me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new AuthDtos.UserView(jwt.getSubject(), jwt.getClaimAsString("name"), jwt.getClaimAsString("email"),
                roles == null ? List.of() : roles, roles != null && roles.contains("GUEST"));
    }
}

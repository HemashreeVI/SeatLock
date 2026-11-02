package com.HemashreeVI.seatlock.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * TESTS AND BENCHMARKS ONLY (seatlock.auth.trust-user-header=true): treats an "X-User-Id" header
 * as the caller's identity when no token was sent, so load tests can simulate thousands of users
 * without signing each one up. Never enabled by default.
 */
class TrustedUserHeaderFilter extends OncePerRequestFilter {

    static final String HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String user = request.getHeader(HEADER);
        if (user != null && !user.isBlank() && !isRealIdentity(user.strip())
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    user.strip(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        }
        chain.doFilter(request, response);
    }

    /**
     * "user-…" and "guest-…" belong to real accounts and guest tokens. The header may never claim
     * them, so even with this test switch on it can't act as (or collide with) a real login.
     */
    static boolean isRealIdentity(String user) {
        return user.startsWith("user-") || user.startsWith("guest-");
    }
}

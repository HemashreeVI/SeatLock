package com.HemashreeVI.seatlock.ratelimit;

import com.HemashreeVI.seatlock.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Rate-limits the expensive write endpoints (holding seats, paying, joining the queue).
 * Two buckets must both have a token: one per logged-in user and one per client IP (a client can
 * mint guest identities, so the IP bucket caps that). Log-in and sign-up get a much smaller per-IP
 * bucket against password guessing.
 * Reads are not limited here (they are cheap and cached).
 * If Redis is unavailable we fail OPEN: better to serve users than to block everyone.
 * <p>
 * Behind a reverse proxy, set server.forward-headers-strategy so getRemoteAddr() is the client.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern LIMITED_PATHS =
            Pattern.compile("^/api/bookings(/\\d+/payments)?/?$|^/api/events/\\d+/queue/?$|^/api/auth/guest/?$");

    /** Password guessing protection: a much smaller per-IP bucket for log-in and sign-up. */
    private static final Pattern CREDENTIAL_PATHS = Pattern.compile("^/api/auth/(login|register)/?$");

    /** Same normalisation Spring MVC applies before routing: decode, strip ";params", collapse "//". */
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    static {
        PATHS.setRemoveSemicolonContent(true);
        PATHS.setUrlDecode(true);
    }

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final JsonMapper json;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties, JsonMapper json) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled() || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = PATHS.getPathWithinApplication(request);
        return !LIMITED_PATHS.matcher(path).matches() && !CREDENTIAL_PATHS.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimiter.Decision decision;
        try {
            String ip = request.getRemoteAddr();
            if (CREDENTIAL_PATHS.matcher(PATHS.getPathWithinApplication(request)).matches()) {
                decision = rateLimiter.tryAcquire("login-ip:" + ip, properties.loginCapacity(), properties.loginRefillPerSecond());
            } else {
                decision = rateLimiter.tryAcquire("ip:" + ip, properties.ipCapacity(), properties.ipRefillPerSecond());
                // the caller's identity comes from the verified login token (Spring Security runs before this filter)
                Authentication caller = SecurityContextHolder.getContext().getAuthentication();
                if (decision.allowed() && caller != null && caller.isAuthenticated()
                        && !(caller instanceof AnonymousAuthenticationToken)) {
                    decision = rateLimiter.tryAcquire("user:" + caller.getName(), properties.capacity(), properties.refillPerSecond());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, (decision.retryAfterMs() + 999) / 1000);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests, slow down and retry in " + retryAfterSeconds + "s");
        problem.setTitle("Too many requests");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(json.writeValueAsString(problem));
    }
}

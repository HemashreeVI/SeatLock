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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Rate-limits the expensive write endpoints (holding seats, paying, joining the queue) per user.
 * Reads are not limited here (they are cheap and cached).
 * If Redis is unavailable we fail OPEN: better to serve users than to block everyone.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern LIMITED_PATHS =
            Pattern.compile("^/api/bookings(/\\d+/payments)?$|^/api/events/\\d+/queue$");

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
        return !properties.enabled()
                || !"POST".equals(request.getMethod())
                || !LIMITED_PATHS.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String user = request.getHeader("X-User-Id");
        String clientKey = (user != null && !user.isBlank()) ? "user:" + user : "ip:" + request.getRemoteAddr();

        RateLimiter.Decision decision;
        try {
            decision = rateLimiter.tryAcquire(clientKey, properties.capacity(), properties.refillPerSecond());
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

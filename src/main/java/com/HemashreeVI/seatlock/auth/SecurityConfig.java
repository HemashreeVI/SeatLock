package com.HemashreeVI.seatlock.auth;

import com.HemashreeVI.seatlock.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Who may call what.
 * <ul>
 *   <li>Public: the pages, browsing events and seats (incl. the live stream), sign-up/log-in.</li>
 *   <li>Logged in (account or guest token): holds, payments, bookings, tickets, notifications, queue.</li>
 *   <li>Organizer (ADMIN): creating events, checking tickets in.</li>
 * </ul>
 * Stateless: every request carries its token ("Authorization: Bearer ..."); no server sessions,
 * no cookies, hence no CSRF exposure.
 */
@Slf4j
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProperties properties, JsonMapper json) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, e) ->
                problem(response, json, HttpStatus.UNAUTHORIZED, "Login required",
                        "Log in (or get a guest token) and send it as 'Authorization: Bearer <token>'");
        AccessDeniedHandler forbidden = (request, response, e) ->
                problem(response, json, HttpStatus.FORBIDDEN, "Not allowed", "This needs the organizer (admin) role");

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/guest").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/*", "/api/events/*/seats",
                                "/api/events/*/seats/stream", "/api/events/*/stats").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/events").hasRole(AppUser.ROLE_ADMIN)
                        .requestMatchers("/api/tickets/check-in", "/api/tickets/check-in/**").hasRole(AppUser.ROLE_ADMIN)
                        .requestMatchers("/api/**").authenticated()
                        // which actuator endpoints exist at all is decided by management.endpoints.web.exposure
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesFromToken()))
                        .authenticationEntryPoint(unauthorized))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden));

        if (properties.trustUserHeader()) {
            log.warn("seatlock.auth.trust-user-header=true: the X-User-Id header is accepted as identity. "
                    + "Tests and benchmarks only, never on a real deployment.");
            http.addFilterAfter(new TrustedUserHeaderFilter(), BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }

    /** "roles": ["USER", "ADMIN"] in the token become ROLE_USER, ROLE_ADMIN. */
    private static JwtAuthenticationConverter rolesFromToken() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static void problem(HttpServletResponse response, JsonMapper json, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(json.writeValueAsString(pd));
    }
}

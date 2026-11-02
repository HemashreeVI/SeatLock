package com.HemashreeVI.seatlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings under "seatlock.auth". */
@ConfigurationProperties("seatlock.auth")
public record AuthProperties(
        /** Secret that signs login tokens. Empty = random per start (everyone is logged out on restart). */
        String jwtSecret,
        /** How long a login token is valid. */
        @DefaultValue("7d") Duration tokenTtl,
        /** Organizer account created/updated at startup (can create events and check people in). */
        String adminEmail,
        String adminPassword,
        @DefaultValue("Organizer") String adminName,
        /**
         * TESTS AND BENCHMARKS ONLY: accept an unauthenticated "X-User-Id" header as the caller's
         * identity. Never enable on a real deployment: anyone could act as anyone.
         */
        @DefaultValue("false") boolean trustUserHeader
) {
}

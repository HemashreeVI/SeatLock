package com.HemashreeVI.seatlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under "seatlock.rate-limit". */
@ConfigurationProperties("seatlock.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        /** Per-user bucket size = how many requests one user may burst. */
        @DefaultValue("20") int capacity,
        /** Per-user sustained rate = tokens added back per second. */
        @DefaultValue("5") double refillPerSecond,
        /**
         * Per-IP bucket. X-User-Id is not authenticated, so a client could rotate it to get fresh
         * user buckets; the IP bucket caps that. Larger than the user bucket because many real
         * users can share one IP (office, mobile carrier NAT).
         */
        @DefaultValue("200") int ipCapacity,
        @DefaultValue("50") double ipRefillPerSecond
) {
}

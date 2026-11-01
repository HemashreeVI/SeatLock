package com.HemashreeVI.seatlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under "seatlock.rate-limit". */
@ConfigurationProperties("seatlock.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        /** Bucket size = how many requests a client may burst. */
        @DefaultValue("20") int capacity,
        /** Sustained rate = tokens added back per second. */
        @DefaultValue("5") double refillPerSecond
) {
}

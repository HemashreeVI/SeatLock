package com.HemashreeVI.seatlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings under "seatlock.queue" (virtual waiting room). */
@ConfigurationProperties("seatlock.queue")
public record QueueProperties(
        /** How many admitted users may be shopping at the same time per event. */
        @DefaultValue("100") int maxActiveUsers,
        /** At most this many users are let in per admission tick. */
        @DefaultValue("50") int admitBatchSize,
        /** How long an admission token stays valid. */
        @DefaultValue("10m") Duration admissionTtl,
        /** Run the admission job automatically (tests switch this off and drive it by hand). */
        @DefaultValue("true") boolean autoAdmit
) {
}

package com.HemashreeVI.seatlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings under "seatlock.payment". */
@ConfigurationProperties("seatlock.payment")
public record PaymentProperties(
        /** Simulated provider latency of the fake gateway. */
        @DefaultValue("0") long fakeLatencyMs,
        /** A payment still PENDING after this long is re-driven by the reconciliation job. */
        @DefaultValue("1m") Duration pendingTimeout,
        /** If the provider is still unreachable after this long, the payment fails and the seats are released. */
        @DefaultValue("15m") Duration giveUpAfter
) {
}

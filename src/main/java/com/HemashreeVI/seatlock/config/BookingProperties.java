package com.HemashreeVI.seatlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings under "seatlock.booking" in application.yml. */
@ConfigurationProperties("seatlock.booking")
public record BookingProperties(
        @DefaultValue("10m") Duration holdTtl,
        @DefaultValue("10") int maxSeatsPerBooking,
        @DefaultValue("200") int expirySweepBatchSize
) {
}

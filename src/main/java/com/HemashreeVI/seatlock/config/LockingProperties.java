package com.HemashreeVI.seatlock.config;

import com.HemashreeVI.seatlock.booking.locking.LockingStrategyType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings under "seatlock.locking" in application.yml. */
@ConfigurationProperties("seatlock.locking")
public record LockingProperties(
        @DefaultValue("PESSIMISTIC") LockingStrategyType strategy,
        @DefaultValue("5s") Duration redisLockTtl
) {
}

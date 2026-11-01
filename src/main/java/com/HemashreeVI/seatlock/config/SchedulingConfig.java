package com.HemashreeVI.seatlock.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs (hold expiry, ...). Can be switched off with seatlock.scheduling.enabled=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "seatlock.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}

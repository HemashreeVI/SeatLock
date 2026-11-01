package com.HemashreeVI.seatlock.observability;

import com.HemashreeVI.seatlock.booking.locking.LockingStrategyType;
import com.HemashreeVI.seatlock.messaging.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Business metrics, exported at /actuator/prometheus:
 * <ul>
 *   <li>seatlock_holds_total{result, strategy}: success / conflict / replayed / error</li>
 *   <li>seatlock_hold_duration_seconds{strategy}: latency histogram (p50/p95/p99)</li>
 *   <li>seatlock_booking_transitions_total{status}: CONFIRMED / CANCELLED / EXPIRED</li>
 *   <li>seatlock_outbox_backlog: messages written but not yet published to Kafka</li>
 * </ul>
 */
@Component
public class BookingMetrics {

    private final MeterRegistry registry;

    public BookingMetrics(MeterRegistry registry, OutboxRepository outboxRepository) {
        this.registry = registry;
        Gauge.builder("seatlock.outbox.backlog", outboxRepository, r -> {
                    try {
                        return r.countByPublishedAtIsNull();
                    } catch (RuntimeException e) {
                        return Double.NaN;
                    }
                })
                .description("Outbox messages not yet published to Kafka")
                .register(registry);
    }

    public <T> T timeHold(LockingStrategyType strategy, Supplier<T> hold) {
        return Timer.builder("seatlock.hold.duration")
                .description("Time to hold seats")
                .tag("strategy", strategy.name())
                .publishPercentileHistogram()
                .register(registry)
                .record(hold);
    }

    public void holdResult(LockingStrategyType strategy, String result) {
        Counter.builder("seatlock.holds")
                .description("Seat hold attempts by outcome")
                .tag("strategy", strategy.name())
                .tag("result", result)
                .register(registry)
                .increment();
    }

    public void transition(String status) {
        Counter.builder("seatlock.booking.transitions")
                .description("Booking state changes")
                .tag("status", status)
                .register(registry)
                .increment();
    }
}

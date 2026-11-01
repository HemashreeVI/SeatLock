package com.HemashreeVI.seatlock.observability;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityTest extends IntegrationTest {

    @Autowired
    MeterRegistry registry;

    @Test
    void statsEndpointReportsSeatsBookingsAndIntegrity() {
        long eventId = createEvent("A", 1, 3, 100);
        List<Long> seats = seatIds(eventId);
        post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats.subList(0, 2)), "grace", Map.of());

        JsonNode stats = get("/api/events/" + eventId + "/stats", null).getBody();
        assertThat(stats.get("totalSeats").asLong()).isEqualTo(3);
        assertThat(stats.get("seatsByStatus").get("HELD").asLong()).isEqualTo(2);
        assertThat(stats.get("seatsByStatus").get("AVAILABLE").asLong()).isEqualTo(1);
        assertThat(stats.get("bookingsByStatus").get("HELD").asLong()).isEqualTo(1);
        assertThat(stats.get("doubleBookedSeats").asLong()).isZero();
    }

    @Test
    void businessMetricsAreRecorded() {
        long eventId = createEvent("A", 1, 1, 100);
        List<Long> seats = seatIds(eventId);
        post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats), "heidi", Map.of());
        post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats), "ivan", Map.of());

        assertThat(registry.find("seatlock.holds").tag("result", "success").counter().count()).isPositive();
        assertThat(registry.find("seatlock.holds").tag("result", "conflict").counter().count()).isPositive();
        assertThat(registry.find("seatlock.hold.duration").timer().count()).isPositive();
        assertThat(registry.find("seatlock.outbox.backlog").gauge()).isNotNull();
    }
}

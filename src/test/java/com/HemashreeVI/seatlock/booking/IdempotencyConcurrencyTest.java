package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyConcurrencyTest extends IntegrationTest {

    @Autowired
    BookingService bookingService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentRetriesWithSameKeyCreateOneBooking() throws Exception {
        long eventId = createEvent("VIP", 1, 3, 100);
        List<Long> seats = seatIds(eventId).subList(0, 2);
        Set<Long> bookingIds = ConcurrentHashMap.newKeySet();

        BookingConcurrencyTest.Outcome outcome = BookingConcurrencyTest.race(30, i ->
                bookingIds.add(bookingService.hold("carol", new HoldRequest(eventId, seats), "double-click").booking().id()));

        assertThat(outcome.unexpected()).isEmpty();
        assertThat(outcome.conflicts().get()).isZero();
        assertThat(bookingIds).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from bookings where user_id = 'carol'", Integer.class))
                .isEqualTo(1);
    }
}

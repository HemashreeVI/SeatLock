package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The audit must see overlaps even after the overlapping bookings are no longer live. */
class DoubleBookingAuditTest extends IntegrationTest {

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    BookingExpiryService expiryService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sequentialOwnersOfASeatAreNotAnOverlap() {
        long eventId = createEvent("A", 1, 1, 100);
        List<Long> seat = seatIds(eventId);
        long first = hold("uma", eventId, seat);
        jdbc.update("update bookings set expires_at = now() - interval '1 second' where id = ?", first);
        expiryService.expireBatch();
        long second = hold("victor", eventId, seat);
        delete("/api/bookings/" + second, "victor");
        hold("wendy", eventId, seat);

        assertThat(bookingRepository.countDoubleBookedSeats(eventId)).isZero();
        assertThat(get("/api/events/" + eventId + "/stats", null).getBody().get("doubleBookedSeats").asLong()).isZero();
    }

    @Test
    void anOverlapIsDetectedEvenAfterBothBookingsEnded() {
        long eventId = createEvent("A", 1, 1, 100);
        List<Long> seat = seatIds(eventId);
        long first = hold("xena", eventId, seat);
        delete("/api/bookings/" + first, "xena");
        long second = hold("yuri", eventId, seat);
        delete("/api/bookings/" + second, "yuri");
        assertThat(bookingRepository.countDoubleBookedSeats(eventId)).isZero();

        // simulate a bug: pretend the first booking was released only AFTER the second one started
        jdbc.update("update bookings set updated_at = (select created_at from bookings where id = ?) + interval '1 second' "
                + "where id = ?", second, first);

        assertThat(bookingRepository.countDoubleBookedSeats(eventId)).isEqualTo(1);
    }

    private long hold(String user, long eventId, List<Long> seats) {
        return post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats), user, Map.of())
                .getBody().get("id").asLong();
    }
}

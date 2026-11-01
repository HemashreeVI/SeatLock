package com.HemashreeVI.seatlock.event;

import com.HemashreeVI.seatlock.booking.BookingRepository;
import com.HemashreeVI.seatlock.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;

/** Live sale statistics, used by the load test to verify there were zero double-bookings. */
@RestController
@RequiredArgsConstructor
public class EventStatsController {

    private final EventService eventService;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final JdbcTemplate jdbc;

    @GetMapping("/api/events/{eventId}/stats")
    public EventStatsResponse stats(@PathVariable Long eventId) {
        eventService.assertExists(eventId);
        Map<String, Long> seats = new TreeMap<>();
        long total = 0;
        for (Object[] row : seatRepository.countByStatus(eventId)) {
            long count = (Long) row[1];
            seats.put(row[0].toString(), count);
            total += count;
        }
        Map<String, Long> bookings = new TreeMap<>();
        jdbc.query("select status, count(*) from bookings where event_id = ? group by status",
                rs -> {
                    bookings.put(rs.getString(1), rs.getLong(2));
                }, eventId);
        return new EventStatsResponse(eventId, total, seats, bookings, bookingRepository.countDoubleBookedSeats(eventId));
    }
}

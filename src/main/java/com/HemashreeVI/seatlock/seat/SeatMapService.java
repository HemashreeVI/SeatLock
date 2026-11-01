package com.HemashreeVI.seatlock.seat;

import com.HemashreeVI.seatlock.event.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Read-through access to an event's seat map: Redis first, Postgres on a miss. */
@Service
@RequiredArgsConstructor
public class SeatMapService {

    private final SeatRepository seatRepository;
    private final EventService eventService;
    private final SeatAvailabilityCache cache;

    public record SeatMap(List<SeatResponse> seats, boolean fromCache) {
    }

    public SeatMap seatMap(Long eventId) {
        Optional<List<SeatResponse>> cached = cache.get(eventId);
        if (cached.isPresent()) {
            return new SeatMap(cached.get(), true);
        }
        eventService.assertExists(eventId);
        List<SeatResponse> seats = seatRepository.findByEventIdOrderBySectionAscRowLabelAscSeatNumberAsc(eventId)
                .stream().map(SeatResponse::from).toList();
        cache.put(eventId, seats);
        return new SeatMap(seats, false);
    }
}

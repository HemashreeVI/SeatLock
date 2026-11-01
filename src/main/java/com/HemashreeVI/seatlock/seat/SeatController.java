package com.HemashreeVI.seatlock.seat;

import com.HemashreeVI.seatlock.event.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatRepository seatRepository;
    private final EventService eventService;
    private final SeatAvailabilityCache cache;

    /** Seat map of an event, served from the Redis cache when possible (X-Cache: HIT/MISS). */
    @GetMapping("/api/events/{eventId}/seats")
    public ResponseEntity<List<SeatResponse>> list(@PathVariable Long eventId,
                                                   @RequestParam(required = false) SeatStatus status) {
        Optional<List<SeatResponse>> cached = cache.get(eventId);
        List<SeatResponse> seats;
        if (cached.isPresent()) {
            seats = cached.get();
        } else {
            eventService.assertExists(eventId); // 404 instead of an empty list for unknown events
            seats = seatRepository.findByEventIdOrderBySectionAscRowLabelAscSeatNumberAsc(eventId).stream()
                    .map(SeatResponse::from).toList();
            cache.put(eventId, seats);
        }
        List<SeatResponse> result = status == null ? seats : seats.stream().filter(s -> s.status() == status).toList();
        return ResponseEntity.ok().header("X-Cache", cached.isPresent() ? "HIT" : "MISS").body(result);
    }
}

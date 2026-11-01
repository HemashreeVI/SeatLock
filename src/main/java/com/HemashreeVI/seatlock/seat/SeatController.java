package com.HemashreeVI.seatlock.seat;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatMapService seatMapService;

    /** Seat map of an event, served from the Redis cache when possible (X-Cache: HIT/MISS). */
    @GetMapping("/api/events/{eventId}/seats")
    public ResponseEntity<List<SeatResponse>> list(@PathVariable Long eventId,
                                                   @RequestParam(required = false) SeatStatus status) {
        SeatMapService.SeatMap map = seatMapService.seatMap(eventId);
        List<SeatResponse> result = status == null
                ? map.seats()
                : map.seats().stream().filter(s -> s.status() == status).toList();
        return ResponseEntity.ok().header("X-Cache", map.fromCache() ? "HIT" : "MISS").body(result);
    }
}

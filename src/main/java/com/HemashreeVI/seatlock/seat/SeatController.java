package com.HemashreeVI.seatlock.seat;

import lombok.RequiredArgsConstructor;
import com.HemashreeVI.seatlock.event.EventService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatMapService seatMapService;
    private final SeatLiveUpdates liveUpdates;
    private final EventService eventService;

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

    /**
     * Live seat statuses as Server-Sent Events: a "seats" event with every seat's id and status,
     * sent on connect and again whenever seats of this event change.
     */
    @GetMapping(value = "/api/events/{eventId}/seats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable Long eventId) {
        eventService.assertExists(eventId);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")   // tell proxies not to buffer the stream
                .body(liveUpdates.subscribe(eventId));
    }
}

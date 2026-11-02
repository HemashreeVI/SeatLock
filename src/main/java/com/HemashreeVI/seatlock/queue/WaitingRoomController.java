package com.HemashreeVI.seatlock.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/queue")
@RequiredArgsConstructor
public class WaitingRoomController {

    private final WaitingRoomService waitingRoom;

    /** Join the line (idempotent: re-joining keeps your place). */
    @PostMapping
    public QueueStatus join(@PathVariable Long eventId, Authentication caller) {
        return waitingRoom.join(eventId, caller.getName());
    }

    /** Poll your position; once ADMITTED, the response contains your admission token. */
    @GetMapping
    public QueueStatus status(@PathVariable Long eventId, Authentication caller) {
        return waitingRoom.status(eventId, caller.getName());
    }
}

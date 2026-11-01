package com.HemashreeVI.seatlock.queue;

import com.HemashreeVI.seatlock.booking.BookingController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/queue")
@RequiredArgsConstructor
public class WaitingRoomController {

    private final WaitingRoomService waitingRoom;

    /** Join the line (idempotent: re-joining keeps your place). */
    @PostMapping
    public QueueStatus join(@PathVariable Long eventId, @RequestHeader(BookingController.USER_HEADER) String userId) {
        return waitingRoom.join(eventId, userId);
    }

    /** Poll your position; once ADMITTED, the response contains your admission token. */
    @GetMapping
    public QueueStatus status(@PathVariable Long eventId, @RequestHeader(BookingController.USER_HEADER) String userId) {
        return waitingRoom.status(eventId, userId);
    }
}

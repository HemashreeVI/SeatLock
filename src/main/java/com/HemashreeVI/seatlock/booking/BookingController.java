package com.HemashreeVI.seatlock.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import com.HemashreeVI.seatlock.queue.WaitingRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Users identify themselves with the X-User-Id header (stand-in for real authentication).
 */
@Validated
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    public static final String USER_HEADER = "X-User-Id";
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";

    private final BookingService bookingService;
    private final WaitingRoomService waitingRoom;

    /** Hold seats for hold-ttl (10 minutes by default). */
    @PostMapping
    public ResponseEntity<BookingResponse> hold(
            @RequestHeader(USER_HEADER) @Size(min = 1, max = 100) String userId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) @Size(max = 100) String idempotencyKey,
            @RequestHeader(value = QUEUE_TOKEN_HEADER, required = false) String queueToken,
            @Valid @RequestBody HoldRequest request) {
        waitingRoom.assertAdmitted(request.eventId(), userId, queueToken);
        HoldResult result = bookingService.hold(userId, request, idempotencyKey);
        BookingResponse booking = result.booking();
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(booking);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/bookings/" + booking.id()))
                .body(booking);
    }

    @GetMapping("/{id}")
    public BookingResponse get(@RequestHeader(USER_HEADER) String userId, @PathVariable Long id) {
        return bookingService.get(userId, id);
    }

    /** Cancel a hold and release its seats. */
    @DeleteMapping("/{id}")
    public BookingResponse cancel(@RequestHeader(USER_HEADER) String userId, @PathVariable Long id) {
        return bookingService.cancel(userId, id);
    }
}

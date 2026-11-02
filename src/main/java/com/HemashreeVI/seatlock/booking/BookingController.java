package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.queue.WaitingRoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * The caller is whoever the login token says (Authentication#getName = token subject).
 */
@Validated
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";

    private final BookingService bookingService;
    private final WaitingRoomService waitingRoom;

    /** Hold seats for hold-ttl (10 minutes by default). */
    @PostMapping
    public ResponseEntity<BookingResponse> hold(
            Authentication caller,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) @Size(max = 100) String idempotencyKey,
            @RequestHeader(value = QUEUE_TOKEN_HEADER, required = false) String queueToken,
            @Valid @RequestBody HoldRequest request) {
        String userId = caller.getName();
        waitingRoom.assertAdmitted(request.eventId(), userId, queueToken);
        HoldResult result = bookingService.hold(userId, withAccountContact(request, caller), idempotencyKey);
        BookingResponse booking = result.booking();
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(booking);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/bookings/" + booking.id()))
                .body(booking);
    }

    /** The caller's most recent bookings (any device: they belong to the account, not the browser). */
    @GetMapping
    public List<BookingResponse> mine(Authentication caller) {
        return bookingService.recent(caller.getName());
    }

    @GetMapping("/{id}")
    public BookingResponse get(Authentication caller, @PathVariable Long id) {
        return bookingService.get(caller.getName(), id);
    }

    /** Cancel a hold and release its seats. */
    @DeleteMapping("/{id}")
    public BookingResponse cancel(Authentication caller, @PathVariable Long id) {
        return bookingService.cancel(caller.getName(), id);
    }

    /** Logged-in account holders don't have to type their name/email again for notifications. */
    private static HoldRequest withAccountContact(HoldRequest request, Authentication caller) {
        if (!(caller instanceof JwtAuthenticationToken jwt)) {
            return request;
        }
        String name = request.name() != null && !request.name().isBlank() ? request.name() : jwt.getToken().getClaimAsString("name");
        String email = request.email() != null && !request.email().isBlank() ? request.email() : jwt.getToken().getClaimAsString("email");
        if (jwt.getToken().getSubject().startsWith("guest-")) {
            name = request.name();   // guests have no real name or address
            email = request.email();
        }
        return new HoldRequest(request.eventId(), request.seatIds(), name, email);
    }
}

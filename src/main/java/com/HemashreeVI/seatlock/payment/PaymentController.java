package com.HemashreeVI.seatlock.payment;

import com.HemashreeVI.seatlock.booking.BookingController;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Starts payment for a held booking. Returns 202 Accepted with a PENDING payment;
     * poll GET /api/bookings/{id} (or GET /api/payments/{id}) for the outcome.
     */
    @PostMapping("/api/bookings/{bookingId}/payments")
    public ResponseEntity<PaymentResponse> pay(
            @RequestHeader(BookingController.USER_HEADER) String userId,
            @RequestHeader(value = BookingController.IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @PathVariable Long bookingId,
            @Valid @RequestBody PayRequest request) {
        PaymentResult result = paymentService.requestPayment(userId, bookingId, idempotencyKey, request);
        ResponseEntity.BodyBuilder response = ResponseEntity.accepted()
                .location(URI.create("/api/payments/" + result.payment().id()));
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(result.payment());
    }

    @GetMapping("/api/payments/{paymentId}")
    public PaymentResponse get(@RequestHeader(BookingController.USER_HEADER) String userId,
                               @PathVariable Long paymentId) {
        return paymentService.get(userId, paymentId);
    }
}

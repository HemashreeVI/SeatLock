package com.HemashreeVI.seatlock.payment;

import com.HemashreeVI.seatlock.booking.BookingController;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/bookings/{bookingId}/payments")
    public ResponseEntity<PaymentResponse> pay(
            @RequestHeader(BookingController.USER_HEADER) String userId,
            @RequestHeader(value = BookingController.IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @PathVariable Long bookingId,
            @Valid @RequestBody PayRequest request) {
        PaymentResult result = paymentService.pay(userId, bookingId, idempotencyKey, request);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(result.payment());
    }
}

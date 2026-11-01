package com.HemashreeVI.seatlock.notification;

import com.HemashreeVI.seatlock.booking.BookingController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository repository;

    public record NotificationResponse(Long id, Long bookingId, String type, String message, Instant createdAt) {
    }

    @GetMapping("/api/notifications")
    public List<NotificationResponse> mine(@RequestHeader(BookingController.USER_HEADER) String userId) {
        return repository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getBookingId(), n.getType(), n.getMessage(),
                        n.getCreatedAt()))
                .toList();
    }
}

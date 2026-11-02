package com.HemashreeVI.seatlock.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository repository;

    public record NotificationResponse(Long id, Long bookingId, String type, String message, Instant createdAt,
                                       String emailTo, EmailStatus emailStatus) {
    }

    @GetMapping("/api/notifications")
    public List<NotificationResponse> mine(Authentication caller) {
        return repository.findTop50ByUserIdOrderByCreatedAtDesc(caller.getName()).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getBookingId(), n.getType(), n.getMessage(),
                        n.getCreatedAt(), n.getEmailTo(), n.getEmailStatus()))
                .toList();
    }
}

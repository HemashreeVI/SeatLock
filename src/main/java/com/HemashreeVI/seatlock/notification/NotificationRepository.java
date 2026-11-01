package com.HemashreeVI.seatlock.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(String userId);

    long countByBookingIdAndType(Long bookingId, String type);

    long countByEmailToAndCreatedAtAfter(String emailTo, java.time.Instant after);

    java.util.Optional<Notification> findFirstByBookingIdAndType(Long bookingId, String type);
}

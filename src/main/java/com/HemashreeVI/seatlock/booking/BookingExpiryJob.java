package com.HemashreeVI.seatlock.booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the expiry sweep every few seconds (seatlock.booking.expiry-sweep-interval-ms). */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryJob {

    private final BookingExpiryService expiryService;
    private final com.HemashreeVI.seatlock.config.BookingProperties properties;

    @Scheduled(fixedDelayString = "${seatlock.booking.expiry-sweep-interval-ms:5000}")
    public void sweep() {
        try {
            int expired;
            do {
                expired = expiryService.expireBatch();
            } while (expired == properties.expirySweepBatchSize());
        } catch (RuntimeException e) {
            log.warn("Hold expiry sweep failed, will retry next run", e);
        }
    }
}

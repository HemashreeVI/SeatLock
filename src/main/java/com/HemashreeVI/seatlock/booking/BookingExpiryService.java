package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.config.BookingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Releases holds whose 10 minutes are up. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingExpiryService {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final BookingProperties properties;

    /** Expires one batch of overdue holds in one transaction. Returns how many were expired. */
    @Transactional
    public int expireBatch() {
        List<Long> ids = bookingRepository.lockExpiredHeldIds(Instant.now(), properties.expirySweepBatchSize());
        for (Long id : ids) {
            Booking booking = bookingRepository.findById(id).orElseThrow();
            bookingService.release(booking, BookingStatus.EXPIRED);
        }
        if (!ids.isEmpty()) {
            log.info("Expired {} hold(s)", ids.size());
        }
        return ids.size();
    }
}

package com.HemashreeVI.seatlock.booking.locking;

import com.HemashreeVI.seatlock.seat.Seat;
import com.HemashreeVI.seatlock.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Optimistic: read without locking, then UPDATE ... WHERE version = ?.
 * If someone else changed the seat in the meantime, 0 rows match and Hibernate throws
 * an OptimisticLockException -> the loser gets HTTP 409.
 * Great when conflicts are rare (no waiting); wasteful under heavy contention (many retries).
 */
@Component
@RequiredArgsConstructor
public class OptimisticSeatLockStrategy implements SeatLockStrategy {

    private final SeatRepository seatRepository;

    @Override
    public LockingStrategyType type() {
        return LockingStrategyType.OPTIMISTIC;
    }

    @Override
    public List<Seat> loadSeats(List<Long> sortedSeatIds) {
        return seatRepository.findAllByIdOrdered(sortedSeatIds);
    }
}

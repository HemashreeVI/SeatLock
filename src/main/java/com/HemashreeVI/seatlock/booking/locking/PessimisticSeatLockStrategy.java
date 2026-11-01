package com.HemashreeVI.seatlock.booking.locking;

import com.HemashreeVI.seatlock.seat.Seat;
import com.HemashreeVI.seatlock.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pessimistic: SELECT ... FOR UPDATE. The first transaction locks the seat rows; everyone else
 * WAITS until it commits, then re-reads the seat, sees it is HELD and gets HTTP 409.
 * Simple and exact under heavy contention, but holds DB connections while waiting.
 */
@Component
@RequiredArgsConstructor
public class PessimisticSeatLockStrategy implements SeatLockStrategy {

    private final SeatRepository seatRepository;

    @Override
    public LockingStrategyType type() {
        return LockingStrategyType.PESSIMISTIC;
    }

    @Override
    public List<Seat> loadSeats(List<Long> sortedSeatIds) {
        return seatRepository.findAllByIdForUpdate(sortedSeatIds);
    }
}

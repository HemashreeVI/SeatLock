package com.HemashreeVI.seatlock.booking.locking;

import com.HemashreeVI.seatlock.seat.Seat;

import java.util.List;
import java.util.function.Supplier;

/**
 * How we stop two people from holding the same seat at the same time.
 * <p>
 * Whatever the strategy, the database keeps two extra safety nets:
 * the seat's @Version column and the partial unique index on booking_seats.
 */
public interface SeatLockStrategy {

    LockingStrategyType type();

    /**
     * Runs {@code action} (which opens its own DB transaction) while holding any
     * locks this strategy needs OUTSIDE the database. Default: none.
     */
    default <T> T withSeatsLocked(List<Long> sortedSeatIds, Supplier<T> action) {
        return action.get();
    }

    /** Loads the seats inside the transaction, possibly locking the rows. Ordered by id. */
    List<Seat> loadSeats(List<Long> sortedSeatIds);
}

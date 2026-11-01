package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.booking.locking.LockingStrategyType;
import com.HemashreeVI.seatlock.booking.locking.SeatLockStrategy;
import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE core guarantee: no matter how many users hit the same seats at the same instant,
 * every seat ends up in at most one booking. Runs once per locking strategy.
 */
class BookingConcurrencyTest extends IntegrationTest {

    private static final int USERS = 200;

    @Autowired
    BookingService bookingService;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(LockingStrategyType.class)
    void manyUsersOneSeat_exactlyOneWins(LockingStrategyType type) throws Exception {
        long eventId = createEvent("FLOOR", 1, 10, 1000);
        long seatId = seatIds(eventId).getFirst();
        SeatLockStrategy strategy = bookingService.strategy(type);

        Outcome outcome = race(USERS, i -> bookingService.hold("user-" + i,
                new HoldRequest(eventId, List.of(seatId)), null, strategy));

        assertThat(outcome.unexpected).as("unexpected errors").isEmpty();
        assertThat(outcome.successes.get()).as("winners").isEqualTo(1);
        assertThat(outcome.conflicts.get()).as("losers got 409").isEqualTo(USERS - 1);
        assertNoDoubleBooking(eventId);
        assertThat(jdbc.queryForObject("select count(*) from seats where event_id = ? and status = 'HELD'",
                Integer.class, eventId)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(LockingStrategyType.class)
    void manyUsersOverlappingMultiSeatRequests_noSeatSoldTwice(LockingStrategyType type) throws Exception {
        long eventId = createEvent("FLOOR", 2, 10, 1000);   // 20 seats
        List<Long> seats = seatIds(eventId);
        SeatLockStrategy strategy = bookingService.strategy(type);
        Random random = new Random(42);
        List<List<Long>> requests = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            List<Long> shuffled = new ArrayList<>(seats);
            Collections.shuffle(shuffled, random);
            requests.add(shuffled.subList(0, 1 + random.nextInt(4)));   // 1..4 random seats each
        }

        Outcome outcome = race(USERS, i -> bookingService.hold("user-" + i,
                new HoldRequest(eventId, requests.get(i)), null, strategy));

        assertThat(outcome.unexpected).as("unexpected errors").isEmpty();
        assertThat(outcome.successes.get()).isPositive();
        assertThat(outcome.successes.get() + outcome.conflicts.get()).isEqualTo(USERS);
        assertNoDoubleBooking(eventId);
        // every HELD seat belongs to exactly one live booking, and nothing else is held
        Integer heldSeats = jdbc.queryForObject(
                "select count(*) from seats where event_id = ? and status = 'HELD'", Integer.class, eventId);
        Integer seatsInLiveBookings = jdbc.queryForObject("""
                select count(*) from booking_seats bs join bookings b on b.id = bs.booking_id
                where b.event_id = ? and b.status = 'HELD' and bs.active""", Integer.class, eventId);
        assertThat(heldSeats).isEqualTo(seatsInLiveBookings);
    }

    private void assertNoDoubleBooking(long eventId) {
        assertThat(bookingRepository.countDoubleBookedSeats(eventId)).as("double-booked seats").isZero();
    }

    interface Attempt {
        void run(int i) throws Exception;
    }

    record Outcome(AtomicInteger successes, AtomicInteger conflicts, ConcurrentLinkedQueue<Throwable> unexpected) {
    }

    /** Fires all attempts at the same instant using a start gate. */
    static Outcome race(int count, Attempt attempt) throws InterruptedException {
        Outcome outcome = new Outcome(new AtomicInteger(), new AtomicInteger(), new ConcurrentLinkedQueue<>());
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        try (ExecutorService pool = Executors.newFixedThreadPool(64)) {
            for (int i = 0; i < count; i++) {
                int n = i;
                pool.submit(() -> {
                    try {
                        startGate.await();
                        attempt.run(n);
                        outcome.successes.incrementAndGet();
                    } catch (SeatUnavailableException e) {
                        outcome.conflicts.incrementAndGet();
                    } catch (Throwable t) {
                        outcome.unexpected.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            startGate.countDown();
            assertThat(done.await(2, TimeUnit.MINUTES)).isTrue();
        }
        return outcome;
    }
}

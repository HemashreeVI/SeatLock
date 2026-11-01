package com.HemashreeVI.seatlock.support;

import com.HemashreeVI.seatlock.booking.SeatUnavailableException;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Fires many attempts at the same instant (start gate) and tallies the outcomes. */
public final class Race {

    private Race() {
    }

    public interface Attempt {
        void run(int i) throws Exception;
    }

    /** conflicts = SeatUnavailableException (HTTP 409); anything else unexpected is collected. */
    public record Outcome(AtomicInteger successes, AtomicInteger conflicts, ConcurrentLinkedQueue<Throwable> unexpected) {
    }

    public static Outcome race(int count, Attempt attempt) throws InterruptedException {
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
                        outcome.successes().incrementAndGet();
                    } catch (SeatUnavailableException e) {
                        outcome.conflicts().incrementAndGet();
                    } catch (Throwable t) {
                        outcome.unexpected().add(t);
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

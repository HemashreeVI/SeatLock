package com.HemashreeVI.seatlock.queue;

import java.time.Instant;

/**
 * @param state                WAITING, ADMITTED or NOT_IN_QUEUE
 * @param position             1-based place in line while WAITING
 * @param admissionToken       send as X-Queue-Token when holding seats (ADMITTED only)
 */
public record QueueStatus(State state, Long position, Long estimatedWaitSeconds,
                          String admissionToken, Instant admissionExpiresAt) {

    public enum State { WAITING, ADMITTED, NOT_IN_QUEUE }

    static QueueStatus waiting(long position, long estimatedWaitSeconds) {
        return new QueueStatus(State.WAITING, position, estimatedWaitSeconds, null, null);
    }

    static QueueStatus admitted(String token, Instant expiresAt) {
        return new QueueStatus(State.ADMITTED, null, null, token, expiresAt);
    }

    static QueueStatus notInQueue() {
        return new QueueStatus(State.NOT_IN_QUEUE, null, null, null, null);
    }
}

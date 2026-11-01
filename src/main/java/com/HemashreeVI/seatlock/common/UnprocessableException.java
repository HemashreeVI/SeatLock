package com.HemashreeVI.seatlock.common;

/** Well-formed request that cannot be honoured, e.g. a reused idempotency key (HTTP 422). */
public class UnprocessableException extends RuntimeException {
    public UnprocessableException(String message) {
        super(message);
    }
}

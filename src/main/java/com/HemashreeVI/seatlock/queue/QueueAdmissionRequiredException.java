package com.HemashreeVI.seatlock.queue;

/** Hot event and the user has no valid admission token (HTTP 403). */
public class QueueAdmissionRequiredException extends RuntimeException {
    public QueueAdmissionRequiredException(String message) {
        super(message);
    }
}

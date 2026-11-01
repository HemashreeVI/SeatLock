package com.HemashreeVI.seatlock.messaging;

/** How outbox messages travel between modules (seatlock.messaging.mode). */
public final class MessagingMode {

    /** Property that selects the mode. */
    public static final String PROPERTY = "seatlock.messaging.mode";
    /** Through Apache Kafka (default). */
    public static final String KAFKA = "kafka";
    /** In-process, straight from the outbox table to the handlers: no broker needed (demo deployments). */
    public static final String LOCAL = "local";

    private MessagingMode() {
    }
}

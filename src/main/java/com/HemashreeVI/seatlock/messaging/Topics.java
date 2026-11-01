package com.HemashreeVI.seatlock.messaging;

/** Kafka topic names. */
public final class Topics {

    /** booking module -> payment module: "please charge this booking". */
    public static final String PAYMENT_REQUESTED = "seatlock.payments.requested";
    /** payment module -> booking module: "the charge succeeded / failed". */
    public static final String PAYMENT_COMPLETED = "seatlock.payments.completed";
    /** booking lifecycle facts (confirmed, cancelled, expired) for anyone interested. */
    public static final String BOOKING_EVENTS = "seatlock.bookings.events";

    /** Kafka header carrying the outbox row id (stable across redeliveries). */
    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_EVENT_TYPE = "eventType";

    /** Spring Kafka's default dead-letter topic suffix. */
    public static final String DLT_SUFFIX = "-dlt";

    private Topics() {
    }
}

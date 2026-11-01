package com.HemashreeVI.seatlock.messaging;

/**
 * A consumer of one topic. Implementations are called by Kafka (@KafkaListener, default mode) or
 * by the {@link LocalOutboxDispatcher} (demo mode, no broker). Either way delivery is
 * at-least-once, so implementations must be idempotent.
 */
public interface MessageHandler {

    String topic();

    /**
     * @param payload JSON message body
     * @param eventId id of the outbox row that produced the message (stable across redeliveries)
     */
    void handle(String payload, String eventId);
}

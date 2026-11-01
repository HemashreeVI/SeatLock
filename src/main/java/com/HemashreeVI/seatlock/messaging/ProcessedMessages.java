package com.HemashreeVI.seatlock.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent-consumer helper ("inbox"). */
@Component
@RequiredArgsConstructor
public class ProcessedMessages {

    private final JdbcTemplate jdbc;

    /**
     * Marks the message as processed by this consumer, in the caller's transaction.
     * Returns false if it was already processed (a redelivery) and must be skipped.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(String consumer, String messageId) {
        int inserted = jdbc.update("""
                INSERT INTO processed_messages (consumer, message_id) VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """, consumer, messageId);
        return inserted == 1;
    }
}

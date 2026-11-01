package com.HemashreeVI.seatlock.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = MessagingMode.PROPERTY, havingValue = MessagingMode.KAFKA, matchIfMissing = true)
public class OutboxRelayJob {

    private static final int BATCH = 100;

    private final OutboxRelay relay;

    @Scheduled(fixedDelayString = "${seatlock.outbox.relay-interval-ms:200}")
    public void run() {
        try {
            int published;
            do {
                published = relay.publishBatch(BATCH);
            } while (published == BATCH);
        } catch (RuntimeException e) {
            log.warn("Outbox relay run failed, will retry", e);
        }
    }
}

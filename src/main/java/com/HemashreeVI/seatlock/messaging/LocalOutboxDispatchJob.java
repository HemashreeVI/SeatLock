package com.HemashreeVI.seatlock.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = MessagingMode.PROPERTY, havingValue = MessagingMode.LOCAL)
public class LocalOutboxDispatchJob {

    private static final int BATCH = 100;

    private final LocalOutboxDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${seatlock.outbox.relay-interval-ms:200}")
    public void run() {
        try {
            int delivered;
            do {
                delivered = dispatcher.dispatchBatch(BATCH);
            } while (delivered == BATCH);
        } catch (RuntimeException e) {
            log.warn("Local outbox dispatch failed, will retry", e);
        }
    }
}

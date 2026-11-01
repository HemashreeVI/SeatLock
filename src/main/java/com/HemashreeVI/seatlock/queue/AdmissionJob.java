package com.HemashreeVI.seatlock.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seatlock.queue.auto-admit", havingValue = "true", matchIfMissing = true)
public class AdmissionJob {

    private final WaitingRoomService waitingRoom;

    @Scheduled(fixedDelayString = "${seatlock.queue.admit-interval-ms:1000}")
    public void admit() {
        try {
            waitingRoom.admitAll();
        } catch (RuntimeException e) {
            log.warn("Waiting room admission failed, will retry", e);
        }
    }
}

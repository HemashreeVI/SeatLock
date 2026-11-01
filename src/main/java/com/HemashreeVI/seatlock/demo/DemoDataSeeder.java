package com.HemashreeVI.seatlock.demo;

import com.HemashreeVI.seatlock.event.CreateEventRequest;
import com.HemashreeVI.seatlock.event.Event;
import com.HemashreeVI.seatlock.event.EventService;
import com.HemashreeVI.seatlock.seat.SeatRepository;
import com.HemashreeVI.seatlock.seat.SeatStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * For the public demo: on every start (free hosts restart the app after it sleeps), make sure
 * visitors find an upcoming event with free seats. Creates one only if none exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seatlock.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private final EventService eventService;
    private final SeatRepository seatRepository;

    @Override
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        if (seatRepository.countByStatusInEventsStartingAfter(SeatStatus.AVAILABLE, now) > 0) {
            return;
        }
        Event event = eventService.createEvent(new CreateEventRequest(
                "SeatLock Live: Demo Night",
                "The Concurrency Arena",
                now.plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS),
                List.of(
                        new CreateEventRequest.SectionRequest("VIP", 2, 8, 450000),
                        new CreateEventRequest.SectionRequest("GENERAL", 4, 12, 150000)),
                false));
        log.info("Demo: created event {} with 64 seats", event.getId());
    }
}

package com.HemashreeVI.seatlock.demo;

import com.HemashreeVI.seatlock.event.CreateEventRequest;
import com.HemashreeVI.seatlock.event.CreateEventRequest.SectionRequest;
import com.HemashreeVI.seatlock.event.Event;
import com.HemashreeVI.seatlock.event.EventCategory;
import com.HemashreeVI.seatlock.event.EventRepository;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * For the public demo: on every start (free hosts restart the app after it sleeps), make sure the
 * home page shows a realistic line-up. Every event in the catalog that has no upcoming edition with
 * free seats gets a fresh one. All events and venues are fictional.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seatlock.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    record CatalogEvent(String name, String venue, EventCategory category, int daysAhead, int hour,
                        List<SectionRequest> sections) {
    }

    static final List<CatalogEvent> CATALOG = List.of(
            new CatalogEvent("Neon Nights: Indie Music Festival", "Riverside Arena, Mumbai", EventCategory.CONCERT, 12, 19,
                    List.of(new SectionRequest("VIP", 2, 10, 499900),
                            new SectionRequest("GOLD", 4, 14, 249900),
                            new SectionRequest("SILVER", 4, 16, 129900))),
            new CatalogEvent("The Laugh Riot: Stand-up Special", "The Comedy Loft, Bengaluru", EventCategory.COMEDY, 6, 20,
                    List.of(new SectionRequest("FRONT ROW", 1, 12, 149900),
                            new SectionRequest("PREMIUM", 3, 14, 99900),
                            new SectionRequest("STANDARD", 4, 14, 59900))),
            new CatalogEvent("City Derby: Football Finals", "Central Stadium, Chennai", EventCategory.SPORTS, 18, 18,
                    List.of(new SectionRequest("PAVILION", 3, 16, 349900),
                            new SectionRequest("EAST STAND", 5, 18, 119900),
                            new SectionRequest("WEST STAND", 5, 18, 99900))),
            new CatalogEvent("Romeo & Juliet: A Modern Retelling", "Grand Playhouse, New Delhi", EventCategory.THEATRE, 9, 19,
                    List.of(new SectionRequest("ORCHESTRA", 4, 12, 189900),
                            new SectionRequest("MEZZANINE", 3, 14, 109900),
                            new SectionRequest("BALCONY", 3, 14, 69900))),
            new CatalogEvent("Symphony Under the Stars", "Lakeside Amphitheatre, Pune", EventCategory.CONCERT, 24, 18,
                    List.of(new SectionRequest("PLATINUM", 2, 12, 299900),
                            new SectionRequest("LAWN", 5, 16, 89900))));

    private final EventService eventService;
    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    @Override
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        List<Event> upcoming = eventRepository.findAll().stream().filter(e -> e.getStartsAt().isAfter(now)).toList();
        for (CatalogEvent item : CATALOG) {
            boolean onSale = upcoming.stream()
                    .filter(e -> e.getName().equals(item.name()))
                    .anyMatch(e -> !seatRepository.findByEventIdAndStatus(e.getId(), SeatStatus.AVAILABLE).isEmpty());
            if (onSale) {
                continue;
            }
            Instant startsAt = ZonedDateTime.now(INDIA).plusDays(item.daysAhead())
                    .withHour(item.hour()).withMinute(0).withSecond(0).withNano(0).toInstant();
            Event event = eventService.createEvent(new CreateEventRequest(item.name(), item.venue(), startsAt,
                    item.sections(), false, item.category()));
            log.info("Demo: created \"{}\" (event {})", item.name(), event.getId());
        }
    }
}

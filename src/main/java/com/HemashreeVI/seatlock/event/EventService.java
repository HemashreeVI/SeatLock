package com.HemashreeVI.seatlock.event;

import com.HemashreeVI.seatlock.common.BadRequestException;
import com.HemashreeVI.seatlock.common.NotFoundException;
import com.HemashreeVI.seatlock.seat.Seat;
import com.HemashreeVI.seatlock.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    /** Creates the event and generates all its seats in ONE transaction (all or nothing). */
    @Transactional
    public Event createEvent(CreateEventRequest request) {
        Set<String> sectionNames = new HashSet<>();
        for (CreateEventRequest.SectionRequest section : request.sections()) {
            if (!sectionNames.add(section.name())) {
                throw new BadRequestException("Duplicate section name: " + section.name());
            }
        }

        Event event = new Event();
        event.setName(request.name());
        event.setVenue(request.venue());
        event.setStartsAt(request.startsAt());
        event.setQueueEnabled(Boolean.TRUE.equals(request.queueEnabled()));
        event.setCategory(request.category() == null ? EventCategory.OTHER : request.category());
        eventRepository.save(event);

        List<Seat> seats = new ArrayList<>();
        for (CreateEventRequest.SectionRequest section : request.sections()) {
            for (int r = 0; r < section.rows(); r++) {
                String rowLabel = String.valueOf((char) ('A' + r));   // 0 -> "A", 1 -> "B", ...
                for (int number = 1; number <= section.seatsPerRow(); number++) {
                    Seat seat = new Seat();
                    seat.setEvent(event);
                    seat.setSection(section.name());
                    seat.setRowLabel(rowLabel);
                    seat.setSeatNumber(number);
                    seat.setPriceCents(section.priceCents());
                    seats.add(seat);
                }
            }
        }
        seatRepository.saveAll(seats);
        return event;
    }

    @Transactional(readOnly = true)
    public Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public void assertExists(Long id) {
        if (!eventRepository.existsById(id)) {
            throw new NotFoundException("Event " + id + " not found");
        }
    }

    /** Seat counts and price range for every event, in one query. */
    @Transactional(readOnly = true)
    public java.util.Map<Long, EventResponse.EventSummary> summaries() {
        java.util.Map<Long, EventResponse.EventSummary> result = new java.util.HashMap<>();
        for (Object[] row : seatRepository.summarizeAll()) {
            result.put((Long) row[0], new EventResponse.EventSummary(((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(), ((Number) row[3]).longValue(), ((Number) row[4]).longValue()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Event> listEvents() {
        return eventRepository.findAll(Sort.by("startsAt"));
    }
}

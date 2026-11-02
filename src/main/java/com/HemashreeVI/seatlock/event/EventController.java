package com.HemashreeVI.seatlock.event;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        Event event = eventService.createEvent(request);
        return ResponseEntity
                .created(URI.create("/api/events/" + event.getId()))
                .body(EventResponse.from(event, eventService.summaries().get(event.getId())));
    }

    /** All events, with seats left and price range for the event cards. */
    @GetMapping
    public List<EventResponse> list() {
        Map<Long, EventResponse.EventSummary> summaries = eventService.summaries();
        return eventService.listEvents().stream().map(e -> EventResponse.from(e, summaries.get(e.getId()))).toList();
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable Long id) {
        return EventResponse.from(eventService.getEvent(id), eventService.summaries().get(id));
    }
}

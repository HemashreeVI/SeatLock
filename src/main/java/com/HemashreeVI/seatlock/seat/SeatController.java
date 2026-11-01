package com.HemashreeVI.seatlock.seat;

import com.HemashreeVI.seatlock.event.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatRepository seatRepository;
    private final EventService eventService;

    @GetMapping("/api/events/{eventId}/seats")
    public List<SeatResponse> list(@PathVariable Long eventId,
                                   @RequestParam(required = false) SeatStatus status) {
        eventService.assertExists(eventId); // 404 instead of an empty list for unknown events

        List<Seat> seats = (status == null)
                ? seatRepository.findByEventIdOrderBySectionAscRowLabelAscSeatNumberAsc(eventId)
                : seatRepository.findByEventIdAndStatus(eventId, status);

        return seats.stream().map(SeatResponse::from).toList();
    }
}

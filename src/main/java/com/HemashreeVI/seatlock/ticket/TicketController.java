package com.HemashreeVI.seatlock.ticket;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService tickets;

    /** The caller's tickets (one per seat of each confirmed booking). */
    @GetMapping
    public List<TicketView> mine(Authentication caller) {
        return tickets.mine(caller.getName());
    }

    /** The ticket's QR code as a PNG: show it at the entrance, or save/print it. */
    @GetMapping(value = "/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(Authentication caller, @PathVariable Long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(tickets.qrPng(caller.getName(), id));
    }

    public record CheckInRequest(@NotBlank @Size(max = 100) String code) {
    }

    /** Organizer only: check a ticket in by its code (typed, or read by a camera on the device). */
    @PostMapping("/check-in")
    public CheckInResult checkIn(Authentication staff, @Valid @RequestBody CheckInRequest request) {
        return tickets.checkIn(request.code(), staffName(staff));
    }

    /** Organizer only: check a ticket in from a photo of its QR code (works on any phone). */
    @PostMapping(value = "/check-in/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CheckInResult scan(Authentication staff, @RequestParam("image") MultipartFile image) throws IOException {
        return tickets.checkInPhoto(image.getBytes(), staffName(staff));
    }

    /** Shown to the next scanner of an already-used ticket ("checked in by ..."). */
    private static String staffName(Authentication staff) {
        if (staff instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaimAsString("name") != null) {
            return jwt.getToken().getClaimAsString("name");
        }
        return staff.getName();
    }
}

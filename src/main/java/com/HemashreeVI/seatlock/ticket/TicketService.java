package com.HemashreeVI.seatlock.ticket;

import com.HemashreeVI.seatlock.booking.Booking;
import com.HemashreeVI.seatlock.common.NotFoundException;
import com.HemashreeVI.seatlock.seat.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    /** Printed in the QR code in front of the code, so scanners can tell SeatLock tickets apart. */
    public static final String QR_PREFIX = "SEATLOCK-";

    private final TicketRepository tickets;
    private final QrCodes qr;
    private final SecureRandom random = new SecureRandom();

    /** One ticket per seat; runs inside the transaction that confirms the booking. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void issueFor(Booking booking, List<Seat> seats) {
        if (tickets.countByBookingId(booking.getId()) > 0) {
            return;   // already issued (defensive: confirm only happens once)
        }
        for (Seat seat : seats) {
            Ticket ticket = new Ticket();
            ticket.setBookingId(booking.getId());
            ticket.setSeat(seat);
            ticket.setEvent(booking.getEvent());
            ticket.setUserId(booking.getUserId());
            ticket.setHolderName(booking.getCustomerName());
            ticket.setCode(newCode());
            tickets.save(ticket);
        }
        log.info("Issued {} ticket(s) for booking {}", seats.size(), booking.getId());
    }

    @Transactional(readOnly = true)
    public List<TicketView> mine(String userId) {
        return tickets.findMine(userId).stream().map(TicketView::of).toList();
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(String userId, Long ticketId) {
        Ticket ticket = tickets.findById(ticketId)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Ticket " + ticketId + " not found"));
        return qr.png(QR_PREFIX + ticket.getCode());
    }

    /** Lets the holder in if (and only if) the ticket is valid and hasn't been used yet. */
    @Transactional
    public CheckInResult checkIn(String scanned, String staff) {
        String code = normalize(scanned);
        if (code.isEmpty()) {
            return new CheckInResult(CheckInResult.Result.INVALID, "No ticket code", null, null);
        }
        boolean admitted = tickets.markUsed(code, staff) == 1;
        return tickets.findByCode(code)
                .map(t -> admitted
                        ? new CheckInResult(CheckInResult.Result.ADMITTED, "Valid ticket. Let them in.",
                        TicketView.of(t), t.getCheckedInAt())
                        : new CheckInResult(CheckInResult.Result.ALREADY_USED,
                        "Already used" + (t.getCheckedInBy() == null ? "" : " (checked in by " + t.getCheckedInBy() + ")"),
                        TicketView.of(t), t.getCheckedInAt()))
                .orElseGet(() -> new CheckInResult(CheckInResult.Result.INVALID,
                        "Not a SeatLock ticket, or the code is wrong", null, null));
    }

    @Transactional
    public CheckInResult checkInPhoto(byte[] image, String staff) {
        return checkIn(qr.read(image), staff);
    }

    /** Accepts the full QR text or just the code, any case, with stray spaces. */
    static String normalize(String scanned) {
        String s = scanned == null ? "" : scanned.strip().toUpperCase(Locale.ROOT).replace(" ", "");
        return s.startsWith(QR_PREFIX) ? s.substring(QR_PREFIX.length()) : s;
    }

    private String newCode() {
        byte[] bytes = new byte[16];   // 128 random bits: guessing a valid code is hopeless
        random.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }
}

package com.HemashreeVI.seatlock.notification.email;

import com.HemashreeVI.seatlock.config.EmailProperties;
import com.HemashreeVI.seatlock.messaging.Messages;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/** Builds the notification emails for booking events. */
@Component
public class BookingEmails {

    private final EmailProperties properties;

    public BookingEmails(EmailProperties properties) {
        this.properties = properties;
    }

    public Email build(Messages.BookingEvent event) {
        String eventName = event.eventName() == null ? "your event" : event.eventName();
        String headline;
        String subject;
        String intro;
        switch (event.type()) {
            case "BookingConfirmed" -> {
                subject = "Booking #%d confirmed: %s".formatted(event.bookingId(), eventName);
                headline = "You're in! 🎟️";
                intro = "Your payment went through and these seats are now yours.";
            }
            case "BookingExpired" -> {
                subject = "Your hold on booking #%d expired".formatted(event.bookingId());
                headline = "Your hold expired";
                intro = "The 10 minutes to pay ran out, so these seats went back on sale.";
            }
            default -> {
                subject = "Booking #%d cancelled".formatted(event.bookingId());
                headline = "Booking cancelled";
                intro = "This booking was cancelled (by you, or because the payment was declined) and the seats were released.";
            }
        }
        String hello = event.customerName() == null ? "Hi," : "Hi " + esc(event.customerName()) + ",";
        List<String> seats = event.seatLabels() == null ? List.of() : event.seatLabels();
        String html = """
                <div style="font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;max-width:520px;margin:0 auto;color:#17191c">
                  <h2 style="margin:0 0 12px">%s</h2>
                  <p>%s</p>
                  <p>%s</p>
                  <table style="border-collapse:collapse;width:100%%;margin:16px 0">
                    <tr><td style="padding:6px 0;color:#5d6470">Booking</td><td style="padding:6px 0"><b>#%d</b></td></tr>
                    <tr><td style="padding:6px 0;color:#5d6470">Event</td><td style="padding:6px 0">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#5d6470">Seats</td><td style="padding:6px 0">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#5d6470">Total</td><td style="padding:6px 0">%s</td></tr>
                  </table>
                  <p><a href="%s" style="color:#3451d1">Open SeatLock</a></p>
                  <p style="color:#5d6470;font-size:12px">You're receiving this because this address was entered for a booking on the SeatLock demo.</p>
                </div>
                """.formatted(headline, hello, intro, event.bookingId(), esc(eventName),
                seats.isEmpty() ? "-" : esc(String.join(", ", seats)),
                money(event.totalCents()), esc(properties.publicUrl()));
        return new Email(event.customerEmail(), event.customerName(), subject, html);
    }

    private String money(long cents) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        format.setCurrency(Currency.getInstance(properties.currency()));
        return format.format(BigDecimal.valueOf(cents, 2));
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s);
    }
}

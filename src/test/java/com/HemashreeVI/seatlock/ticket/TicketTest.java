package com.HemashreeVI.seatlock.ticket;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import com.HemashreeVI.seatlock.support.Race;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.JsonNode;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TicketTest extends IntegrationTest {

    @Autowired
    QrCodes qrCodes;

    @Autowired
    TicketService ticketService;

    @Test
    void paidBookingGetsOneTicketPerSeatWithAReadableQrCode() throws Exception {
        List<JsonNode> tickets = buyTickets("fan-1", 2);
        assertThat(tickets).hasSize(2);
        JsonNode first = tickets.getFirst();
        assertThat(first.get("status").asString()).isEqualTo("VALID");
        assertThat(first.get("code").asString()).matches("[0-9A-F]{32}");
        assertThat(first.get("seat").asString()).startsWith("FLOOR A");
        assertThat(first.get("holderName").asString()).isEqualTo("Fan One");

        byte[] png = qrBytes("fan-1", first.get("id").asLong());
        assertThat(ImageIO.read(new ByteArrayInputStream(png))).isNotNull();
        assertThat(qrCodes.read(png)).isEqualTo("SEATLOCK-" + first.get("code").asString());

        // someone else can't fetch your QR code
        assertThat(http.get().uri("/api/tickets/" + first.get("id").asLong() + "/qr.png")
                .header("X-User-Id", "someone-else").retrieve().toEntity(byte[].class).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void checkInAdmitsOnceThenRejects() {
        String code = buyTickets("fan-2", 1).getFirst().get("code").asString();

        JsonNode admitted = checkIn(code);
        assertThat(admitted.get("result").asString()).isEqualTo("ADMITTED");
        assertThat(admitted.get("ticket").get("seat").asString()).startsWith("FLOOR A");
        assertThat(admitted.get("ticket").get("holderName").asString()).isEqualTo("Fan One");

        JsonNode again = checkIn(" seatlock-" + code.toLowerCase() + " ");   // tolerant of case / prefix / spaces
        assertThat(again.get("result").asString()).isEqualTo("ALREADY_USED");
        assertThat(again.get("checkedInAt").asString()).isNotBlank();

        assertThat(checkIn("SEATLOCK-" + "0".repeat(32)).get("result").asString()).isEqualTo("INVALID");

        JsonNode mine = ticketsOf("fan-2").getFirst();
        assertThat(mine.get("status").asString()).isEqualTo("USED");
    }

    @Test
    void onlyOrganizersCanCheckIn() {
        String code = buyTickets("fan-3", 1).getFirst().get("code").asString();
        ResponseEntity<JsonNode> res = post("/api/tickets/check-in", Map.of("code", code), "fan-3", Map.of());
        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(post("/api/tickets/check-in", Map.of("code", code), null, Map.of()).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void checkInFromAPhotoOfTheQrCode() throws Exception {
        JsonNode ticket = buyTickets("fan-4", 1).getFirst();
        // simulate a phone photo: the QR code pasted onto a larger, off-white picture
        BufferedImage qr = ImageIO.read(new ByteArrayInputStream(qrBytes("fan-4", ticket.get("id").asLong())));
        BufferedImage photo = new BufferedImage(1400, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = photo.createGraphics();
        g.setColor(new Color(235, 232, 225));
        g.fillRect(0, 0, 1400, 1000);
        g.drawImage(qr, 500, 300, 420, 420, null);
        g.dispose();
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        ImageIO.write(photo, "jpg", jpg);

        JsonNode result = scan(jpg.toByteArray(), "ticket-photo.jpg");
        assertThat(result.get("result").asString()).isEqualTo("ADMITTED");
        assertThat(result.get("ticket").get("code").asString()).isEqualTo(ticket.get("code").asString());

        // a photo without a QR code gets a clear error
        ByteArrayOutputStream blank = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", blank);
        assertThat(scanStatus(blank.toByteArray())).isEqualTo(400);
    }

    @Test
    void twoGatesScanningTheSameTicketAtOnceAdmitOnlyOne() throws Exception {
        String code = buyTickets("fan-5", 1).getFirst().get("code").asString();
        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        Race.Outcome outcome = Race.race(20, i -> {
            CheckInResult r = ticketService.checkIn(code, "gate-" + i);
            (r.result() == CheckInResult.Result.ADMITTED ? admitted : rejected).incrementAndGet();
        });

        assertThat(outcome.unexpected()).isEmpty();
        assertThat(admitted.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(19);
    }

    // ------------------------------------------------------------------ helpers

    private List<JsonNode> buyTickets(String user, int seats) {
        long eventId = createEvent("FLOOR", 1, seats, 50000);
        long bookingId = post("/api/bookings", Map.of("eventId", eventId, "seatIds", seatIds(eventId), "name", "Fan One"),
                user, Map.of()).getBody().get("id").asLong();
        post("/api/bookings/" + bookingId + "/payments", Map.of("paymentToken", "tok_visa"), user,
                Map.of("Idempotency-Key", "pay-" + user));
        await().atMost(Duration.ofSeconds(20)).until(() -> ticketsOf(user).size() == seats);
        return ticketsOf(user);
    }

    private List<JsonNode> ticketsOf(String user) {
        return get("/api/tickets", user).getBody().valueStream().toList();
    }

    private byte[] qrBytes(String user, long ticketId) {
        return http.get().uri("/api/tickets/" + ticketId + "/qr.png").header("X-User-Id", user)
                .retrieve().body(byte[].class);
    }

    private JsonNode checkIn(String code) {
        return post("/api/tickets/check-in", Map.of("code", code), null, admin()).getBody();
    }

    private JsonNode scan(byte[] image, String filename) {
        return http.post().uri("/api/tickets/check-in/scan")
                .headers(h -> admin().forEach(h::add))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form(image, filename))
                .retrieve().toEntity(JsonNode.class).getBody();
    }

    private int scanStatus(byte[] image) {
        return http.post().uri("/api/tickets/check-in/scan")
                .headers(h -> admin().forEach(h::add))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form(image, "blank.png"))
                .retrieve().toEntity(JsonNode.class).getStatusCode().value();
    }

    private static LinkedMultiValueMap<String, Object> form(byte[] image, String filename) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("image", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return form;
    }
}

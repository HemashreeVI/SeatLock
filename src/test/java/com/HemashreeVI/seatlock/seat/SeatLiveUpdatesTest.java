package com.HemashreeVI.seatlock.seat;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SeatLiveUpdatesTest extends IntegrationTest {

    @Autowired
    Environment environment;

    @Autowired
    SeatLiveUpdates liveUpdates;

    @Test
    void viewersSeeASeatTurnHeldWithinAMoment() {
        long eventId = createEvent("PIT", 1, 3, 100);
        List<Long> seats = seatIds(eventId);
        long seatId = seats.getFirst();

        ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:"
                        + environment.getProperty("local.server.port") + "/api/events/" + eventId + "/seats/stream"))
                .header("Accept", "text/event-stream").build();
        CompletableFuture<HttpResponse<Stream<String>>> response =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofLines());
        response.thenAccept(r -> r.body().forEach(lines::add));

        // on connect: a snapshot with every seat AVAILABLE
        await().atMost(Duration.ofSeconds(10)).until(() -> lines.stream().anyMatch(
                l -> l.startsWith("data:") && l.contains("\"id\":" + seatId) && l.contains("AVAILABLE")));
        assertThat(lines).contains("event:seats");

        post("/api/bookings", Map.of("eventId", eventId, "seatIds", List.of(seatId)), "live-viewer", Map.of());

        // pushed, not polled: the next "seats" event shows the seat HELD
        await().atMost(Duration.ofSeconds(5)).until(() -> lines.stream().anyMatch(
                l -> l.startsWith("data:") && l.contains("{\"id\":" + seatId + ",\"status\":\"HELD\"}")));
        assertThat(liveUpdates.subscriberCount()).isPositive();

        // a stream never "finishes", so close() would wait forever: abort it instead
        client.shutdownNow();
    }

    @Test
    void unknownEventStreamIs404() {
        assertThat(get("/api/events/987654321/seats/stream", null).getStatusCode().value()).isEqualTo(404);
    }
}

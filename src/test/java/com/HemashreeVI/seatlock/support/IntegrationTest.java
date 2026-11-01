package com.HemashreeVI.seatlock.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Base class: boots the full app on a random port against Testcontainers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "seatlock.rate-limit.capacity=50",
        "seatlock.rate-limit.refill-per-second=5",
        "seatlock.queue.auto-admit=false"      // tests drive admissions explicitly
})
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    private Environment environment;

    protected RestClient http;

    @BeforeEach
    void setUpHttp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* let tests assert */ })
                .build();
    }

    protected ResponseEntity<JsonNode> get(String path, String userId) {
        return http.get().uri(path)
                .headers(h -> addUser(h, userId))
                .retrieve().toEntity(JsonNode.class);
    }

    protected ResponseEntity<JsonNode> post(String path, Object body, String userId, Map<String, String> headers) {
        return http.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    addUser(h, userId);
                    headers.forEach(h::add);
                })
                .body(body == null ? Map.of() : body)
                .retrieve().toEntity(JsonNode.class);
    }

    protected ResponseEntity<JsonNode> delete(String path, String userId) {
        return http.delete().uri(path)
                .headers(h -> addUser(h, userId))
                .retrieve().toEntity(JsonNode.class);
    }

    private static void addUser(HttpHeaders h, String userId) {
        if (userId != null) {
            h.add("X-User-Id", userId);
        }
    }

    /** Creates an event with one section and returns its id. */
    protected long createEvent(String section, int rows, int seatsPerRow, long priceCents) {
        Map<String, Object> body = Map.of(
                "name", "Test Event",
                "venue", "Test Arena",
                "startsAt", Instant.now().plus(30, ChronoUnit.DAYS).toString(),
                "sections", List.of(Map.of(
                        "name", section, "rows", rows, "seatsPerRow", seatsPerRow, "priceCents", priceCents)));
        ResponseEntity<JsonNode> res = post("/api/events", body, null, Map.of());
        if (res.getStatusCode().value() != 201) {
            throw new IllegalStateException("Could not create event: " + res);
        }
        return res.getBody().get("id").asLong();
    }

    protected List<Long> seatIds(long eventId) {
        JsonNode seats = get("/api/events/" + eventId + "/seats", null).getBody();
        return seats.valueStream().map(s -> s.get("id").asLong()).toList();
    }
}

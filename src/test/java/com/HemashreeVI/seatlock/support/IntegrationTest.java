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
        "seatlock.rate-limit.ip-capacity=1000000",   // every test request comes from 127.0.0.1
        "seatlock.rate-limit.ip-refill-per-second=100000",
        "seatlock.queue.auto-admit=false",     // tests drive admissions explicitly
        "seatlock.rate-limit.login-capacity=1000",
        "seatlock.rate-limit.login-refill-per-second=1000",
        // most tests act as many users at once; signing each up would add nothing, so they
        // identify with X-User-Id (TEST ONLY). Login itself is covered by AuthTest.
        "seatlock.auth.trust-user-header=true",
        "seatlock.auth.jwt-secret=test-secret-for-integration-tests",
        "seatlock.auth.admin-email=" + IntegrationTest.ADMIN_EMAIL,
        "seatlock.auth.admin-password=" + IntegrationTest.ADMIN_PASSWORD
})
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String ADMIN_EMAIL = "organizer@seatlock.test";
    public static final String ADMIN_PASSWORD = "organizer-password-1";

    /** Same signing secret in every test context, so one organizer token works everywhere. */
    private static volatile String adminToken;

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

    /** "Authorization: Bearer <organizer token>": needed to create events. */
    protected Map<String, String> admin() {
        if (adminToken == null) {
            ResponseEntity<JsonNode> login = post("/api/auth/login",
                    Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD), null, Map.of());
            if (login.getStatusCode().value() != 200) {
                throw new IllegalStateException("Organizer login failed: " + login);
            }
            adminToken = login.getBody().get("token").asString();
        }
        return bearer(adminToken);
    }

    protected static Map<String, String> bearer(String token) {
        return Map.of("Authorization", "Bearer " + token);
    }

    /** Creates an event with one section and returns its id. */
    protected long createEvent(String section, int rows, int seatsPerRow, long priceCents) {
        Map<String, Object> body = Map.of(
                "name", "Test Event",
                "venue", "Test Arena",
                "startsAt", Instant.now().plus(30, ChronoUnit.DAYS).toString(),
                "sections", List.of(Map.of(
                        "name", section, "rows", rows, "seatsPerRow", seatsPerRow, "priceCents", priceCents)));
        ResponseEntity<JsonNode> res = post("/api/events", body, null, admin());
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

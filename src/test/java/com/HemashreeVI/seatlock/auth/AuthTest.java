package com.HemashreeVI.seatlock.auth;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTest extends IntegrationTest {

    @Test
    void signUpLogInAndWhoAmI() {
        String email = unique("hema") + "@example.com";
        ResponseEntity<JsonNode> registered = register("Hema", email, "correct-horse-1");
        assertThat(registered.getStatusCode().value()).isEqualTo(201);
        String id = registered.getBody().get("user").get("id").asString();
        assertThat(id).startsWith("user-");
        assertThat(registered.getBody().get("user").get("roles").get(0).asString()).isEqualTo("USER");

        // email is case-insensitive
        ResponseEntity<JsonNode> login = login(email.toUpperCase(), "correct-horse-1");
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        JsonNode me = getWithToken("/api/auth/me", login.getBody().get("token").asString());
        assertThat(me.get("id").asString()).isEqualTo(id);
        assertThat(me.get("email").asString()).isEqualTo(email);
        assertThat(me.get("guest").asBoolean()).isFalse();
    }

    @Test
    void rejectsBadSignUpsAndBadLogins() {
        String email = unique("dup") + "@example.com";
        register("A", email, "long-enough-1");
        assertThat(register("B", email, "long-enough-2").getStatusCode().value()).isEqualTo(409);
        assertThat(register("C", unique("short") + "@example.com", "short").getStatusCode().value()).isEqualTo(400);
        assertThat(register("D", "not-an-email", "long-enough-1").getStatusCode().value()).isEqualTo(400);

        ResponseEntity<JsonNode> wrongPassword = login(email, "wrong-password");
        ResponseEntity<JsonNode> unknownEmail = login(unique("nobody") + "@example.com", "whatever-123");
        assertThat(wrongPassword.getStatusCode().value()).isEqualTo(401);
        assertThat(unknownEmail.getStatusCode().value()).isEqualTo(401);
        // same answer either way: the response doesn't reveal which emails have accounts
        assertThat(wrongPassword.getBody().get("detail").asString())
                .isEqualTo(unknownEmail.getBody().get("detail").asString());
    }

    @Test
    void bookingsBelongToTheLoggedInAccount() {
        String email = unique("owner") + "@example.com";
        String token = register("Owner", email, "owner-password-1").getBody().get("token").asString();
        String otherToken = register("Other", unique("other") + "@example.com", "other-password-1")
                .getBody().get("token").asString();
        long eventId = createEvent("A", 1, 2, 100);
        List<Long> seats = seatIds(eventId);

        ResponseEntity<JsonNode> held = post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats.subList(0, 1)),
                null, bearer(token));
        assertThat(held.getStatusCode().value()).isEqualTo(201);
        long bookingId = held.getBody().get("id").asLong();
        assertThat(held.getBody().get("userId").asString()).startsWith("user-");
        // account name and email are used for notifications without typing them again
        assertThat(held.getBody().get("customerEmail").asString()).isEqualTo(email);
        assertThat(held.getBody().get("customerName").asString()).isEqualTo("Owner");

        JsonNode mine = http.get().uri("/api/bookings").headers(h -> bearer(token).forEach(h::add))
                .retrieve().toEntity(JsonNode.class).getBody();
        assertThat(mine.valueStream().map(b -> b.get("id").asLong()).toList()).containsExactly(bookingId);

        assertThat(status("/api/bookings/" + bookingId, otherToken)).isEqualTo(404);   // not yours
        // even the test-only X-User-Id switch can't impersonate a real account
        String accountId = held.getBody().get("userId").asString();
        assertThat(get("/api/bookings/" + bookingId, accountId).getStatusCode().value()).isEqualTo(401);
        assertThat(status("/api/bookings/" + bookingId, null)).isEqualTo(401);         // not logged in
        assertThat(status("/api/bookings/" + bookingId, token + "x")).isEqualTo(401);  // tampered token
    }

    @Test
    void onlyOrganizersCreateEvents() {
        String token = register("Fan", unique("fan") + "@example.com", "fan-password-1").getBody().get("token").asString();
        Map<String, Object> event = Map.of("name", "Mine", "venue", "Here",
                "startsAt", Instant.now().plus(5, ChronoUnit.DAYS).toString(),
                "sections", List.of(Map.of("name", "A", "rows", 1, "seatsPerRow", 1, "priceCents", 100)));
        assertThat(post("/api/events", event, null, bearer(token)).getStatusCode().value()).isEqualTo(403);
        assertThat(post("/api/events", event, null, Map.of()).getStatusCode().value()).isEqualTo(401);
        assertThat(post("/api/events", event, null, admin()).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void guestTokensCanBookButHaveNoAccount() {
        JsonNode guest = post("/api/auth/guest", null, null, Map.of()).getBody();
        String token = guest.get("token").asString();
        assertThat(guest.get("user").get("guest").asBoolean()).isTrue();
        long eventId = createEvent("A", 1, 1, 100);

        ResponseEntity<JsonNode> held = post("/api/bookings", Map.of("eventId", eventId, "seatIds", seatIds(eventId)),
                null, bearer(token));
        assertThat(held.getStatusCode().value()).isEqualTo(201);
        assertThat(held.getBody().get("userId").asString()).startsWith("guest-");
        assertThat(held.getBody().get("customerEmail").isNull()).isTrue();
    }

    private ResponseEntity<JsonNode> register(String name, String email, String password) {
        return post("/api/auth/register", Map.of("name", name, "email", email, "password", password), null, Map.of());
    }

    private ResponseEntity<JsonNode> login(String email, String password) {
        return post("/api/auth/login", Map.of("email", email, "password", password), null, Map.of());
    }

    private JsonNode getWithToken(String path, String token) {
        return http.get().uri(path).headers(h -> bearer(token).forEach(h::add)).retrieve().toEntity(JsonNode.class).getBody();
    }

    private int status(String path, String token) {
        return http.get().uri(path).headers(h -> {
            if (token != null) {
                bearer(token).forEach(h::add);
            }
        }).retrieve().toEntity(JsonNode.class).getStatusCode().value();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

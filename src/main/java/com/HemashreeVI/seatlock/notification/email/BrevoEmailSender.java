package com.HemashreeVI.seatlock.notification.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends email through Brevo's transactional email API (free plan: 300 emails per day).
 * https://developers.brevo.com/reference/sendtransacemail
 */
@Slf4j
public class BrevoEmailSender implements EmailSender {

    static final String BASE_URL = "https://api.brevo.com";

    private final RestClient http;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailSender(RestClient.Builder builder, String apiKey, String fromEmail, String fromName) {
        this.http = builder.baseUrl(BASE_URL)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    @Override
    public void send(Email email) {
        Map<String, Object> recipient = email.toName() == null
                ? Map.of("email", email.to())
                : Map.of("email", email.to(), "name", email.toName());
        http.post().uri("/v3/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sender", Map.of("email", fromEmail, "name", fromName),
                        "to", List.of(recipient),
                        "subject", email.subject(),
                        "htmlContent", email.html()))
                .retrieve()
                .toBodilessEntity();   // throws on 4xx/5xx
        log.info("Email sent via Brevo to {}: {}", email.to(), email.subject());
    }
}

package com.HemashreeVI.seatlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under "seatlock.email". */
@ConfigurationProperties("seatlock.email")
public record EmailProperties(
        /** Brevo API key. Empty = emails are only logged (local runs, tests). */
        String brevoApiKey,
        /** Sender address; must be a sender verified in your Brevo account. */
        String fromEmail,
        @DefaultValue("SeatLock") String fromName,
        /** Public address of the app, used for the link in emails. */
        @DefaultValue("http://localhost:8080") String publicUrl,
        /** Currency used to format prices in emails (ISO 4217). */
        @DefaultValue("INR") String currency,
        /** Anti-abuse: at most this many emails per recipient address per 24 hours. */
        @DefaultValue("10") int maxPerRecipientPerDay
) {
    public boolean brevoConfigured() {
        return brevoApiKey != null && !brevoApiKey.isBlank() && fromEmail != null && !fromEmail.isBlank();
    }
}

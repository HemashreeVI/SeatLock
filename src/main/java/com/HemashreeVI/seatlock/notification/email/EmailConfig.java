package com.HemashreeVI.seatlock.notification.email;

import com.HemashreeVI.seatlock.config.EmailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Configuration
public class EmailConfig {

    @Bean
    EmailSender emailSender(EmailProperties properties) {
        if (properties.brevoConfigured()) {
            log.info("Email: sending through Brevo as {}", properties.fromEmail());
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
            factory.setReadTimeout(Duration.ofSeconds(15));
            return new BrevoEmailSender(RestClient.builder().requestFactory(factory),
                    properties.brevoApiKey(), properties.fromEmail(), properties.fromName());
        }
        if (properties.brevoApiKey() != null && !properties.brevoApiKey().isBlank()) {
            log.warn("Email: BREVO_API_KEY is set but EMAIL_FROM is empty; emails will only be logged");
        } else {
            log.info("Email: no provider configured (set BREVO_API_KEY and EMAIL_FROM); emails will only be logged");
        }
        return new LoggingEmailSender();
    }
}

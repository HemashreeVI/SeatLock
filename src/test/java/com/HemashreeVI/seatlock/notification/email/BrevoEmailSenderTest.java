package com.HemashreeVI.seatlock.notification.email;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Checks the exact request sent to Brevo's transactional email API. */
class BrevoEmailSenderTest {

    @Test
    void sendsTransactionalEmailRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer brevo = MockRestServiceServer.bindTo(builder).build();
        BrevoEmailSender sender = new BrevoEmailSender(builder, "xkeysib-test", "tickets@example.com", "SeatLock");

        brevo.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "xkeysib-test"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.email").value("tickets@example.com"))
                .andExpect(jsonPath("$.sender.name").value("SeatLock"))
                .andExpect(jsonPath("$.to[0].email").value("hema@example.com"))
                .andExpect(jsonPath("$.to[0].name").value("Hema"))
                .andExpect(jsonPath("$.subject").value("Booking #1 confirmed"))
                .andExpect(jsonPath("$.htmlContent").value("<p>hi</p>"))
                .andRespond(withSuccess("{\"messageId\":\"<abc@brevo>\"}", MediaType.APPLICATION_JSON));

        sender.send(new Email("hema@example.com", "Hema", "Booking #1 confirmed", "<p>hi</p>"));
        brevo.verify();
    }

    @Test
    void providerErrorsAreThrown() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer brevo = MockRestServiceServer.bindTo(builder).build();
        BrevoEmailSender sender = new BrevoEmailSender(builder, "bad-key", "tickets@example.com", "SeatLock");
        brevo.expect(requestTo("https://api.brevo.com/v3/smtp/email")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> sender.send(new Email("x@example.com", null, "s", "b")))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }
}

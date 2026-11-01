package com.HemashreeVI.seatlock.payment;

import com.HemashreeVI.seatlock.booking.HoldRequest;
import com.HemashreeVI.seatlock.booking.BookingService;
import com.HemashreeVI.seatlock.support.IntegrationTest;
import com.HemashreeVI.seatlock.support.Race;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentIdempotencyConcurrencyTest extends IntegrationTest {

    @Autowired
    BookingService bookingService;

    @Autowired
    PaymentService paymentService;

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    void concurrentCopiesOfOnePaymentRequestAllGetTheSamePayment() throws Exception {
        long eventId = createEvent("VIP", 1, 1, 1000);
        long bookingId = bookingService.hold("tina", new HoldRequest(eventId, seatIds(eventId)), null).booking().id();
        Set<Long> paymentIds = ConcurrentHashMap.newKeySet();

        Race.Outcome outcome = Race.race(20, i -> paymentIds.add(
                paymentService.requestPayment("tina", bookingId, "double-submit", new PayRequest("tok_visa")).payment().id()));

        assertThat(outcome.unexpected()).as("no 409 for copies of the same request").isEmpty();
        assertThat(paymentIds).hasSize(1);
        assertThat(paymentRepository.countByBookingId(bookingId)).isEqualTo(1);
    }
}

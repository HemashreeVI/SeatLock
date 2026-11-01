package com.HemashreeVI.seatlock.payment;

import com.HemashreeVI.seatlock.config.PaymentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Safety net for payments that never got a result (provider outage longer than the Kafka
 * retries, message parked on the dead-letter topic, crash, ...). Without it such a booking would
 * stay PAYMENT_PENDING forever and its seats would never return to sale.
 * <p>
 * Re-driving is safe: the gateway is called with the payment's original idempotency key, so a
 * charge that already happened is returned, not repeated. If the provider is still unreachable
 * after {@code give-up-after}, the payment is failed and the saga releases the seats.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciler {

    private static final String TIMED_OUT = "Payment timed out";

    private final PaymentRepository paymentRepository;
    private final PaymentProcessor processor;
    private final PaymentProperties properties;

    /** Returns how many stale payments were looked at. */
    public int reconcile() {
        Instant now = Instant.now();
        List<Payment> stale = paymentRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAt(
                PaymentStatus.PENDING, now.minus(properties.pendingTimeout()));
        for (Payment payment : stale) {
            try {
                processor.process(payment.getId());
                log.info("Reconciled stuck payment {}", payment.getId());
            } catch (RuntimeException e) {
                if (payment.getCreatedAt().isBefore(now.minus(properties.giveUpAfter()))) {
                    log.error("Payment {} still unresolved after {}: failing it and releasing the seats. "
                            + "Check with the provider whether a refund is needed.", payment.getId(), properties.giveUpAfter());
                    processor.record(payment.getId(), false, null, TIMED_OUT);
                } else {
                    log.warn("Payment {} still cannot be processed, will retry: {}", payment.getId(), e.getMessage());
                }
            }
        }
        return stale.size();
    }
}

package com.HemashreeVI.seatlock.payment;

import com.HemashreeVI.seatlock.messaging.Messages;
import com.HemashreeVI.seatlock.messaging.Outbox;
import com.HemashreeVI.seatlock.messaging.Topics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saga step 2 (payment side): charge the customer and report the result.
 * <p>
 * The gateway call happens OUTSIDE any database transaction (no DB locks held while waiting on
 * a slow provider). Duplicate deliveries are harmless: the payment's status tells us whether it
 * was already handled, and the gateway itself de-duplicates on the idempotency key.
 * <p>
 * If the gateway keeps failing, Kafka retries a few times and then parks the message on the
 * dead-letter topic; {@link PaymentReconciler} later re-drives the still-PENDING payment.
 */
@Slf4j
@Component
public class PaymentProcessor {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final Outbox outbox;
    private final JsonMapper json;
    private final TransactionTemplate tx;

    public PaymentProcessor(PaymentRepository paymentRepository, PaymentGateway gateway, Outbox outbox,
                            JsonMapper json, PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.outbox = outbox;
        this.json = json;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @KafkaListener(topics = Topics.PAYMENT_REQUESTED, groupId = "seatlock-payments")
    public void onPaymentRequested(String payload) {
        Messages.PaymentRequested request = json.readValue(payload, Messages.PaymentRequested.class);
        process(request.paymentId());
    }

    /**
     * Charges a PENDING payment and records the result. Safe to call repeatedly and concurrently.
     * Throws if the gateway is unreachable (the caller decides whether to retry).
     */
    public void process(long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already processed, skipping", paymentId);
            return;
        }
        PaymentGateway.ChargeResult result =
                gateway.charge(payment.getIdempotencyKey(), payment.getAmountCents(), payment.getPaymentToken());
        record(paymentId, result.success(), result.providerRef(), result.failureReason());
    }

    /** Records the outcome if the payment is still PENDING and tells the booking side via the outbox. */
    public void record(long paymentId, boolean success, String providerRef, String failureReason) {
        tx.executeWithoutResult(s -> {
            Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
            if (payment.getStatus() != PaymentStatus.PENDING) {
                return; // someone else (redelivery, reconciler) finished first
            }
            if (success) {
                payment.setStatus(PaymentStatus.SUCCEEDED);
                payment.setProviderRef(providerRef);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(failureReason);
            }
            outbox.add("payment", payment.getBookingId(), "PaymentCompleted", Topics.PAYMENT_COMPLETED,
                    new Messages.PaymentCompleted(payment.getId(), payment.getBookingId(), success, failureReason));
        });
    }
}

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

        boolean pending = paymentRepository.findById(request.paymentId())
                .map(p -> p.getStatus() == PaymentStatus.PENDING).orElse(false);
        if (!pending) {
            log.info("Payment {} already processed, skipping redelivery", request.paymentId());
            return;
        }

        PaymentGateway.ChargeResult result =
                gateway.charge(request.idempotencyKey(), request.amountCents(), request.paymentToken());

        tx.executeWithoutResult(s -> {
            Payment payment = paymentRepository.findByIdForUpdate(request.paymentId()).orElseThrow();
            if (payment.getStatus() != PaymentStatus.PENDING) {
                return; // a concurrent redelivery finished first
            }
            if (result.success()) {
                payment.setStatus(PaymentStatus.SUCCEEDED);
                payment.setProviderRef(result.providerRef());
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(result.failureReason());
            }
            outbox.add("payment", payment.getBookingId(), "PaymentCompleted", Topics.PAYMENT_COMPLETED,
                    new Messages.PaymentCompleted(payment.getId(), payment.getBookingId(), result.success(),
                            result.failureReason()));
        });
    }
}

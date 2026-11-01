package com.HemashreeVI.seatlock.payment;

import com.HemashreeVI.seatlock.config.PaymentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated provider. Token "tok_fail" (or anything starting with "tok_fail") is declined;
 * every other token succeeds. Like a real provider, it remembers idempotency keys and returns
 * the original result for a repeated key instead of charging again.
 * <p>
 * {@link #setOutage(boolean)} simulates the provider being unreachable (calls throw), which is
 * what the payment reconciliation job exists for.
 */
@Slf4j
@Component
public class FakePaymentGateway implements PaymentGateway {

    private final Map<String, ChargeResult> processed = new ConcurrentHashMap<>();
    private final long latencyMs;
    private volatile boolean outage;

    public FakePaymentGateway(PaymentProperties properties) {
        this.latencyMs = properties.fakeLatencyMs();
    }

    public void setOutage(boolean outage) {
        this.outage = outage;
    }

    @Override
    public ChargeResult charge(String idempotencyKey, long amountCents, String paymentToken) {
        if (outage) {
            throw new IllegalStateException("Payment provider unavailable");
        }
        return processed.computeIfAbsent(idempotencyKey, key -> {
            simulateLatency();
            if (paymentToken != null && paymentToken.startsWith("tok_fail")) {
                log.info("Fake gateway DECLINED {} cents (key {})", amountCents, key);
                return new ChargeResult(false, null, "Card declined");
            }
            log.info("Fake gateway charged {} cents (key {})", amountCents, key);
            return new ChargeResult(true, "fake_" + UUID.randomUUID(), null);
        });
    }

    private void simulateLatency() {
        if (latencyMs > 0) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

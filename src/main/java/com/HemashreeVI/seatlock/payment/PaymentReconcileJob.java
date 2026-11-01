package com.HemashreeVI.seatlock.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileJob {

    private final PaymentReconciler reconciler;

    @Scheduled(fixedDelayString = "${seatlock.payment.reconcile-interval-ms:30000}")
    public void run() {
        try {
            reconciler.reconcile();
        } catch (RuntimeException e) {
            log.warn("Payment reconciliation run failed, will retry", e);
        }
    }
}

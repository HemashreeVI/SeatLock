package com.HemashreeVI.seatlock.payment;

import com.HemashreeVI.seatlock.booking.Booking;
import com.HemashreeVI.seatlock.booking.BookingRepository;
import com.HemashreeVI.seatlock.booking.BookingService;
import com.HemashreeVI.seatlock.booking.BookingStatus;
import com.HemashreeVI.seatlock.common.BadRequestException;
import com.HemashreeVI.seatlock.common.ConflictException;
import com.HemashreeVI.seatlock.common.Exceptions;
import com.HemashreeVI.seatlock.common.NotFoundException;
import com.HemashreeVI.seatlock.common.UnprocessableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Idempotent payments: the client sends an Idempotency-Key with every payment attempt.
 * Retrying with the same key (network timeout, double click) returns the original result and
 * never charges twice; the unique constraint on payments.idempotency_key enforces it.
 */
@Service
public class PaymentService {

    private static final String UQ_IDEMPOTENCY = "uq_payments_idempotency";

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentGateway gateway;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository paymentRepository, BookingRepository bookingRepository,
                          BookingService bookingService, PaymentGateway gateway,
                          PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.gateway = gateway;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public PaymentResult pay(String userId, Long bookingId, String idempotencyKey, PayRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required for payments");
        }
        String hash = requestHash(userId, bookingId, request.paymentToken());

        Optional<PaymentResult> previous = previous(idempotencyKey, hash);
        if (previous.isPresent()) {
            return previous.get();
        }
        try {
            return new PaymentResult(tx.execute(s -> doPay(userId, bookingId, idempotencyKey, hash, request)), false);
        } catch (DataIntegrityViolationException e) {
            if (Exceptions.violates(e, UQ_IDEMPOTENCY)) {
                // a concurrent retry with the same key won the race: return its result
                return previous(idempotencyKey, hash).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    private Optional<PaymentResult> previous(String idempotencyKey, String hash) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey).map(p -> {
            if (!p.getRequestHash().equals(hash)) {
                throw new UnprocessableException("Idempotency-Key was already used for a different payment request");
            }
            return new PaymentResult(PaymentResponse.from(p), true);
        });
    }

    private PaymentResponse doPay(String userId, Long bookingId, String key, String hash, PayRequest request) {
        // row lock: the expiry sweeper and concurrent payments for this booking wait (or skip) until we commit
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .filter(b -> b.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("Booking " + bookingId + " not found"));
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new ConflictException("Booking is " + booking.getStatus() + ", it can no longer be paid");
        }
        if (booking.getExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException("The hold on this booking has expired");
        }

        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setIdempotencyKey(key);
        payment.setRequestHash(hash);
        payment.setAmountCents(booking.getTotalCents());
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.saveAndFlush(payment);

        // NOTE: calling an external service inside a DB transaction is a known trade-off;
        // Stage 3 replaces this with an asynchronous saga over Kafka.
        PaymentGateway.ChargeResult result = gateway.charge(key, booking.getTotalCents(), request.paymentToken());
        if (result.success()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setProviderRef(result.providerRef());
            bookingService.confirm(booking);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.failureReason());
            bookingService.release(booking, BookingStatus.CANCELLED);   // compensation: give the seats back
        }
        return PaymentResponse.from(payment);
    }

    static String requestHash(String userId, Long bookingId, String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((userId + "|" + bookingId + "|" + token).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

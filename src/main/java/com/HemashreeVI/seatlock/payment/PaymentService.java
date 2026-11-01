package com.HemashreeVI.seatlock.payment;

import com.HemashreeVI.seatlock.booking.Booking;
import com.HemashreeVI.seatlock.booking.BookingRepository;
import com.HemashreeVI.seatlock.booking.BookingStatus;
import com.HemashreeVI.seatlock.common.BadRequestException;
import com.HemashreeVI.seatlock.common.ConflictException;
import com.HemashreeVI.seatlock.common.Exceptions;
import com.HemashreeVI.seatlock.common.NotFoundException;
import com.HemashreeVI.seatlock.common.UnprocessableException;
import com.HemashreeVI.seatlock.messaging.Messages;
import com.HemashreeVI.seatlock.messaging.Outbox;
import com.HemashreeVI.seatlock.messaging.Topics;
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
 * Saga step 1: accept a payment request.
 * <p>
 * In ONE database transaction we: lock the booking, record a PENDING payment, move the booking to
 * PAYMENT_PENDING and write a PaymentRequested message to the outbox. The HTTP call returns
 * immediately (202 Accepted); the actual charge happens asynchronously.
 * <p>
 * Idempotency: the client sends an Idempotency-Key. Retrying with the same key returns the
 * same payment and never charges twice (unique constraint on payments.idempotency_key).
 */
@Service
public class PaymentService {

    private static final String UQ_IDEMPOTENCY = "uq_payments_idempotency";

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final Outbox outbox;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository paymentRepository, BookingRepository bookingRepository,
                          Outbox outbox, PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public PaymentResult requestPayment(String userId, Long bookingId, String idempotencyKey, PayRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required for payments");
        }
        String hash = requestHash(userId, bookingId, request.paymentToken());

        Optional<PaymentResult> previous = previous(idempotencyKey, hash);
        if (previous.isPresent()) {
            return previous.get();
        }
        try {
            return new PaymentResult(tx.execute(s -> doRequest(userId, bookingId, idempotencyKey, hash, request)), false);
        } catch (DataIntegrityViolationException e) {
            if (Exceptions.violates(e, UQ_IDEMPOTENCY)) {
                // a concurrent retry with the same key won the race: return its result
                return previous(idempotencyKey, hash).orElseThrow(() -> e);
            }
            throw e;
        } catch (ConflictException e) {
            // A concurrent copy of THIS request (double click, overlapping retry) waited on the
            // booking row lock behind the original and now sees PAYMENT_PENDING. If the original
            // recorded a payment under the same key, answer with it instead of a 409.
            return previous(idempotencyKey, hash).orElseThrow(() -> e);
        }
    }

    public PaymentResponse get(String userId, Long paymentId) {
        return tx.execute(s -> paymentRepository.findById(paymentId)
                .filter(p -> bookingRepository.findById(p.getBookingId()).map(b -> b.isOwnedBy(userId)).orElse(false))
                .map(PaymentResponse::from)
                .orElseThrow(() -> new NotFoundException("Payment " + paymentId + " not found")));
    }

    private Optional<PaymentResult> previous(String idempotencyKey, String hash) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey).map(p -> {
            if (!p.getRequestHash().equals(hash)) {
                throw new UnprocessableException("Idempotency-Key was already used for a different payment request");
            }
            return new PaymentResult(PaymentResponse.from(p), true);
        });
    }

    private PaymentResponse doRequest(String userId, Long bookingId, String key, String hash, PayRequest request) {
        // row lock: the expiry sweeper (SKIP LOCKED) and concurrent payments wait or skip until we commit
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .filter(b -> b.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("Booking " + bookingId + " not found"));
        switch (booking.getStatus()) {
            case HELD -> { /* ok */ }
            case PAYMENT_PENDING -> throw new ConflictException("A payment for this booking is already in progress");
            case CONFIRMED -> throw new ConflictException("This booking is already paid");
            default -> throw new ConflictException("Booking is " + booking.getStatus() + ", it can no longer be paid");
        }
        if (booking.getExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException("The hold on this booking has expired");
        }

        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setIdempotencyKey(key);
        payment.setRequestHash(hash);
        payment.setAmountCents(booking.getTotalCents());
        payment.setPaymentToken(request.paymentToken());
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.saveAndFlush(payment);

        booking.setStatus(BookingStatus.PAYMENT_PENDING);
        outbox.add("payment", bookingId, "PaymentRequested", Topics.PAYMENT_REQUESTED,
                new Messages.PaymentRequested(payment.getId(), bookingId, payment.getAmountCents(),
                        request.paymentToken(), key));
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

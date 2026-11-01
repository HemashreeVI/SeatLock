package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.booking.locking.LockingStrategyType;
import com.HemashreeVI.seatlock.booking.locking.SeatLockStrategy;
import com.HemashreeVI.seatlock.common.BadRequestException;
import com.HemashreeVI.seatlock.common.ConflictException;
import com.HemashreeVI.seatlock.common.Exceptions;
import com.HemashreeVI.seatlock.common.NotFoundException;
import com.HemashreeVI.seatlock.common.UnprocessableException;
import com.HemashreeVI.seatlock.config.BookingProperties;
import com.HemashreeVI.seatlock.config.LockingProperties;
import com.HemashreeVI.seatlock.event.Event;
import com.HemashreeVI.seatlock.event.EventRepository;
import com.HemashreeVI.seatlock.messaging.Messages;
import com.HemashreeVI.seatlock.messaging.Outbox;
import com.HemashreeVI.seatlock.messaging.Topics;
import com.HemashreeVI.seatlock.seat.Seat;
import com.HemashreeVI.seatlock.seat.SeatsChangedEvent;
import com.HemashreeVI.seatlock.seat.SeatStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Seat holds: the heart of SeatLock.
 * <pre>
 * hold()    AVAILABLE -> HELD     (booking HELD, expires after hold-ttl)
 * confirm() HELD      -> BOOKED   (booking CONFIRMED, after successful payment)
 * release() HELD      -> AVAILABLE (booking CANCELLED / EXPIRED)
 * </pre>
 */
@Slf4j
@Service
public class BookingService {

    private static final String UQ_ONE_ACTIVE = "uq_booking_seats_one_active";
    private static final String UQ_IDEMPOTENCY = "uq_bookings_user_idempotency";

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final EventRepository eventRepository;
    private final BookingProperties properties;
    private final TransactionTemplate tx;
    private final TransactionTemplate readOnlyTx;
    private final Map<LockingStrategyType, SeatLockStrategy> strategies = new EnumMap<>(LockingStrategyType.class);
    private final SeatLockStrategy defaultStrategy;
    private final Outbox outbox;
    private final ApplicationEventPublisher events;

    public BookingService(BookingRepository bookingRepository,
                          BookingSeatRepository bookingSeatRepository,
                          EventRepository eventRepository,
                          BookingProperties properties,
                          LockingProperties lockingProperties,
                          List<SeatLockStrategy> strategyBeans,
                          PlatformTransactionManager transactionManager,
                          Outbox outbox,
                          ApplicationEventPublisher events) {
        this.outbox = outbox;
        this.events = events;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.eventRepository = eventRepository;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        strategyBeans.forEach(s -> strategies.put(s.type(), s));
        this.defaultStrategy = strategy(lockingProperties.strategy());
        log.info("Seat locking strategy: {}", defaultStrategy.type());
    }

    public SeatLockStrategy strategy(LockingStrategyType type) {
        SeatLockStrategy s = strategies.get(type);
        if (s == null) {
            throw new IllegalStateException("No SeatLockStrategy bean for " + type);
        }
        return s;
    }

    public LockingStrategyType defaultStrategyType() {
        return defaultStrategy.type();
    }

    // ------------------------------------------------------------------ hold

    public HoldResult hold(String userId, HoldRequest request, String idempotencyKey) {
        return hold(userId, request, idempotencyKey, defaultStrategy);
    }

    public HoldResult hold(String userId, HoldRequest request, String idempotencyKey, SeatLockStrategy strategy) {
        List<Long> seatIds = validate(request);

        if (idempotencyKey != null) {
            Optional<BookingResponse> previous = findPrevious(userId, idempotencyKey, request.eventId(), seatIds);
            if (previous.isPresent()) {
                return new HoldResult(previous.get(), true);
            }
        }

        try {
            return new HoldResult(attemptHold(userId, request.eventId(), seatIds, idempotencyKey, strategy), false);
        } catch (SeatUnavailableException e) {
            if (idempotencyKey != null) {
                // the "competitor" may be a concurrent copy of this very request (double click,
                // client retry): if that copy won, answer with its booking instead of a 409
                Optional<BookingResponse> previous = findPrevious(userId, idempotencyKey, request.eventId(), seatIds);
                if (previous.isPresent()) {
                    return new HoldResult(previous.get(), true);
                }
            }
            throw e;
        }
    }

    private BookingResponse attemptHold(String userId, Long eventId, List<Long> seatIds, String idempotencyKey,
                                        SeatLockStrategy strategy) {
        try {
            return strategy.withSeatsLocked(seatIds,
                    () -> tx.execute(status -> doHold(userId, eventId, seatIds, idempotencyKey, strategy)));
        } catch (ConcurrencyFailureException e) {
            // optimistic version conflict, lock timeout or deadlock: someone else got there first
            throw new SeatUnavailableException(seatIds, "Seat was taken by a concurrent booking");
        } catch (DataIntegrityViolationException e) {
            if (Exceptions.violates(e, UQ_ONE_ACTIVE)
                    || (idempotencyKey != null && Exceptions.violates(e, UQ_IDEMPOTENCY))) {
                throw new SeatUnavailableException(seatIds, "Seat was taken by a concurrent booking");
            }
            throw e;
        }
    }

    private List<Long> validate(HoldRequest request) {
        List<Long> seatIds = request.seatIds().stream().distinct().sorted().toList();
        if (seatIds.size() != request.seatIds().size()) {
            throw new BadRequestException("seatIds contains duplicates");
        }
        if (seatIds.size() > properties.maxSeatsPerBooking()) {
            throw new BadRequestException("At most " + properties.maxSeatsPerBooking() + " seats per booking");
        }
        return seatIds;
    }

    private BookingResponse doHold(String userId, Long eventId, List<Long> seatIds, String idempotencyKey,
                                   SeatLockStrategy strategy) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event " + eventId + " not found"));

        List<Seat> seats = strategy.loadSeats(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new NotFoundException("One or more seats do not exist");
        }
        for (Seat seat : seats) {
            if (!seat.getEvent().getId().equals(eventId)) {
                throw new BadRequestException("Seat " + seat.getId() + " does not belong to event " + eventId);
            }
        }
        List<Long> taken = seats.stream().filter(s -> !s.isAvailable()).map(Seat::getId).toList();
        if (!taken.isEmpty()) {
            throw new SeatUnavailableException(taken, "Seat(s) already taken");
        }

        Instant expiresAt = Instant.now().plus(properties.holdTtl());
        Booking booking = new Booking();
        booking.setEvent(event);
        booking.setUserId(userId);
        booking.setStatus(BookingStatus.HELD);
        booking.setTotalCents(seats.stream().mapToLong(Seat::getPriceCents).sum());
        booking.setExpiresAt(expiresAt);
        booking.setIdempotencyKey(idempotencyKey);
        bookingRepository.save(booking);

        for (Seat seat : seats) {
            seat.hold(booking.getId(), expiresAt);
            bookingSeatRepository.save(new BookingSeat(booking, seat));
        }
        // flush now so version conflicts / unique violations surface here, not at some later point
        bookingRepository.flush();
        events.publishEvent(new SeatsChangedEvent(eventId));
        return BookingResponse.of(booking, seats);
    }

    private Optional<BookingResponse> findPrevious(String userId, String key, Long eventId, List<Long> seatIds) {
        return readOnlyTx.execute(status -> bookingRepository.findByUserIdAndIdempotencyKey(userId, key)
                .map(previous -> {
                    List<Seat> seats = seatsOf(previous.getId());
                    Set<Long> previousSeatIds = new HashSet<>(seats.stream().map(Seat::getId).toList());
                    if (!previous.getEvent().getId().equals(eventId) || !previousSeatIds.equals(new HashSet<>(seatIds))) {
                        throw new UnprocessableException(
                                "Idempotency-Key was already used for a different booking request");
                    }
                    return BookingResponse.of(previous, seats);
                }));
    }

    // ------------------------------------------------------------------ read / cancel

    public BookingResponse get(String userId, Long bookingId) {
        return readOnlyTx.execute(status -> {
            Booking booking = ownedBooking(userId, bookingRepository.findById(bookingId), bookingId);
            return BookingResponse.of(booking, seatsOf(bookingId));
        });
    }

    public BookingResponse cancel(String userId, Long bookingId) {
        return tx.execute(status -> {
            Booking booking = ownedBooking(userId, bookingRepository.findByIdForUpdate(bookingId), bookingId);
            if (booking.getStatus() != BookingStatus.HELD) {
                throw new ConflictException("Only HELD bookings can be cancelled (booking is " + booking.getStatus() + ")");
            }
            release(booking, BookingStatus.CANCELLED);
            return BookingResponse.of(booking, seatsOf(bookingId));
        });
    }

    private static Booking ownedBooking(String userId, Optional<Booking> booking, Long bookingId) {
        // other users' bookings are reported as "not found" so ids can't be probed
        return booking.filter(b -> b.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("Booking " + bookingId + " not found"));
    }

    // ------------------------------------------------------------------ state transitions (call inside a transaction)

    /** HELD/PAYMENT_PENDING -> CONFIRMED, seats -> BOOKED. Caller must hold the booking row lock. */
    public void confirm(Booking booking) {
        List<BookingSeat> items = bookingSeatRepository.findByBookingIdWithSeats(booking.getId());
        for (BookingSeat item : items) {
            item.getSeat().book();
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        publish(booking, "BookingConfirmed", items);
        log.info("Booking {} confirmed", booking.getId());
    }

    /** HELD/PAYMENT_PENDING -> CANCELLED/EXPIRED, seats -> AVAILABLE. Caller must hold the booking row lock. */
    public void release(Booking booking, BookingStatus newStatus) {
        List<BookingSeat> items = bookingSeatRepository.findByBookingIdWithSeats(booking.getId());
        for (BookingSeat item : items) {
            if (!item.isActive()) {
                continue;
            }
            item.setActive(false);
            Seat seat = item.getSeat();
            if (booking.getId().equals(seat.getBookingId()) && seat.getStatus() == SeatStatus.HELD) {
                seat.release();
            }
        }
        booking.setStatus(newStatus);
        publish(booking, newStatus == BookingStatus.EXPIRED ? "BookingExpired" : "BookingCancelled", items);
        log.info("Booking {} {}", booking.getId(), newStatus);
    }

    /** Written to the outbox in the same transaction as the state change. */
    private void publish(Booking booking, String type, List<BookingSeat> items) {
        events.publishEvent(new SeatsChangedEvent(booking.getEvent().getId()));
        outbox.add("booking", booking.getId(), type, Topics.BOOKING_EVENTS, new Messages.BookingEvent(
                type, booking.getId(), booking.getEvent().getId(), booking.getUserId(), booking.getStatus().name(),
                booking.getTotalCents(), items.stream().map(i -> i.getSeat().getId()).toList(), Instant.now()));
    }

    public List<Seat> seatsOf(Long bookingId) {
        return bookingSeatRepository.findByBookingIdWithSeats(bookingId).stream().map(BookingSeat::getSeat).toList();
    }
}

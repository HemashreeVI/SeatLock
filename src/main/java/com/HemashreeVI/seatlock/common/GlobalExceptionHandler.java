package com.HemashreeVI.seatlock.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns domain exceptions into RFC 9457 "problem details" JSON responses.
 * Framework errors (validation, missing headers, ...) are rendered by Spring's
 * built-in problem-details support (spring.mvc.problemdetails.enabled=true).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleDomainConflict(ConflictException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
        ex.getProperties().forEach(pd::setProperty);
        return pd;
    }

    @ExceptionHandler(UnprocessableException.class)
    public ProblemDetail handleUnprocessable(UnprocessableException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Unprocessable request", ex.getMessage());
    }

    @ExceptionHandler(com.HemashreeVI.seatlock.queue.QueueAdmissionRequiredException.class)
    public ProblemDetail handleQueue(com.HemashreeVI.seatlock.queue.QueueAdmissionRequiredException ex) {
        return problem(HttpStatus.FORBIDDEN, "Waiting room admission required", ex.getMessage());
    }

    @ExceptionHandler(com.HemashreeVI.seatlock.seat.SeatLiveUpdates.TooManySubscribersException.class)
    public ProblemDetail handleTooManyViewers(RuntimeException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Too many live viewers", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleConflict(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "Request conflicts with existing data (for example a duplicate section name)");
    }

    protected static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}

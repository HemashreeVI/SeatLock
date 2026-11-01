package com.HemashreeVI.seatlock.common;

/** The request is syntactically valid but makes no sense (HTTP 400). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}

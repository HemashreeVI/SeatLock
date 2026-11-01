package com.HemashreeVI.seatlock.common;

import java.util.Map;

/** The request clashes with the current state of the system (HTTP 409). */
public class ConflictException extends RuntimeException {

    private final Map<String, Object> properties;

    public ConflictException(String message) {
        this(message, Map.of());
    }

    public ConflictException(String message, Map<String, Object> properties) {
        super(message);
        this.properties = properties;
    }

    /** Extra fields added to the problem-details JSON body. */
    public Map<String, Object> getProperties() {
        return properties;
    }
}

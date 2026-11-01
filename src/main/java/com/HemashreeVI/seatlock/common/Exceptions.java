package com.HemashreeVI.seatlock.common;

import org.springframework.core.NestedExceptionUtils;

public final class Exceptions {

    private Exceptions() {
    }

    /** True if the root cause message mentions the given database constraint name. */
    public static boolean violates(Throwable e, String constraintName) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        String message = root.getMessage();
        return message != null && message.contains(constraintName);
    }
}

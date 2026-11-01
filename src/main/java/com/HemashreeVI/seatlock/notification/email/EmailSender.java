package com.HemashreeVI.seatlock.notification.email;

/** Delivers one email; throws if the provider rejects it or can't be reached. */
public interface EmailSender {

    void send(Email email);

    /** False for senders that don't actually deliver (logging only). */
    default boolean delivers() {
        return true;
    }
}

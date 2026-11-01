package com.HemashreeVI.seatlock.notification.email;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Used when no email provider is configured (local runs, tests): logs the email instead of
 * sending it, and keeps the last 100 in memory so tests can inspect them.
 */
@Slf4j
public class LoggingEmailSender implements EmailSender {

    private static final int KEEP = 100;

    private final List<Email> sent = new ArrayList<>();

    @Override
    public synchronized void send(Email email) {
        log.info("[email not sent: no provider configured] to={} subject=\"{}\"", email.to(), email.subject());
        sent.add(email);
        if (sent.size() > KEEP) {
            sent.removeFirst();
        }
    }

    @Override
    public boolean delivers() {
        return false;
    }

    public synchronized List<Email> sent() {
        return List.copyOf(sent);
    }
}

package com.HemashreeVI.seatlock.notification;

public enum EmailStatus {
    /** The customer gave no email address. */
    NONE,
    /** Will be sent right after the notification is committed. */
    PENDING,
    SENT,
    /** No email provider configured: the email was written to the log instead of being sent. */
    LOGGED,
    FAILED,
    /** Not sent: this address already got the daily maximum (anti-abuse for the public demo). */
    SKIPPED
}

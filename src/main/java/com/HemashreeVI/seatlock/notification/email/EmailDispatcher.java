package com.HemashreeVI.seatlock.notification.email;

import com.HemashreeVI.seatlock.notification.EmailStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends a notification's email only AFTER the notification has been committed, so a rolled-back
 * or redelivered message never sends an email for a notification that doesn't exist. The result
 * (SENT / FAILED) is recorded on the notification. A provider failure doesn't undo the in-app
 * notification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatcher {

    private final EmailSender sender;
    private final JdbcTemplate jdbc;

    /** Published by the notification consumer inside its transaction. */
    public record EmailRequested(long notificationId, Email email) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailRequested(EmailRequested request) {
        EmailStatus status;
        try {
            sender.send(request.email());
            status = sender.delivers() ? EmailStatus.SENT : EmailStatus.LOGGED;
        } catch (RuntimeException e) {
            log.warn("Email for notification {} to {} failed: {}", request.notificationId(), request.email().to(), e.getMessage());
            status = EmailStatus.FAILED;
        }
        jdbc.update("UPDATE notifications SET email_status = ? WHERE id = ?", status.name(), request.notificationId());
    }
}

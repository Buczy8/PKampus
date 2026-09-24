package pl.edu.pk.pkampus.mail;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.edu.pk.pkampus.common.exception.MailDeliveryException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

/**
 * Best-effort resident notifications and critical authentication emails.
 * All public send methods are asynchronous (dispatched onto {@code mailExecutor}).
 *
 * <p>Critical emails (verification, password reset) return a {@link CompletableFuture}
 * for programmatic observation and are additionally escalated by this service:
 * a failed critical delivery is logged as CRITICAL, because the resident has no
 * recovery path until the mail arrives (there is no resend flow in the MVP).</p>
 *
 * <p>Fire-and-forget notification exceptions are routed to {@code AsyncUncaughtExceptionHandler}.
 * Delivery outcomes are tracked with {@code mail.sent} / {@code mail.failed} counters
 * tagged with the per-template {@link MailType} tag (never PII).</p>
 *
 * <p>Callers inside a transaction must not send directly — they publish a
 * domain event instead and a {@code @TransactionalEventListener(AFTER_COMMIT)}
 * listener sends the mail, so residents never receive mail about changes that
 * were rolled back.</p>
 */
@Slf4j
@Service
public class EmailService {

    private static final String MAIL_EXECUTOR = "mailExecutor";

    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer templateRenderer;
    private final MeterRegistry meterRegistry;
    private final String frontendUrl;
    private final String fromEmail;

    public EmailService(
            JavaMailSender mailSender,
            EmailTemplateRenderer templateRenderer,
            MeterRegistry meterRegistry,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl,
            @Value("${app.mail.from:noreply@pkampus.pk.edu.pl}") String fromEmail) {
        this.mailSender = mailSender;
        this.templateRenderer = templateRenderer;
        this.meterRegistry = meterRegistry;
        // normalize once so that both concatenated and builder-based template
        // links stay single-slash even when the configured URL ends with a slash
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.fromEmail = fromEmail;
    }

    @Async(MAIL_EXECUTOR)
    public CompletableFuture<Void> sendVerificationEmail(String toEmail, String token) {
        try {
            dispatch(toEmail, MailType.VERIFICATION, templateRenderer.renderVerificationEmail(frontendUrl, token));
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async(MAIL_EXECUTOR)
    public void sendAccountActivatedEmail(String toEmail, String firstName, String roomNumber, String dormitoryName) {
        dispatch(toEmail, MailType.ACCOUNT_ACTIVATED,
                templateRenderer.renderAccountActivatedEmail(frontendUrl, firstName, roomNumber, dormitoryName));
    }

    @Async(MAIL_EXECUTOR)
    public void sendRegistrationRejectedEmail(String toEmail, String firstName, String reason) {
        dispatch(toEmail, MailType.REGISTRATION_REJECTED,
                templateRenderer.renderRegistrationRejectedEmail(firstName, reason));
    }

    @Async(MAIL_EXECUTOR)
    public void sendLaundryMachineBreakdownEmail(
            String toEmail,
            String firstName,
            String machineIdentifier,
            Instant startTime
    ) {
        sendLaundryMachineBreakdownEmail(toEmail, firstName, machineIdentifier, templateRenderer.formatStartTime(startTime));
    }

    void sendLaundryMachineBreakdownEmail(
            String toEmail,
            String firstName,
            String machineIdentifier,
            String startTimeLabel
    ) {
        dispatch(toEmail, MailType.LAUNDRY_BREAKDOWN,
                templateRenderer.renderLaundryMachineBreakdownEmail(frontendUrl, firstName, machineIdentifier, startTimeLabel));
    }

    @Async(MAIL_EXECUTOR)
    public void sendRoomMaintenanceEmail(
            String toEmail,
            String firstName,
            String roomName,
            Instant startTime
    ) {
        sendRoomMaintenanceEmail(toEmail, firstName, roomName, templateRenderer.formatStartTime(startTime));
    }

    void sendRoomMaintenanceEmail(
            String toEmail,
            String firstName,
            String roomName,
            String startTimeLabel
    ) {
        dispatch(toEmail, MailType.ROOM_MAINTENANCE,
                templateRenderer.renderRoomMaintenanceEmail(frontendUrl, firstName, roomName, startTimeLabel));
    }

    @Async(MAIL_EXECUTOR)
    public void sendIssueStatusChangedEmail(
            String toEmail,
            String firstName,
            String statusLabel,
            String staffNotes
    ) {
        dispatch(toEmail, MailType.ISSUE_STATUS_CHANGED,
                templateRenderer.renderIssueStatusChangedEmail(frontendUrl, firstName, statusLabel, staffNotes));
    }

    @Async(MAIL_EXECUTOR)
    public void sendBookingAutoCancelled15MinEmail(
            String toEmail,
            String firstName,
            String resourceName,
            Instant startTime,
            ResourceSchedulePage page
    ) {
        sendBookingAutoCancelled15MinEmail(toEmail, firstName, resourceName, templateRenderer.formatStartTime(startTime), page);
    }

    void sendBookingAutoCancelled15MinEmail(
            String toEmail,
            String firstName,
            String resourceName,
            String startTimeLabel,
            ResourceSchedulePage page
    ) {
        dispatch(toEmail, MailType.BOOKING_AUTO_CANCELLED,
                templateRenderer.renderBookingAutoCancelled15MinEmail(frontendUrl, firstName, resourceName, startTimeLabel, page));
    }

    @Async(MAIL_EXECUTOR)
    public CompletableFuture<Void> sendPasswordResetEmail(String toEmail, String firstName, String rawToken) {
        try {
            dispatch(toEmail, MailType.PASSWORD_RESET, templateRenderer.renderPasswordResetEmail(frontendUrl, firstName, rawToken));
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async(MAIL_EXECUTOR)
    public void sendAccountBlockedEmail(String toEmail, String firstName) {
        dispatch(toEmail, MailType.ACCOUNT_BLOCKED, templateRenderer.renderAccountBlockedEmail(firstName));
    }

    @Async(MAIL_EXECUTOR)
    public void sendCheckedOutEmail(String toEmail, String firstName) {
        dispatch(toEmail, MailType.CHECKED_OUT, templateRenderer.renderCheckedOutEmail(firstName));
    }

    @Async(MAIL_EXECUTOR)
    public void sendRoomBanEmail(String toEmail, String firstName, LocalDate start, LocalDate end, String reason) {
        dispatch(toEmail, MailType.ROOM_BAN, templateRenderer.renderRoomBanEmail(firstName, start, end, reason));
    }

    /**
     * Sends the rendered email and records the delivery outcome.
     * The recipient address is logged only at DEBUG (success) and ERROR (failure)
     * level; INFO logs stay free of personally identifiable information.
     */
    private void dispatch(String to, MailType type, RenderedEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(email.subject());
            helper.setText(email.plainTextBody(), email.htmlBody());

            mailSender.send(message);
            meterRegistry.counter("mail.sent", "type", type.tag()).increment();
            log.info("Email [type={}] '{}' dispatched", type.tag(), email.subject());
            log.debug("Email [type={}] '{}' dispatched to {}", type.tag(), email.subject(), to);
        } catch (Exception e) {
            meterRegistry.counter("mail.failed", "type", type.tag()).increment();
            if (type.critical()) {
                log.error("CRITICAL: [type={}] email '{}' was not delivered to {}; the resident cannot complete "
                        + "the auth flow until delivery succeeds (FR-AUTH-01)", type.tag(), email.subject(), to, e);
            } else {
                log.error("Failed to send email [type={}] '{}' to {}", type.tag(), email.subject(), to, e);
            }
            throw new MailDeliveryException("Failed to send email '" + email.subject() + "'", e);
        }
    }
}

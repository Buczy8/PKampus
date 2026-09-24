package pl.edu.pk.pkampus.mail;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
 * All send methods are asynchronous (dispatched onto {@code mailExecutor}).
 * Critical emails return a {@link CompletableFuture} to allow callers to observe failure.
 * Fire-and-forget notification exceptions are routed to {@code AsyncUncaughtExceptionHandler}.
 * Delivery outcomes are tracked using {@code mail.sent} and {@code mail.failed} metrics.
 *
 * <p>Callers inside a transaction must not send directly — they publish a
 * domain event instead and a {@code @TransactionalEventListener(AFTER_COMMIT)}
 * listener sends the mail, so residents never receive mail about changes that
 * were rolled back.
 */
@Slf4j
@Service
public class EmailService {

    private static final String MAIL_EXECUTOR = "mailExecutor";

    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer templateRenderer;
    private final String frontendUrl;
    private final String fromEmail;
    private final Counter mailSentCounter;
    private final Counter mailFailedCounter;

    @Autowired
    public EmailService(
            JavaMailSender mailSender,
            EmailTemplateRenderer templateRenderer,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl,
            @Value("${spring.mail.username:noreply@pkampus.pk.edu.pl}") String fromEmail) {
        this.mailSender = mailSender;
        this.templateRenderer = templateRenderer != null ? templateRenderer : new EmailTemplateRenderer();
        this.frontendUrl = frontendUrl;
        this.fromEmail = fromEmail;
        MeterRegistry registry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.mailSentCounter = registry.counter("mail.sent");
        this.mailFailedCounter = registry.counter("mail.failed");
    }

    public EmailService(JavaMailSender mailSender, MeterRegistry meterRegistry, String frontendUrl, String fromEmail) {
        this(mailSender, new EmailTemplateRenderer(), ObjectProviderOf.of(meterRegistry != null ? meterRegistry : new SimpleMeterRegistry()), frontendUrl, fromEmail);
    }

    public EmailService(JavaMailSender mailSender, String frontendUrl, String fromEmail) {
        this(mailSender, new EmailTemplateRenderer(), ObjectProviderOf.of(new SimpleMeterRegistry()), frontendUrl, fromEmail);
    }

    @Async(MAIL_EXECUTOR)
    public CompletableFuture<Void> sendVerificationEmail(String toEmail, String token) {
        try {
            RenderedEmail email = templateRenderer.renderVerificationEmail(frontendUrl, token);
            sendHtmlEmail(toEmail, email);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async(MAIL_EXECUTOR)
    public void sendAccountActivatedEmail(String toEmail, String firstName, String roomNumber, String dormitoryName) {
        RenderedEmail email = templateRenderer.renderAccountActivatedEmail(frontendUrl, firstName, roomNumber, dormitoryName);
        sendHtmlEmail(toEmail, email);
    }

    @Async(MAIL_EXECUTOR)
    public void sendRegistrationRejectedEmail(String toEmail, String firstName, String reason) {
        RenderedEmail email = templateRenderer.renderRegistrationRejectedEmail(firstName, reason);
        sendHtmlEmail(toEmail, email);
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

    @Async(MAIL_EXECUTOR)
    public void sendLaundryMachineBreakdownEmail(
            String toEmail,
            String firstName,
            String machineIdentifier,
            String startTimeLabel
    ) {
        RenderedEmail email = templateRenderer.renderLaundryMachineBreakdownEmail(frontendUrl, firstName, machineIdentifier, startTimeLabel);
        sendHtmlEmail(toEmail, email);
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

    @Async(MAIL_EXECUTOR)
    public void sendRoomMaintenanceEmail(
            String toEmail,
            String firstName,
            String roomName,
            String startTimeLabel
    ) {
        RenderedEmail email = templateRenderer.renderRoomMaintenanceEmail(frontendUrl, firstName, roomName, startTimeLabel);
        sendHtmlEmail(toEmail, email);
    }

    @Async(MAIL_EXECUTOR)
    public void sendIssueStatusChangedEmail(
            String toEmail,
            String firstName,
            String statusLabel,
            String staffNotes
    ) {
        RenderedEmail email = templateRenderer.renderIssueStatusChangedEmail(frontendUrl, firstName, statusLabel, staffNotes);
        sendHtmlEmail(toEmail, email);
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

    @Async(MAIL_EXECUTOR)
    public void sendBookingAutoCancelled15MinEmail(
            String toEmail,
            String firstName,
            String resourceName,
            String startTimeLabel,
            ResourceSchedulePage page
    ) {
        RenderedEmail email = templateRenderer.renderBookingAutoCancelled15MinEmail(frontendUrl, firstName, resourceName, startTimeLabel, page);
        sendHtmlEmail(toEmail, email);
    }

    @Async(MAIL_EXECUTOR)
    public CompletableFuture<Void> sendPasswordResetEmail(String toEmail, String firstName, String rawToken) {
        try {
            RenderedEmail email = templateRenderer.renderPasswordResetEmail(frontendUrl, firstName, rawToken);
            sendHtmlEmail(toEmail, email);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async(MAIL_EXECUTOR)
    public void sendAccountBlockedEmail(String toEmail, String firstName) {
        RenderedEmail email = templateRenderer.renderAccountBlockedEmail(firstName);
        sendHtmlEmail(toEmail, email);
    }

    @Async(MAIL_EXECUTOR)
    public void sendCheckedOutEmail(String toEmail, String firstName) {
        RenderedEmail email = templateRenderer.renderCheckedOutEmail(firstName);
        sendHtmlEmail(toEmail, email);
    }

    @Async(MAIL_EXECUTOR)
    public void sendRoomBanEmail(String toEmail, String firstName, LocalDate start, LocalDate end, String reason) {
        RenderedEmail email = templateRenderer.renderRoomBanEmail(firstName, start, end, reason);
        sendHtmlEmail(toEmail, email);
    }

    void sendHtmlEmail(String to, RenderedEmail email) {
        sendHtmlEmail(to, email.subject(), email.htmlBody(), email.plainTextBody());
    }

    void sendHtmlEmail(String to, String subject, String htmlBody) {
        sendHtmlEmail(to, subject, htmlBody, EmailTemplateRenderer.toPlainText(htmlBody));
    }

    void sendHtmlEmail(String to, String subject, String htmlBody, String plainTextBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainTextBody, htmlBody);

            mailSender.send(message);
            mailSentCounter.increment();
            log.info("Email '{}' sent successfully to {}", subject, to);
        } catch (Exception e) {
            mailFailedCounter.increment();
            log.error("Failed to send email '{}' to {}", subject, to, e);
            throw new MailDeliveryException("Failed to send email '" + subject + "' to " + to, e);
        }
    }

    static String esc(String value) {
        return EmailTemplateRenderer.esc(value);
    }

    private static class ObjectProviderOf {
        static <T> ObjectProvider<T> of(T value) {
            return new ObjectProvider<>() {
                @Override
                public T getObject(Object... args) {
                    return value;
                }

                @Override
                public T getIfAvailable() {
                    return value;
                }

                @Override
                public T getIfUnique() {
                    return value;
                }

                @Override
                public T getObject() {
                    return value;
                }
            };
        }
    }
}

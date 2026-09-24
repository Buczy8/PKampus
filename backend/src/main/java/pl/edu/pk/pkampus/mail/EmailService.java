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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private final String frontendUrl;
    private final String fromEmail;
    private final Counter mailSentCounter;
    private final Counter mailFailedCounter;

    @Autowired
    public EmailService(
            JavaMailSender mailSender,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl,
            @Value("${spring.mail.username:noreply@pkampus.pk.edu.pl}") String fromEmail) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.fromEmail = fromEmail;
        MeterRegistry registry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.mailSentCounter = registry.counter("mail.sent");
        this.mailFailedCounter = registry.counter("mail.failed");
    }

    public EmailService(JavaMailSender mailSender, MeterRegistry meterRegistry, String frontendUrl, String fromEmail) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.fromEmail = fromEmail;
        MeterRegistry registry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.mailSentCounter = registry.counter("mail.sent");
        this.mailFailedCounter = registry.counter("mail.failed");
    }

    public EmailService(JavaMailSender mailSender, String frontendUrl, String fromEmail) {
        this(mailSender, new SimpleMeterRegistry(), frontendUrl, fromEmail);
    }

    @Async(MAIL_EXECUTOR)
    public CompletableFuture<Void> sendVerificationEmail(String toEmail, String token) {
        try {
            String verificationLink = frontendUrl + "/verify-email?token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8);
            String subject = "PKampus - Confirm your registration";
            String body = """
                    <p>Thank you for registering in the PKampus student dormitory system.</p>
                    <p>To confirm your email address and proceed to residency verification, please click the button below (this link is valid for 24 hours):</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Confirm email address</a>
                    </div>
                    <p style="color: #64748b; font-size: 14px;">Or paste the following URL into your browser:<br><a href="%s">%s</a></p>
                    """.formatted(verificationLink, verificationLink, verificationLink);

            sendHtmlEmail(toEmail, subject, layout("Welcome to PKampus!", "#0284c7", body));
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async(MAIL_EXECUTOR)
    public void sendAccountActivatedEmail(String toEmail, String firstName, String roomNumber, String dormitoryName) {
        String subject = "PKampus - Account activated";
        String body = """
                <p>Hello %s,</p>
                <p>Your PKampus account has been activated by the dormitory administration.</p>
                <p><strong>Dormitory:</strong> %s<br><strong>Room:</strong> %s</p>
                <p>You can now sign in and use reservations and issue reporting.</p>
                <div style="text-align: center; margin: 30px 0;">
                    <a href="%s/login" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Sign in</a>
                </div>
                """.formatted(esc(firstName), esc(dormitoryName), esc(roomNumber), frontendUrl);
        sendHtmlEmail(toEmail, subject, layout("Your residency has been approved", "#0284c7", body));
    }

    @Async(MAIL_EXECUTOR)
    public void sendRegistrationRejectedEmail(String toEmail, String firstName, String reason) {
        String subject = "PKampus - Registration rejected";
        String body = """
                <p>Hello %s,</p>
                <p>Your residency verification request was rejected by the dormitory administration.</p>
                <p><strong>Reason:</strong> %s</p>
                <p>If you believe this is a mistake, please contact your dormitory office.</p>
                """.formatted(esc(firstName), esc(reason));
        sendHtmlEmail(toEmail, subject, layout("Registration request rejected", "#b91c1c", body));
    }

    @Async(MAIL_EXECUTOR)
    public void sendLaundryMachineBreakdownEmail(
            String toEmail,
            String firstName,
            String machineIdentifier,
            String startTimeLabel
    ) {
        String subject = "PKampus - Laundry reservation cancelled (machine out of order)";
        String body = """
                <p>Hello %s,</p>
                <p>Your laundry reservation for <strong>%s</strong> starting at <strong>%s</strong> was cancelled because the machine was taken out of service.</p>
                <p>Please book another free slot in the PKampus app.</p>
                <div style="text-align: center; margin: 30px 0;">
                    <a href="%s/laundry" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Open laundry schedule</a>
                </div>
                """.formatted(esc(firstName), esc(machineIdentifier), esc(startTimeLabel), frontendUrl);
        sendHtmlEmail(toEmail, subject, layout("Laundry machine out of order", "#b91c1c", body));
    }

    @Async(MAIL_EXECUTOR)
    public void sendRoomMaintenanceEmail(
            String toEmail,
            String firstName,
            String roomName,
            String startTimeLabel
    ) {
        String subject = "PKampus - Room reservation cancelled (maintenance)";
        String body = """
                <p>Hello %s,</p>
                <p>Your reservation for <strong>%s</strong> starting at <strong>%s</strong> was cancelled because the room was taken out of service.</p>
                <p>Please book another free slot in the PKampus app.</p>
                <div style="text-align: center; margin: 30px 0;">
                    <a href="%s/rooms" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Open room schedule</a>
                </div>
                """.formatted(esc(firstName), esc(roomName), esc(startTimeLabel), frontendUrl);
        sendHtmlEmail(toEmail, subject, layout("Thematic room under maintenance", "#b91c1c", body));
    }

    @Async(MAIL_EXECUTOR)
    public void sendIssueStatusChangedEmail(
            String toEmail,
            String firstName,
            String statusLabel,
            String staffNotes
    ) {
        String notesBlock = (staffNotes == null || staffNotes.isBlank())
                ? ""
                : "<p><strong>Staff note:</strong> %s</p>".formatted(esc(staffNotes));
        String subject = "PKampus - Issue status updated";
        String body = """
                <p>Hello %s,</p>
                <p>The status of your issue report is now: <strong>%s</strong>.</p>
                %s
                <div style="text-align: center; margin: 30px 0;">
                    <a href="%s/issues" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">View my issues</a>
                </div>
                """.formatted(esc(firstName), esc(statusLabel), notesBlock, frontendUrl);
        sendHtmlEmail(toEmail, subject, layout("Your maintenance issue was updated", "#0284c7", body));
    }

    @Async(MAIL_EXECUTOR)
    public void sendBookingAutoCancelled15MinEmail(
            String toEmail,
            String firstName,
            String resourceName,
            String startTimeLabel,
            ResourceSchedulePage page
    ) {
        String subject = "PKampus - Reservation released (15-minute rule)";
        String body = """
                <p>Hello %s,</p>
                <p>Your reservation for <strong>%s</strong> starting at <strong>%s</strong> was automatically released because the key was not collected from the reception desk within 15 minutes of the start time (§2 ust. 5 / BR-02 rule).</p>
                <p>The slot has been made available for other residents.</p>
                <div style="text-align: center; margin: 30px 0;">
                    <a href="%s/%s" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Open schedule</a>
                </div>
                """.formatted(esc(firstName), esc(resourceName), esc(startTimeLabel), frontendUrl, page.path());
        sendHtmlEmail(toEmail, subject, layout("Reservation released", "#b91c1c", body));
    }

    @Async(MAIL_EXECUTOR)
    public CompletableFuture<Void> sendPasswordResetEmail(String toEmail, String firstName, String rawToken) {
        try {
            String resetLink = frontendUrl + "/reset-password?token="
                    + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
            String subject = "PKampus - Reset your password";
            String body = """
                    <p>Hello %s,</p>
                    <p>We received a request to reset your PKampus account password. Click the button below to set a new password (this link is valid for 15 minutes):</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Reset Password</a>
                    </div>
                    <p style="color: #64748b; font-size: 14px;">Or copy and paste the following URL into your browser:<br><a href="%s">%s</a></p>
                    <p style="color: #64748b; font-size: 14px;">If you did not request this change, please ignore this email. Your password will remain unchanged.</p>
                    """.formatted(esc(firstName), resetLink, resetLink, resetLink);
            sendHtmlEmail(toEmail, subject, layout("Password Reset Request", "#0284c7", body));
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async(MAIL_EXECUTOR)
    public void sendAccountBlockedEmail(String toEmail, String firstName) {
        String subject = "PKampus - Account blocked";
        String body = """
                <p>Hello %s,</p>
                <p>Your PKampus account has been blocked by the dormitory administration.
                Contact your dormitory office for details.</p>
                """.formatted(esc(firstName));
        sendHtmlEmail(toEmail, subject, layout("Account blocked", "#b91c1c", body));
    }

    @Async(MAIL_EXECUTOR)
    public void sendCheckedOutEmail(String toEmail, String firstName) {
        String subject = "PKampus - Checked out";
        String body = """
                <p>Hello %s,</p>
                <p>Your residency in PKampus has been closed (checked out).
                The account can no longer be used to sign in.</p>
                """.formatted(esc(firstName));
        sendHtmlEmail(toEmail, subject, layout("Checked out", "#0284c7", body));
    }

    @Async(MAIL_EXECUTOR)
    public void sendRoomBanEmail(String toEmail, String firstName, LocalDate start, LocalDate end, String reason) {
        String subject = "PKampus - Room reservation ban";
        String body = """
                <p>Hello %s,</p>
                <p>A room reservation ban (ROOM_BAN) has been registered for your account.</p>
                <p><strong>Period:</strong> %s – %s<br>
                <strong>Reason:</strong> %s</p>
                <p>During this period you cannot book thematic rooms across the campus.</p>
                """.formatted(esc(firstName), start, end, esc(reason));
        sendHtmlEmail(toEmail, subject, layout("Room reservation ban", "#b91c1c", body));
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(toPlainText(htmlBody), htmlBody);

            mailSender.send(message);
            mailSentCounter.increment();
            log.info("Email '{}' sent successfully to {}", subject, to);
        } catch (Exception e) {
            mailFailedCounter.increment();
            log.error("Failed to send email '{}' to {}", subject, to, e);
            throw new MailDeliveryException("Failed to send email '" + subject + "' to " + to, e);
        }
    }

    private static String layout(String title, String titleColor, String body) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: %s;">%s</h2>
                    %s
                    <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                    <p style="color: #94a3b8; font-size: 12px;">This message was generated automatically by the PKampus system. Please do not reply.</p>
                </div>
                """.formatted(titleColor, title, body);
    }

    /**
     * Escapes user- and staff-controlled values interpolated into HTML templates,
     * so a name, reason or note can never break out into markup.
     */
    static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String toPlainText(String html) {
        return html.replaceAll("<[^>]*>", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

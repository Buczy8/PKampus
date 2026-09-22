package pl.edu.pk.pkampus.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${spring.mail.username:noreply@pkampus.pk.edu.pl}")
    private String fromEmail;

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        String verificationLink = frontendUrl + "/verify-email?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        String subject = "PKampus - Confirm your registration";
        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #0284c7;">Welcome to PKampus!</h2>
                    <p>Thank you for registering in the PKampus student dormitory system.</p>
                    <p>To confirm your email address and proceed to residency verification, please click the button below (this link is valid for 24 hours):</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Confirm email address</a>
                    </div>
                    <p style="color: #64748b; font-size: 14px;">Or paste the following URL into your browser:<br><a href="%s">%s</a></p>
                    <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                    <p style="color: #94a3b8; font-size: 12px;">This message was generated automatically by the PKampus system. Please do not reply.</p>
                </div>
                """.formatted(verificationLink, verificationLink, verificationLink);

        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    @Async
    public void sendAccountActivatedEmail(String toEmail, String firstName, String roomNumber, String dormitoryName) {
        String subject = "PKampus - Account activated";
        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #0284c7;">Your residency has been approved</h2>
                    <p>Hello %s,</p>
                    <p>Your PKampus account has been activated by the dormitory administration.</p>
                    <p><strong>Dormitory:</strong> %s<br><strong>Room:</strong> %s</p>
                    <p>You can now sign in and use reservations and issue reporting.</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s/login" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Sign in</a>
                    </div>
                    <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                    <p style="color: #94a3b8; font-size: 12px;">This message was generated automatically by the PKampus system. Please do not reply.</p>
                </div>
                """.formatted(firstName, dormitoryName, roomNumber, frontendUrl);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    @Async
    public void sendRegistrationRejectedEmail(String toEmail, String firstName, String reason) {
        String subject = "PKampus - Registration rejected";
        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #b91c1c;">Registration request rejected</h2>
                    <p>Hello %s,</p>
                    <p>Your residency verification request was rejected by the dormitory administration.</p>
                    <p><strong>Reason:</strong> %s</p>
                    <p>If you believe this is a mistake, please contact your dormitory office.</p>
                    <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                    <p style="color: #94a3b8; font-size: 12px;">This message was generated automatically by the PKampus system. Please do not reply.</p>
                </div>
                """.formatted(firstName, reason);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    @Async
    public void sendLaundryMachineBreakdownEmail(
            String toEmail,
            String firstName,
            String machineIdentifier,
            String startTimeLabel
    ) {
        String subject = "PKampus - Laundry reservation cancelled (machine out of order)";
        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #b91c1c;">Laundry machine out of order</h2>
                    <p>Hello %s,</p>
                    <p>Your laundry reservation for <strong>%s</strong> starting at <strong>%s</strong> was cancelled because the machine was taken out of service.</p>
                    <p>Please book another free slot in the PKampus app.</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s/laundry" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Open laundry schedule</a>
                    </div>
                    <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                    <p style="color: #94a3b8; font-size: 12px;">This message was generated automatically by the PKampus system. Please do not reply.</p>
                </div>
                """.formatted(firstName, machineIdentifier, startTimeLabel, frontendUrl);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    @Async
    public void sendRoomMaintenanceEmail(
            String toEmail,
            String firstName,
            String roomName,
            String startTimeLabel
    ) {
        String subject = "PKampus - Room reservation cancelled (maintenance)";
        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #b91c1c;">Thematic room under maintenance</h2>
                    <p>Hello %s,</p>
                    <p>Your reservation for <strong>%s</strong> starting at <strong>%s</strong> was cancelled because the room was taken out of service.</p>
                    <p>Please book another free slot in the PKampus app.</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s/rooms" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Open room schedule</a>
                    </div>
                    <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                    <p style="color: #94a3b8; font-size: 12px;">This message was generated automatically by the PKampus system. Please do not reply.</p>
                </div>
                """.formatted(firstName, roomName, startTimeLabel, frontendUrl);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    @Async
    public void sendIssueStatusChangedEmail(
            String toEmail,
            String firstName,
            String statusLabel,
            String staffNotes
    ) {
        String notesBlock = (staffNotes == null || staffNotes.isBlank())
                ? ""
                : "<p><strong>Staff note:</strong> %s</p>".formatted(staffNotes);
        String subject = "PKampus - Issue status updated";
        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #0284c7;">Your maintenance issue was updated</h2>
                    <p>Hello %s,</p>
                    <p>The status of your issue report is now: <strong>%s</strong>.</p>
                    %s
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s/issues" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">View my issues</a>
                    </div>
                    <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                    <p style="color: #94a3b8; font-size: 12px;">This message was generated automatically by the PKampus system. Please do not reply.</p>
                </div>
                """.formatted(firstName, statusLabel, notesBlock, frontendUrl);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    @Async
    public void sendBookingAutoCancelled15MinEmail(
            String toEmail,
            String firstName,
            String resourceName,
            String startTimeLabel,
            String resourceTypeUrlPath
    ) {
        String subject = "PKampus - Reservation released (15-minute rule)";
        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #b91c1c;">Reservation released</h2>
                    <p>Hello %s,</p>
                    <p>Your reservation for <strong>%s</strong> starting at <strong>%s</strong> was automatically released because the key was not collected from the reception desk within 15 minutes of the start time (§2 ust. 5 / BR-02 rule).</p>
                    <p>The slot has been made available for other residents.</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s/%s" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Open schedule</a>
                    </div>
                    <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                    <p style="color: #94a3b8; font-size: 12px;">This message was generated automatically by the PKampus system. Please do not reply.</p>
                </div>
                """.formatted(firstName, resourceName, startTimeLabel, frontendUrl, resourceTypeUrlPath);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Verification email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}

package pl.edu.pk.pkampus.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
        String verificationLink = frontendUrl + "/verify-email?token=" + token;
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

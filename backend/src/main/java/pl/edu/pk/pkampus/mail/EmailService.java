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
        String subject = "PKampus - Potwierdzenie rejestracji konta";
        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #0284c7;">Witaj w PKampus!</h2>
                    <p>Dziękujemy za rejestrację w systemie domów studenckich Politechniki Krakowskiej.</p>
                    <p>Aby potwierdzić swój adres e-mail i przejść do etapu weryfikacji meldunku, kliknij poniższy przycisk (link jest ważny przez 24 godziny):</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Potwierdź adres e-mail</a>
                    </div>
                    <p style="color: #64748b; font-size: 14px;">Lub wklej poniższy adres do okna przeglądarki:<br><a href="%s">%s</a></p>
                    <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                    <p style="color: #94a3b8; font-size: 12px;">Wiadomość wygenerowana automatycznie przez system PKampus. Prosimy na nią nie odpowiadać.</p>
                </div>
                """.formatted(verificationLink, verificationLink, verificationLink);

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

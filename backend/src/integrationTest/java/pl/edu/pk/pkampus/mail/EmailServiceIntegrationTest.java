package pl.edu.pk.pkampus.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("EmailService Spring integration tests")
class EmailServiceIntegrationTest {

    @Autowired
    private EmailService emailService;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("Should dispatch an account-activated email through the Spring context with injected configuration")
    void shouldDispatchEmailThroughSpringContext() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendAccountActivatedEmail("resident@pk.edu.pl", "Pawel", "101", "DS-1");

        // Assert: send methods are @Async, hence the timeout-based verification
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(3000)).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertEquals("PKampus - Account activated", sent.getSubject());
        assertEquals("resident@pk.edu.pl", sent.getAllRecipients()[0].toString());
        assertEquals("noreply@pkampus.pk.edu.pl", sent.getFrom()[0].toString());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sent.writeTo(baos);
        assertTrue(baos.toString().contains("Pawel"));
        assertTrue(baos.toString().contains("http://localhost:5173/login"));
    }

    @Test
    @DisplayName("Should asynchronously dispatch password reset email with correct frontend URL from configuration")
    void shouldDispatchPasswordResetEmailWithConfiguredFrontendUrl() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendPasswordResetEmail("resident@pk.edu.pl", "Pawel", "test-token-value");

        // Assert: using timeout() since sendPasswordResetEmail is @Async
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(3000)).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertEquals("PKampus - Reset your password", sent.getSubject());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sent.writeTo(baos);
        String body = baos.toString();
        assertTrue(body.contains("http://localhost:5173/reset-password?token=test-token-value"));
        assertTrue(body.contains("Pawel"));
    }
}

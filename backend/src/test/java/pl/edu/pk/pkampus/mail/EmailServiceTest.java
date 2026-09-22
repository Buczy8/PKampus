package pl.edu.pk.pkampus.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@pkampus.pk.edu.pl");
    }

    @Test
    void shouldSendVerificationEmailSuccessfully() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        String toEmail = "student@pk.edu.pl";
        String token = "sample-token-123+xyz";

        // Act
        emailService.sendVerificationEmail(toEmail, token);

        // Assert
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("PKampus - Confirm your registration", sentMessage.getSubject());
        assertEquals(toEmail, sentMessage.getAllRecipients()[0].toString());
        assertEquals("noreply@pkampus.pk.edu.pl", sentMessage.getFrom()[0].toString());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sentMessage.writeTo(baos);
        String rawContent = baos.toString();
        assertTrue(rawContent.contains("sample-token-123%2Bxyz"));
        assertTrue(rawContent.contains("Confirm email address"));
    }

    @Test
    void shouldSendAccountActivatedEmailSuccessfully() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendAccountActivatedEmail("student@pk.edu.pl", "Jan", "101", "DS-1");

        // Assert
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("PKampus - Account activated", sentMessage.getSubject());
        assertEquals("student@pk.edu.pl", sentMessage.getAllRecipients()[0].toString());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sentMessage.writeTo(baos);
        String content = baos.toString();
        assertTrue(content.contains("Jan"));
        assertTrue(content.contains("DS-1"));
        assertTrue(content.contains("101"));
        assertTrue(content.contains("http://localhost:5173/login"));
    }

    @Test
    void shouldSendRegistrationRejectedEmailSuccessfully() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendRegistrationRejectedEmail("student@pk.edu.pl", "Jan", "Invalid student ID document");

        // Assert
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("PKampus - Registration rejected", sentMessage.getSubject());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sentMessage.writeTo(baos);
        String content = baos.toString();
        assertTrue(content.contains("Jan"));
        assertTrue(content.contains("Invalid student ID document"));
    }

    @Test
    void shouldSendLaundryMachineBreakdownEmailSuccessfully() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendLaundryMachineBreakdownEmail("student@pk.edu.pl", "Jan", "Pralka #3", "2026-09-23 14:00");

        // Assert
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("PKampus - Laundry reservation cancelled (machine out of order)", sentMessage.getSubject());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sentMessage.writeTo(baos);
        String content = baos.toString();
        assertTrue(content.contains("Jan"));
        assertTrue(content.contains("Pralka #3"));
        assertTrue(content.contains("2026-09-23 14:00"));
        assertTrue(content.contains("http://localhost:5173/laundry"));
    }

    @Test
    void shouldSendRoomMaintenanceEmailSuccessfully() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendRoomMaintenanceEmail("student@pk.edu.pl", "Jan", "Salka bilardowa", "2026-09-23 18:00");

        // Assert
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("PKampus - Room reservation cancelled (maintenance)", sentMessage.getSubject());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sentMessage.writeTo(baos);
        String content = baos.toString();
        assertTrue(content.contains("Jan"));
        assertTrue(content.contains("Salka bilardowa"));
        assertTrue(content.contains("http://localhost:5173/rooms"));
    }

    @Test
    void shouldSendIssueStatusChangedEmailWithStaffNotes() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendIssueStatusChangedEmail("student@pk.edu.pl", "Jan", "IN_PROGRESS", "Plumber will arrive at 10:00");

        // Assert
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("PKampus - Issue status updated", sentMessage.getSubject());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sentMessage.writeTo(baos);
        String content = baos.toString();
        assertTrue(content.contains("IN_PROGRESS"));
        assertTrue(content.contains("Plumber will arrive at 10:00"));
        assertTrue(content.contains("http://localhost:5173/issues"));
    }

    @Test
    void shouldSendIssueStatusChangedEmailWithoutStaffNotes() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act: null notes
        emailService.sendIssueStatusChangedEmail("student@pk.edu.pl", "Jan", "RESOLVED", null);

        // Assert
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("PKampus - Issue status updated", sentMessage.getSubject());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sentMessage.writeTo(baos);
        String content = baos.toString();
        assertTrue(content.contains("RESOLVED"));
        assertFalse(content.contains("Staff note:"));
    }

    @Test
    void shouldSendIssueStatusChangedEmailWithBlankStaffNotes() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act: blank notes
        emailService.sendIssueStatusChangedEmail("student@pk.edu.pl", "Jan", "REJECTED", "   ");

        // Assert
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sentMessage.writeTo(baos);
        String content = baos.toString();
        assertTrue(content.contains("REJECTED"));
        assertFalse(content.contains("Staff note:"));
    }

    @Test
    void shouldSendBookingAutoCancelled15MinEmailSuccessfully() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendBookingAutoCancelled15MinEmail(
                "student@pk.edu.pl",
                "Jan",
                "Pralka #1",
                "12:00",
                "laundry"
        );

        // Assert
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("PKampus - Reservation released (15-minute rule)", sentMessage.getSubject());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sentMessage.writeTo(baos);
        String content = baos.toString();
        assertTrue(content.contains("Jan"));
        assertTrue(content.contains("Pralka #1"));
        assertTrue(content.contains("15-minute rule"));
        assertTrue(content.contains("http://localhost:5173/laundry"));
    }

    @Test
    void shouldSendPasswordResetEmailSuccessfully() throws Exception {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        String rawToken = "raw-reset-token-123+abc";

        // Act
        emailService.sendPasswordResetEmail("student@pk.edu.pl", "Jan", rawToken);

        // Assert
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("PKampus - Reset your password", sentMessage.getSubject());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sentMessage.writeTo(baos);
        String content = baos.toString();
        assertTrue(content.contains("Jan"));
        assertTrue(content.contains("raw-reset-token-123%2Babc"));
        assertTrue(content.contains("http://localhost:5173/reset-password?token="));
    }

    @Test
    void shouldGracefullyHandleExceptionWhenMailSenderThrows() {
        // Arrange
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP connection refused")).when(mailSender).send(any(MimeMessage.class));

        // Act & Assert (sendHtmlEmail catches Exception, logs it, and does not crash caller)
        assertDoesNotThrow(() -> emailService.sendVerificationEmail("student@pk.edu.pl", "test-token"));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void shouldGracefullyHandleExceptionWhenCreateMimeMessageThrows() {
        // Arrange
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("Mime creation failed"));

        // Act & Assert
        assertDoesNotThrow(() -> emailService.sendVerificationEmail("student@pk.edu.pl", "test-token"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}

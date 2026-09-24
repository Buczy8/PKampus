package pl.edu.pk.pkampus.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmailTemplateRenderer unit tests (AAA)")
class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    @Test
    @DisplayName("Should escape HTML special characters including single quote")
    void shouldEscapeHtmlSpecialChars() {
        // Arrange
        String input = "<script>alert('xss' & \"attack\")</script>";

        // Act
        String escaped = EmailTemplateRenderer.esc(input);

        // Assert
        assertEquals("&lt;script&gt;alert(&#39;xss&#39; &amp; &quot;attack&quot;)&lt;/script&gt;", escaped);
    }

    @Test
    @DisplayName("Should convert HTML to plain text cleanly")
    void shouldConvertToPlainText() {
        // Arrange
        String html = "<p>Hello <strong>World</strong>!</p><p>Check <a href=\"http://test\">link</a> &amp; &#39;quote&#39;.</p>";

        // Act
        String plain = EmailTemplateRenderer.toPlainText(html);

        // Assert
        assertEquals("Hello World ! Check link & 'quote'.", plain);
    }

    @Test
    @DisplayName("Should build verification email using UriComponentsBuilder")
    void shouldRenderVerificationEmailWithUriComponents() {
        // Act
        RenderedEmail rendered = renderer.renderVerificationEmail("http://localhost:5173", "token+with/special=chars");

        // Assert
        assertTrue(rendered.htmlBody().contains("http://localhost:5173/verify-email?token=token+with/special=chars")
                || rendered.htmlBody().contains("token%2Bwith%2Fspecial%3Dchars"));
        assertTrue(rendered.subject().contains("Confirm your registration"));
    }

    @Test
    @DisplayName("Should format instant in Warsaw timezone")
    void shouldFormatStartTimeInWarsawTimezone() {
        // Arrange (2026-09-24 14:00 UTC = 16:00 CEST)
        Instant instant = Instant.parse("2026-09-24T14:00:00Z");

        // Act
        String formatted = renderer.formatStartTime(instant);

        // Assert
        assertEquals("24.09.2026 16:00", formatted);
    }
}

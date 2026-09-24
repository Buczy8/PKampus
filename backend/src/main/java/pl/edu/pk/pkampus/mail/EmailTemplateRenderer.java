package pl.edu.pk.pkampus.mail;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Renders HTML and plain-text email contents with safe escaping and consistent layouts.
 */
@Component
class EmailTemplateRenderer {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter SLOT_LABEL =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(WARSAW);

    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    public RenderedEmail renderVerificationEmail(String frontendUrl, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String verificationLink = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/verify-email")
                .queryParam("token", encodedToken)
                .build(true)
                .toUriString();
        String subject = "PKampus - Confirm your registration";
        String body = """
                <p>Thank you for registering in the PKampus student dormitory system.</p>
                <p>To confirm your email address and proceed to residency verification, please click the button below (this link is valid for 24 hours):</p>
                <div style="text-align: center; margin: 30px 0;">
                    <a href="%s" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Confirm email address</a>
                </div>
                <p style="color: #64748b; font-size: 14px;">Or paste the following URL into your browser:<br><a href="%s">%s</a></p>
                """.formatted(verificationLink, verificationLink, verificationLink);

        String html = layout("Welcome to PKampus!", "#0284c7", body);
        return new RenderedEmail(subject, html, toPlainText(html));
    }

    public RenderedEmail renderPasswordResetEmail(String frontendUrl, String firstName, String rawToken) {
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String resetLink = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/reset-password")
                .queryParam("token", encodedToken)
                .build(true)
                .toUriString();
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
        String html = layout("Password Reset Request", "#0284c7", body);
        return new RenderedEmail(subject, html, toPlainText(html));
    }

    public RenderedEmail renderAccountActivatedEmail(String frontendUrl, String firstName, String roomNumber, String dormitoryName) {
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
        String html = layout("Your residency has been approved", "#0284c7", body);
        return new RenderedEmail(subject, html, toPlainText(html));
    }

    public RenderedEmail renderRegistrationRejectedEmail(String firstName, String reason) {
        String subject = "PKampus - Registration rejected";
        String body = """
                <p>Hello %s,</p>
                <p>Your residency verification request was rejected by the dormitory administration.</p>
                <p><strong>Reason:</strong> %s</p>
                <p>If you believe this is a mistake, please contact your dormitory office.</p>
                """.formatted(esc(firstName), esc(reason));
        String html = layout("Registration request rejected", "#b91c1c", body);
        return new RenderedEmail(subject, html, toPlainText(html));
    }

    public RenderedEmail renderLaundryMachineBreakdownEmail(
            String frontendUrl,
            String firstName,
            String machineIdentifier,
            String formattedStartTime
    ) {
        String subject = "PKampus - Laundry reservation cancelled (machine out of order)";
        String body = """
                <p>Hello %s,</p>
                <p>Your laundry reservation for <strong>%s</strong> starting at <strong>%s</strong> was cancelled because the machine was taken out of service.</p>
                <p>Please book another free slot in the PKampus app.</p>
                <div style="text-align: center; margin: 30px 0;">
                    <a href="%s/laundry" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Open laundry schedule</a>
                </div>
                """.formatted(esc(firstName), esc(machineIdentifier), esc(formattedStartTime), frontendUrl);
        String html = layout("Laundry machine out of order", "#b91c1c", body);
        return new RenderedEmail(subject, html, toPlainText(html));
    }

    public RenderedEmail renderRoomMaintenanceEmail(
            String frontendUrl,
            String firstName,
            String roomName,
            String formattedStartTime
    ) {
        String subject = "PKampus - Room reservation cancelled (maintenance)";
        String body = """
                <p>Hello %s,</p>
                <p>Your reservation for <strong>%s</strong> starting at <strong>%s</strong> was cancelled because the room was taken out of service.</p>
                <p>Please book another free slot in the PKampus app.</p>
                <div style="text-align: center; margin: 30px 0;">
                    <a href="%s/rooms" style="display: inline-block; padding: 12px 24px; background-color: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-weight: bold;">Open room schedule</a>
                </div>
                """.formatted(esc(firstName), esc(roomName), esc(formattedStartTime), frontendUrl);
        String html = layout("Thematic room under maintenance", "#b91c1c", body);
        return new RenderedEmail(subject, html, toPlainText(html));
    }

    public RenderedEmail renderIssueStatusChangedEmail(
            String frontendUrl,
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
        String html = layout("Your maintenance issue was updated", "#0284c7", body);
        return new RenderedEmail(subject, html, toPlainText(html));
    }

    public RenderedEmail renderBookingAutoCancelled15MinEmail(
            String frontendUrl,
            String firstName,
            String resourceName,
            String formattedStartTime,
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
                """.formatted(esc(firstName), esc(resourceName), esc(formattedStartTime), frontendUrl, page.path());
        String html = layout("Reservation released", "#b91c1c", body);
        return new RenderedEmail(subject, html, toPlainText(html));
    }

    public RenderedEmail renderAccountBlockedEmail(String firstName) {
        String subject = "PKampus - Account blocked";
        String body = """
                <p>Hello %s,</p>
                <p>Your PKampus account has been blocked by the dormitory administration.
                Contact your dormitory office for details.</p>
                """.formatted(esc(firstName));
        String html = layout("Account blocked", "#b91c1c", body);
        return new RenderedEmail(subject, html, toPlainText(html));
    }

    public RenderedEmail renderCheckedOutEmail(String firstName) {
        String subject = "PKampus - Checked out";
        String body = """
                <p>Hello %s,</p>
                <p>Your residency in PKampus has been closed (checked out).
                The account can no longer be used to sign in.</p>
                """.formatted(esc(firstName));
        String html = layout("Checked out", "#0284c7", body);
        return new RenderedEmail(subject, html, toPlainText(html));
    }

    public RenderedEmail renderRoomBanEmail(String firstName, LocalDate start, LocalDate end, String reason) {
        String subject = "PKampus - Room reservation ban";
        String body = """
                <p>Hello %s,</p>
                <p>A room reservation ban (ROOM_BAN) has been registered for your account.</p>
                <p><strong>Period:</strong> %s – %s<br>
                <strong>Reason:</strong> %s</p>
                <p>During this period you cannot book thematic rooms across the campus.</p>
                """.formatted(esc(firstName), start, end, esc(reason));
        String html = layout("Room reservation ban", "#b91c1c", body);
        return new RenderedEmail(subject, html, toPlainText(html));
    }

    public String formatStartTime(Instant instant) {
        return SLOT_LABEL.format(instant);
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

    static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static String toPlainText(String html) {
        String stripped = HTML_TAGS.matcher(html).replaceAll(" ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        return MULTI_WHITESPACE.matcher(stripped).replaceAll(" ").trim();
    }
}

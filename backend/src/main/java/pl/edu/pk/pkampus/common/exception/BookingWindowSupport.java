package pl.edu.pk.pkampus.common.exception;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * Maps last-line database booking-window trigger rejections (SQLSTATE P0001
 * with a {@code *_booking_window:} prefix) to 422 business-rule errors so they
 * surface as validation failures instead of misleading 409 slot conflicts.
 * Shared by the laundry and thematic-room booking flows.
 */
public final class BookingWindowSupport {

    private BookingWindowSupport() {
    }

    public static boolean isViolation(DataIntegrityViolationException ex, String marker) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(marker);
    }

    public static BusinessRuleException toRuleViolation(
            DataIntegrityViolationException ex, String fallbackMessage) {
        String raw = ex.getMostSpecificCause().getMessage();
        if (raw == null) {
            raw = ex.getMessage();
        }
        String firstLine = raw != null ? raw.split("\\R")[0].replaceFirst("^(ERROR:\\s*)", "").trim() : "";
        return new BusinessRuleException(firstLine.isEmpty() ? fallbackMessage : firstLine);
    }
}

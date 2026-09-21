package pl.edu.pk.pkampus.modules.profile;

/**
 * Daily verification token derived without DB state (FR-CARD-02 / ERD §1.8).
 */
public record CardDayToken(
        String dayCode,
        String dayColorHex,
        String dayColorName,
        String validDate
) {
}

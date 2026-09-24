package pl.edu.pk.pkampus.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("MailType unit tests (AAA)")
class MailTypeTest {

    @Test
    @DisplayName("Only auth-critical templates are flagged as critical")
    void onlyAuthCriticalTemplatesAreCritical() {
        // Arrange
        List<MailType> all = List.of(MailType.values());

        // Act
        List<MailType> critical = all.stream().filter(MailType::critical).toList();

        // Assert
        assertEquals(List.of(MailType.VERIFICATION, MailType.PASSWORD_RESET), critical);
    }

    @Test
    @DisplayName("Every template carries a unique metric tag")
    void tagsAreUnique() {
        // Arrange
        List<MailType> all = List.of(MailType.values());

        // Act
        long distinctTags = all.stream().map(MailType::tag).distinct().count();

        // Assert
        assertEquals(all.size(), distinctTags);
    }
}

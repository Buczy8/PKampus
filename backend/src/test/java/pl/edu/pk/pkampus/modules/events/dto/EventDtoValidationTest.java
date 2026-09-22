package pl.edu.pk.pkampus.modules.events.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.events.DormEventPriority;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Event DTO validation unit tests (AAA)")
class EventDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("CreateDormEventRequestDto passes with valid data")
    void createDormEventRequestDtoValid() {
        // Arrange
        CreateDormEventRequestDto dto = CreateDormEventRequestDto.builder()
                .title("Przerwa w dostawie wody")
                .description("W godzinach 10:00 - 14:00")
                .priority(DormEventPriority.WARNING)
                .eventDate(Instant.now())
                .endDate(Instant.now().plusSeconds(3600))
                .build();

        // Act
        Set<ConstraintViolation<CreateDormEventRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateDormEventRequestDto fails when required fields are missing or blank")
    void createDormEventRequestDtoInvalid() {
        // Arrange
        CreateDormEventRequestDto dto = CreateDormEventRequestDto.builder()
                .title("   ")
                .description("")
                .priority(null)
                .eventDate(null)
                .build();

        // Act
        Set<ConstraintViolation<CreateDormEventRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("title")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("description")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("priority")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("eventDate")));
    }

    @Test
    @DisplayName("CreateDormEventRequestDto fails when title exceeds 200 characters")
    void createDormEventRequestDtoTitleTooLong() {
        // Arrange
        CreateDormEventRequestDto dto = CreateDormEventRequestDto.builder()
                .title("T".repeat(201))
                .description("Opis")
                .priority(DormEventPriority.INFO)
                .eventDate(Instant.now())
                .build();

        // Act
        Set<ConstraintViolation<CreateDormEventRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("title", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    @DisplayName("UpdateDormEventRequestDto passes with valid or null fields")
    void updateDormEventRequestDtoValid() {
        // Arrange
        UpdateDormEventRequestDto emptyDto = new UpdateDormEventRequestDto();
        UpdateDormEventRequestDto populatedDto = UpdateDormEventRequestDto.builder()
                .title("Zaktualizowany tytuł")
                .description("Zaktualizowany opis")
                .priority(DormEventPriority.CRITICAL)
                .build();

        // Act
        Set<ConstraintViolation<UpdateDormEventRequestDto>> emptyViolations = validator.validate(emptyDto);
        Set<ConstraintViolation<UpdateDormEventRequestDto>> populatedViolations = validator.validate(populatedDto);

        // Assert
        assertTrue(emptyViolations.isEmpty());
        assertTrue(populatedViolations.isEmpty());
    }

    @Test
    @DisplayName("UpdateDormEventRequestDto fails when title exceeds 200 characters")
    void updateDormEventRequestDtoTitleTooLong() {
        // Arrange
        UpdateDormEventRequestDto dto = UpdateDormEventRequestDto.builder()
                .title("A".repeat(201))
                .build();

        // Act
        Set<ConstraintViolation<UpdateDormEventRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("title", violations.iterator().next().getPropertyPath().toString());
    }
}

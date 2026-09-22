package pl.edu.pk.pkampus.modules.issues.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.issues.IssueCategory;
import pl.edu.pk.pkampus.modules.issues.IssueLocationType;
import pl.edu.pk.pkampus.modules.issues.IssueUrgency;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Issue DTO validation unit tests (AAA)")
class IssueDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("CreateIssueRequestDto passes with valid MY_ROOM data")
    void createIssueRequestDtoValidMyRoom() {
        // Arrange
        CreateIssueRequestDto dto = new CreateIssueRequestDto(
                IssueLocationType.MY_ROOM,
                null,
                IssueCategory.PLUMBING,
                IssueUrgency.URGENT,
                "Cieknie kran w umywalce"
        );

        // Act
        Set<ConstraintViolation<CreateIssueRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateIssueRequestDto passes with valid COMMON_AREA data")
    void createIssueRequestDtoValidCommonArea() {
        // Arrange
        CreateIssueRequestDto dto = new CreateIssueRequestDto(
                IssueLocationType.COMMON_AREA,
                "pralnia",
                IssueCategory.ELECTRICAL,
                IssueUrgency.NORMAL,
                "Przepalona żarówka nad pralką nr 3"
        );

        // Act
        Set<ConstraintViolation<CreateIssueRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateIssueRequestDto fails when required fields are missing or blank")
    void createIssueRequestDtoMissingRequired() {
        // Arrange
        CreateIssueRequestDto dto = new CreateIssueRequestDto(
                null,
                null,
                null,
                null,
                "   "
        );

        // Act
        Set<ConstraintViolation<CreateIssueRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("locationType")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("category")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("urgency")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("description")));
    }

    @Test
    @DisplayName("CreateIssueRequestDto fails when description or commonAreaName exceed max sizes")
    void createIssueRequestDtoTooLong() {
        // Arrange
        CreateIssueRequestDto dto = new CreateIssueRequestDto(
                IssueLocationType.COMMON_AREA,
                "A".repeat(101),
                IssueCategory.OTHER,
                IssueUrgency.NORMAL,
                "B".repeat(4001)
        );

        // Act
        Set<ConstraintViolation<CreateIssueRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("commonAreaName")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("description")));
    }
}

package pl.edu.pk.pkampus.modules.admin.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Admin DTO validation unit tests (AAA)")
class AdminDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("ActivateResidentRequestDto passes with valid or null room number")
    void activateResidentRequestDtoValid() {
        // Arrange
        ActivateResidentRequestDto nullDto = new ActivateResidentRequestDto(null);
        ActivateResidentRequestDto validDto = new ActivateResidentRequestDto("101A");

        // Act
        Set<ConstraintViolation<ActivateResidentRequestDto>> nullViolations = validator.validate(nullDto);
        Set<ConstraintViolation<ActivateResidentRequestDto>> validViolations = validator.validate(validDto);

        // Assert
        assertTrue(nullViolations.isEmpty());
        assertTrue(validViolations.isEmpty());
    }

    @Test
    @DisplayName("ActivateResidentRequestDto fails when room number exceeds 10 characters")
    void activateResidentRequestDtoTooLong() {
        // Arrange
        ActivateResidentRequestDto dto = new ActivateResidentRequestDto("12345678901");

        // Act
        Set<ConstraintViolation<ActivateResidentRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("roomNumber", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    @DisplayName("RejectResidentRequestDto passes with non-blank reason")
    void rejectResidentRequestDtoValid() {
        // Arrange
        RejectResidentRequestDto dto = new RejectResidentRequestDto("Not on housing list");

        // Act
        Set<ConstraintViolation<RejectResidentRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("RejectResidentRequestDto fails with blank or oversized reason")
    void rejectResidentRequestDtoInvalid() {
        // Arrange
        RejectResidentRequestDto blankDto = new RejectResidentRequestDto("   ");
        RejectResidentRequestDto oversizedDto = new RejectResidentRequestDto("a".repeat(1001));

        // Act
        Set<ConstraintViolation<RejectResidentRequestDto>> blankViolations = validator.validate(blankDto);
        Set<ConstraintViolation<RejectResidentRequestDto>> oversizedViolations = validator.validate(oversizedDto);

        // Assert
        assertFalse(blankViolations.isEmpty());
        assertFalse(oversizedViolations.isEmpty());
    }

    @Test
    @DisplayName("CreateReceptionistRequestDto passes with valid payload")
    void createReceptionistRequestDtoValid() {
        // Arrange
        CreateReceptionistRequestDto dto = CreateReceptionistRequestDto.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .email("jan.kowalski@pk.edu.pl")
                .phoneNumber("+48123456789")
                .password("Password123!")
                .build();

        // Act
        Set<ConstraintViolation<CreateReceptionistRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateReceptionistRequestDto fails on blank fields, invalid phone, or weak password")
    void createReceptionistRequestDtoInvalid() {
        // Arrange
        CreateReceptionistRequestDto dto = CreateReceptionistRequestDto.builder()
                .firstName("")
                .lastName("")
                .email("")
                .phoneNumber("invalid-phone")
                .password("weak")
                .build();

        // Act
        Set<ConstraintViolation<CreateReceptionistRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("firstName")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("lastName")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    @DisplayName("UpdateReceptionistRequestDto passes with valid partial fields")
    void updateReceptionistRequestDtoValid() {
        // Arrange
        UpdateReceptionistRequestDto dto = UpdateReceptionistRequestDto.builder()
                .firstName("Stanisław")
                .lastName("Nowak")
                .phoneNumber("+48987654321")
                .status(UserStatus.ACTIVE)
                .build();

        // Act
        Set<ConstraintViolation<UpdateReceptionistRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("UpdateReceptionistRequestDto fails when field lengths are exceeded")
    void updateReceptionistRequestDtoInvalidLengths() {
        // Arrange
        UpdateReceptionistRequestDto dto = UpdateReceptionistRequestDto.builder()
                .firstName("a".repeat(51))
                .lastName("b".repeat(81))
                .phoneNumber("c".repeat(21))
                .build();

        // Act
        Set<ConstraintViolation<UpdateReceptionistRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(3, violations.size());
    }

    @Test
    @DisplayName("CreateRoomBanRequestDto passes with valid 1-3 months duration and reason")
    void createRoomBanRequestDtoValid() {
        // Arrange
        CreateRoomBanRequestDto dto = CreateRoomBanRequestDto.builder()
                .durationMonths(2)
                .reason("Noise after quiet hours")
                .build();

        // Act
        Set<ConstraintViolation<CreateRoomBanRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateRoomBanRequestDto fails on invalid duration or blank reason")
    void createRoomBanRequestDtoInvalid() {
        // Arrange
        CreateRoomBanRequestDto underDto = CreateRoomBanRequestDto.builder()
                .durationMonths(0)
                .reason("Reason")
                .build();
        CreateRoomBanRequestDto overDto = CreateRoomBanRequestDto.builder()
                .durationMonths(4)
                .reason("Reason")
                .build();
        CreateRoomBanRequestDto blankReasonDto = CreateRoomBanRequestDto.builder()
                .durationMonths(2)
                .reason("  ")
                .build();

        // Act & Assert
        assertFalse(validator.validate(underDto).isEmpty());
        assertFalse(validator.validate(overDto).isEmpty());
        assertFalse(validator.validate(blankReasonDto).isEmpty());
    }

    @Test
    @DisplayName("CreateDormRoomRequestDto passes with valid parameters")
    void createDormRoomRequestDtoValid() {
        // Arrange
        CreateDormRoomRequestDto dto = CreateDormRoomRequestDto.builder()
                .roomNumber("101")
                .floor(1)
                .capacity(2)
                .build();

        // Act
        Set<ConstraintViolation<CreateDormRoomRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateDormRoomRequestDto fails on blank roomNumber, negative floor, or capacity out of range")
    void createDormRoomRequestDtoInvalid() {
        // Arrange
        CreateDormRoomRequestDto dto = CreateDormRoomRequestDto.builder()
                .roomNumber("")
                .floor(-1)
                .capacity(0)
                .build();

        // Act
        Set<ConstraintViolation<CreateDormRoomRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(3, violations.size());
    }

    @Test
    @DisplayName("UpdateDormRoomRequestDto passes with valid null or updated values")
    void updateDormRoomRequestDtoValid() {
        // Arrange
        UpdateDormRoomRequestDto emptyDto = new UpdateDormRoomRequestDto();
        UpdateDormRoomRequestDto updatedDto = UpdateDormRoomRequestDto.builder()
                .roomNumber("205")
                .floor(2)
                .capacity(3)
                .build();

        // Act
        Set<ConstraintViolation<UpdateDormRoomRequestDto>> emptyViolations = validator.validate(emptyDto);
        Set<ConstraintViolation<UpdateDormRoomRequestDto>> updatedViolations = validator.validate(updatedDto);

        // Assert
        assertTrue(emptyViolations.isEmpty());
        assertTrue(updatedViolations.isEmpty());
    }

    @Test
    @DisplayName("UpdateDormRoomRequestDto fails on negative floor or capacity out of range")
    void updateDormRoomRequestDtoInvalid() {
        // Arrange
        UpdateDormRoomRequestDto dto = UpdateDormRoomRequestDto.builder()
                .roomNumber("12345678901")
                .floor(51)
                .capacity(11)
                .build();

        // Act
        Set<ConstraintViolation<UpdateDormRoomRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(3, violations.size());
    }
}

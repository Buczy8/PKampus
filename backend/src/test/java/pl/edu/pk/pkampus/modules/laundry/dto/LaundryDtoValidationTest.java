package pl.edu.pk.pkampus.modules.laundry.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineStatus;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Laundry DTO validation unit tests (AAA)")
class LaundryDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("CreateLaundryBookingRequestDto passes with valid data")
    void createLaundryBookingRequestDtoValid() {
        // Arrange
        CreateLaundryBookingRequestDto dto = new CreateLaundryBookingRequestDto(
                UUID.randomUUID(),
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusHours(4)
        );

        // Act
        Set<ConstraintViolation<CreateLaundryBookingRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateLaundryBookingRequestDto fails when required fields are null")
    void createLaundryBookingRequestDtoNullFields() {
        // Arrange
        CreateLaundryBookingRequestDto dto = new CreateLaundryBookingRequestDto(
                null,
                null,
                null
        );

        // Act
        Set<ConstraintViolation<CreateLaundryBookingRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(3, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("machineId")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("startTime")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("endTime")));
    }

    @Test
    @DisplayName("CreateLaundryMachineRequestDto passes with valid data")
    void createLaundryMachineRequestDtoValid() {
        // Arrange
        CreateLaundryMachineRequestDto dto = CreateLaundryMachineRequestDto.builder()
                .machineIdentifier("Pralka 1")
                .floorLocation("Pralnia poziom -1")
                .build();

        // Act
        Set<ConstraintViolation<CreateLaundryMachineRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateLaundryMachineRequestDto fails on blank fields")
    void createLaundryMachineRequestDtoBlankFields() {
        // Arrange
        CreateLaundryMachineRequestDto dto = CreateLaundryMachineRequestDto.builder()
                .machineIdentifier("   ")
                .floorLocation("")
                .build();

        // Act
        Set<ConstraintViolation<CreateLaundryMachineRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("machineIdentifier")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("floorLocation")));
    }

    @Test
    @DisplayName("CreateLaundryMachineRequestDto fails on fields exceeding max length")
    void createLaundryMachineRequestDtoTooLong() {
        // Arrange
        CreateLaundryMachineRequestDto dto = CreateLaundryMachineRequestDto.builder()
                .machineIdentifier("A".repeat(31))
                .floorLocation("B".repeat(51))
                .build();

        // Act
        Set<ConstraintViolation<CreateLaundryMachineRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("machineIdentifier")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("floorLocation")));
    }

    @Test
    @DisplayName("UpdateLaundryMachineRequestDto passes with valid or null fields")
    void updateLaundryMachineRequestDtoValid() {
        // Arrange
        UpdateLaundryMachineRequestDto dto = UpdateLaundryMachineRequestDto.builder()
                .machineIdentifier("Nowa nazwa")
                .floorLocation(null)
                .status(LaundryMachineStatus.OUT_OF_ORDER)
                .build();

        // Act
        Set<ConstraintViolation<UpdateLaundryMachineRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("UpdateLaundryMachineRequestDto fails on fields exceeding max length")
    void updateLaundryMachineRequestDtoTooLong() {
        // Arrange
        UpdateLaundryMachineRequestDto dto = UpdateLaundryMachineRequestDto.builder()
                .machineIdentifier("A".repeat(31))
                .floorLocation("B".repeat(51))
                .build();

        // Act
        Set<ConstraintViolation<UpdateLaundryMachineRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("machineIdentifier")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("floorLocation")));
    }
}

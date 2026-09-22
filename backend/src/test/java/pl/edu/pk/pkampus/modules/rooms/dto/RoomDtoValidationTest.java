package pl.edu.pk.pkampus.modules.rooms.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Room DTO validation unit tests (AAA)")
class RoomDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("CreateThematicRoomRequestDto passes with valid data")
    void createThematicRoomRequestDtoValid() {
        // Arrange
        CreateThematicRoomRequestDto dto = CreateThematicRoomRequestDto.builder()
                .name("Salka Cichej Nauki")
                .maxCapacity(15)
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(22, 0))
                .spansMidnight(false)
                .maxDurationHours(4)
                .description("Ciche miejsce do nauki indywidualnej")
                .build();

        // Act
        Set<ConstraintViolation<CreateThematicRoomRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateThematicRoomRequestDto fails on missing or invalid fields")
    void createThematicRoomRequestDtoInvalid() {
        // Arrange
        CreateThematicRoomRequestDto dto = CreateThematicRoomRequestDto.builder()
                .name("   ")
                .maxCapacity(0) // min 1
                .openingTime(null)
                .closingTime(null)
                .maxDurationHours(25) // max 24
                .build();

        // Act
        Set<ConstraintViolation<CreateThematicRoomRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(5, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("maxCapacity")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("openingTime")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("closingTime")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("maxDurationHours")));
    }

    @Test
    @DisplayName("UpdateThematicRoomRequestDto passes with valid or null fields")
    void updateThematicRoomRequestDtoValid() {
        // Arrange
        UpdateThematicRoomRequestDto dto = UpdateThematicRoomRequestDto.builder()
                .name("Zaktualizowana nazwa")
                .maxCapacity(20)
                .maxDurationHours(6)
                .build();

        // Act
        Set<ConstraintViolation<UpdateThematicRoomRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("UpdateThematicRoomRequestDto fails on bounds violation")
    void updateThematicRoomRequestDtoInvalid() {
        // Arrange
        UpdateThematicRoomRequestDto dto = UpdateThematicRoomRequestDto.builder()
                .maxCapacity(250) // max 200
                .maxDurationHours(0) // min 1
                .build();

        // Act
        Set<ConstraintViolation<UpdateThematicRoomRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("maxCapacity")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("maxDurationHours")));
    }

    @Test
    @DisplayName("CreateRoomBookingRequestDto passes with valid data")
    void createRoomBookingRequestDtoValid() {
        // Arrange
        CreateRoomBookingRequestDto dto = new CreateRoomBookingRequestDto(
                UUID.randomUUID(),
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusHours(3),
                5,
                "Projekt zespołowy",
                true
        );

        // Act
        Set<ConstraintViolation<CreateRoomBookingRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateRoomBookingRequestDto fails on invalid fields and rejected terms")
    void createRoomBookingRequestDtoInvalid() {
        // Arrange
        CreateRoomBookingRequestDto dto = new CreateRoomBookingRequestDto(
                null,
                null,
                null,
                0, // min 1
                "  ", // blank
                false // must be true
        );

        // Act
        Set<ConstraintViolation<CreateRoomBookingRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(6, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("roomId")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("startTime")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("endTime")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("participantsCount")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("purpose")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("termsAccepted")));
    }
}

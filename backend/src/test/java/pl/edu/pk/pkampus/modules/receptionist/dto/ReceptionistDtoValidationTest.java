package pl.edu.pk.pkampus.modules.receptionist.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.issues.IssueStatus;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Receptionist DTO validation unit tests (AAA)")
class ReceptionistDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("MachineBreakdownRequestDto passes with valid reason")
    void machineBreakdownRequestDtoValid() {
        // Arrange
        MachineBreakdownRequestDto dto = new MachineBreakdownRequestDto("Awaria bębna, głośne stuki.");

        // Act
        Set<ConstraintViolation<MachineBreakdownRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("MachineBreakdownRequestDto fails when reason is blank or exceeds max size")
    void machineBreakdownRequestDtoInvalid() {
        // Arrange
        MachineBreakdownRequestDto blankDto = new MachineBreakdownRequestDto("   ");
        MachineBreakdownRequestDto tooLongDto = new MachineBreakdownRequestDto("A".repeat(2001));

        // Act
        Set<ConstraintViolation<MachineBreakdownRequestDto>> blankViolations = validator.validate(blankDto);
        Set<ConstraintViolation<MachineBreakdownRequestDto>> tooLongViolations = validator.validate(tooLongDto);

        // Assert
        assertEquals(1, blankViolations.size());
        assertTrue(blankViolations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("reason")));

        assertEquals(1, tooLongViolations.size());
        assertTrue(tooLongViolations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("reason")));
    }

    @Test
    @DisplayName("RoomMaintenanceRequestDto passes with valid reason")
    void roomMaintenanceRequestDtoValid() {
        // Arrange
        RoomMaintenanceRequestDto dto = new RoomMaintenanceRequestDto("Malowanie ścian i naprawa zamka.");

        // Act
        Set<ConstraintViolation<RoomMaintenanceRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("RoomMaintenanceRequestDto fails when reason is blank or exceeds max size")
    void roomMaintenanceRequestDtoInvalid() {
        // Arrange
        RoomMaintenanceRequestDto blankDto = new RoomMaintenanceRequestDto("");
        RoomMaintenanceRequestDto tooLongDto = new RoomMaintenanceRequestDto("B".repeat(2001));

        // Act
        Set<ConstraintViolation<RoomMaintenanceRequestDto>> blankViolations = validator.validate(blankDto);
        Set<ConstraintViolation<RoomMaintenanceRequestDto>> tooLongViolations = validator.validate(tooLongDto);

        // Assert
        assertEquals(1, blankViolations.size());
        assertEquals(1, tooLongViolations.size());
    }

    @Test
    @DisplayName("UpdateIssueStatusRequestDto passes with valid data")
    void updateIssueStatusRequestDtoValid() {
        // Arrange
        UpdateIssueStatusRequestDto dto = new UpdateIssueStatusRequestDto(
                IssueStatus.IN_PROGRESS,
                "Hydraulik zamówiony na jutro rano."
        );

        // Act
        Set<ConstraintViolation<UpdateIssueStatusRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("UpdateIssueStatusRequestDto fails when status is null or staffNotes too long")
    void updateIssueStatusRequestDtoInvalid() {
        // Arrange
        UpdateIssueStatusRequestDto nullStatusDto = new UpdateIssueStatusRequestDto(
                null,
                "Notatki"
        );
        UpdateIssueStatusRequestDto tooLongNotesDto = new UpdateIssueStatusRequestDto(
                IssueStatus.RESOLVED,
                "C".repeat(4001)
        );

        // Act
        Set<ConstraintViolation<UpdateIssueStatusRequestDto>> nullViolations = validator.validate(nullStatusDto);
        Set<ConstraintViolation<UpdateIssueStatusRequestDto>> tooLongViolations = validator.validate(tooLongNotesDto);

        // Assert
        assertEquals(1, nullViolations.size());
        assertTrue(nullViolations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("status")));

        assertEquals(1, tooLongViolations.size());
        assertTrue(tooLongViolations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("staffNotes")));
    }
}

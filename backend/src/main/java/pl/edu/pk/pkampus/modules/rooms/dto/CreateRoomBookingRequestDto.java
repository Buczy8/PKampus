package pl.edu.pk.pkampus.modules.rooms.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateRoomBookingRequestDto(
        @NotNull UUID roomId,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        @NotNull @Min(1) @Max(100) Integer participantsCount,
        @NotBlank @Size(max = 255) String purpose,
        @NotNull @AssertTrue(message = "Terms must be accepted") Boolean termsAccepted
) {
}

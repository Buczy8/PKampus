package pl.edu.pk.pkampus.modules.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivateResidentRequestDto {

    /**
     * Optional override of declared room number when ADS confirms a different room.
     */
    @Size(max = 10, message = "Room number may be at most 10 characters")
    private String roomNumber;
}

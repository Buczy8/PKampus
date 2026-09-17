package pl.edu.pk.pkampus.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectResidentRequestDto {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 1000, message = "Rejection reason may be at most 1000 characters")
    private String reason;
}

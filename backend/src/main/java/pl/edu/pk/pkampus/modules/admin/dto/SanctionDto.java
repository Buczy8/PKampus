package pl.edu.pk.pkampus.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.sanctions.SanctionType;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionDto {

    private UUID id;
    private UUID userId;
    private SanctionType sanctionType;
    private String reason;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean active;
}

package pl.edu.pk.pkampus.modules.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.events.DormEventCategory;
import pl.edu.pk.pkampus.modules.events.DormEventPriority;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCampusEventRequestDto {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private DormEventCategory category;

    @NotNull
    private DormEventPriority priority;

    private Boolean pinned;

    @NotNull
    private Instant eventDate;

    private Instant endDate;
}

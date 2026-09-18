package pl.edu.pk.pkampus.modules.superadmin.dto;

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
public class UpdateCampusEventRequestDto {

    @Size(max = 200)
    private String title;

    private String description;

    private DormEventCategory category;

    private DormEventPriority priority;

    private Boolean pinned;

    private Instant eventDate;

    private Instant endDate;
}

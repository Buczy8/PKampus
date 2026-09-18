package pl.edu.pk.pkampus.modules.events.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.events.DormEventPriority;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDormEventRequestDto {

    @Size(max = 200)
    private String title;

    private String description;

    private DormEventPriority priority;

    private Instant eventDate;

    private Instant endDate;
}

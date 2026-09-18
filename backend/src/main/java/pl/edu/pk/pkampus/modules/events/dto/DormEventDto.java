package pl.edu.pk.pkampus.modules.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.events.DormEventCategory;
import pl.edu.pk.pkampus.modules.events.DormEventPriority;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DormEventDto {

    private UUID id;
    private UUID authorId;
    private String authorName;
    private UUID dormitoryId;
    private String title;
    private String description;
    private DormEventCategory category;
    private DormEventPriority priority;
    private boolean pinned;
    private Instant eventDate;
    private Instant endDate;
    private Instant createdAt;
}

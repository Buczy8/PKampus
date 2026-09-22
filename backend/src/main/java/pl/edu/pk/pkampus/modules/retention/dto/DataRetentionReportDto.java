package pl.edu.pk.pkampus.modules.retention.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary report produced after running GDPR / RODO data retention procedures")
public class DataRetentionReportDto {

    @Schema(description = "Timestamp when retention procedure completed")
    private Instant executedAt;

    @Schema(description = "Number of MinIO issue photo files and IssuePhoto records deleted (issues RESOLVED/REJECTED > 30 days)")
    private int issuePhotosRemovedCount;

    @Schema(description = "Number of board posts permanently removed (RESOLVED > 30 days or deleted/moderated > 14 days)")
    private int postsRemovedCount;

    @Schema(description = "Number of standalone soft-deleted comments permanently removed (> 14 days)")
    private int commentsRemovedCount;

    @Schema(description = "Number of completed/cancelled laundry bookings purged (> 90 days)")
    private int laundryBookingsPurgedCount;

    @Schema(description = "Number of completed/cancelled room bookings purged (> 90 days)")
    private int roomBookingsPurgedCount;

    @Schema(description = "Number of checked-out student accounts anonymized (> 365 days after checkout)")
    private int usersAnonymizedCount;

    @Schema(description = "Total execution duration in milliseconds")
    private long executionDurationMs;
}

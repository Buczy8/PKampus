package pl.edu.pk.pkampus.modules.retention;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.retention.dto.DataRetentionReportDto;

@RestController
@RequestMapping("/api/v1/superadmin/retention")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Data Retention", description = "GDPR / RODO data retention procedures (§12)")
public class SuperAdminRetentionController {

    private final DataRetentionService dataRetentionService;

    @PostMapping("/run")
    @Operation(summary = "Execute GDPR / RODO data retention tasks manually and return execution report")
    public ResponseEntity<ApiResponse<DataRetentionReportDto>> runRetention() {
        DataRetentionReportDto report = dataRetentionService.runRetentionTasks();
        return ResponseEntity.ok(ApiResponse.ok(report, "GDPR / RODO data retention completed successfully"));
    }
}

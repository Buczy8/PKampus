package pl.edu.pk.pkampus.modules.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.admin.dto.ActivateResidentRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.ActivateResidentResponseDto;
import pl.edu.pk.pkampus.modules.admin.dto.PendingResidentDto;
import pl.edu.pk.pkampus.modules.admin.dto.RejectResidentRequestDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/residents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DORM_ADMIN', 'SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin — Residents", description = "Dormitory admin residency verification (FR-AUTH-02 / UC-AUTH-03)")
public class AdminResidentController {

    private final AdminResidentService adminResidentService;

    @GetMapping("/pending")
    @Operation(
            summary = "List pending residency applications",
            description = "Returns PENDING_APPROVAL residents for the administrator's dormitory, including declared room and MinIO avatar URL."
    )
    public ResponseEntity<ApiResponse<List<PendingResidentDto>>> listPending(
            @AuthenticationPrincipal User admin
    ) {
        List<PendingResidentDto> pending = adminResidentService.listPendingResidents(admin);
        return ResponseEntity.ok(ApiResponse.ok(pending));
    }

    @PostMapping("/{id}/activate")
    @Operation(
            summary = "Approve residency (activate account)",
            description = "Sets status ACTIVE, creates room_assignments for the current academic year, and emails the resident."
    )
    public ResponseEntity<ApiResponse<ActivateResidentResponseDto>> activate(
            @AuthenticationPrincipal User admin,
            @PathVariable("id") UUID residentId,
            @RequestBody(required = false) @Valid ActivateResidentRequestDto request
    ) {
        ActivateResidentResponseDto response = adminResidentService.activateResident(
                admin,
                residentId,
                request != null ? request : new ActivateResidentRequestDto()
        );
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMessage()));
    }

    @PostMapping("/{id}/reject")
    @Operation(
            summary = "Reject residency application",
            description = "Deletes the temporary account and avatar from MinIO, then emails the applicant with the rejection reason."
    )
    public ResponseEntity<ApiResponse<Void>> reject(
            @AuthenticationPrincipal User admin,
            @PathVariable("id") UUID residentId,
            @Valid @RequestBody RejectResidentRequestDto request
    ) {
        adminResidentService.rejectResident(admin, residentId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Residency application rejected"));
    }
}

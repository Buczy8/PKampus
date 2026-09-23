package pl.edu.pk.pkampus.modules.admin.receptionists;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.admin.dto.CreateReceptionistRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.ReceptionistDto;
import pl.edu.pk.pkampus.modules.admin.dto.UpdateReceptionistRequestDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/receptionists")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DORM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin — Receptionists", description = "Dormitory receptionist accounts (FR-AUTH-05 / UC-ADM-04)")
public class AdminReceptionistController {

    private final AdminReceptionistService adminReceptionistService;

    @GetMapping
    @Operation(summary = "List receptionists for the admin's dormitory")
    public ResponseEntity<ApiResponse<List<ReceptionistDto>>> list(@AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(ApiResponse.ok(adminReceptionistService.list(admin)));
    }

    @PostMapping
    @Operation(summary = "Create a receptionist account in the admin's dormitory")
    public ResponseEntity<ApiResponse<ReceptionistDto>> create(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody CreateReceptionistRequestDto request
    ) {
        ReceptionistDto created = adminReceptionistService.create(admin, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Receptionist account created"));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a receptionist in the admin's dormitory")
    public ResponseEntity<ApiResponse<ReceptionistDto>> update(
            @AuthenticationPrincipal User admin,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReceptionistRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminReceptionistService.update(admin, id, request)));
    }
}

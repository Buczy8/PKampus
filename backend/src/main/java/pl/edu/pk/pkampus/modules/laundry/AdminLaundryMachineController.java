package pl.edu.pk.pkampus.modules.laundry;

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
import pl.edu.pk.pkampus.modules.laundry.dto.AdminLaundryMachineDto;
import pl.edu.pk.pkampus.modules.laundry.dto.CreateLaundryMachineRequestDto;
import pl.edu.pk.pkampus.modules.laundry.dto.UpdateLaundryMachineRequestDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/laundry-machines")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DORM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin — Laundry Machines", description = "Dormitory laundry machine configuration (FR-LAUND-01 / UC-ADM-01)")
public class AdminLaundryMachineController {

    private final AdminLaundryMachineService adminLaundryMachineService;

    @GetMapping
    @Operation(summary = "List laundry machines for the admin's dormitory")
    public ResponseEntity<ApiResponse<List<AdminLaundryMachineDto>>> list(@AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(ApiResponse.ok(adminLaundryMachineService.listForAdmin(admin)));
    }

    @PostMapping
    @Operation(summary = "Create a laundry machine in the admin's dormitory")
    public ResponseEntity<ApiResponse<AdminLaundryMachineDto>> create(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody CreateLaundryMachineRequestDto request
    ) {
        AdminLaundryMachineDto created = adminLaundryMachineService.create(admin, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Laundry machine created"));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a laundry machine in the admin's dormitory")
    public ResponseEntity<ApiResponse<AdminLaundryMachineDto>> update(
            @AuthenticationPrincipal User admin,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLaundryMachineRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminLaundryMachineService.update(admin, id, request)));
    }
}

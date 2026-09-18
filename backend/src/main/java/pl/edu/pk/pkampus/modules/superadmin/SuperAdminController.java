package pl.edu.pk.pkampus.modules.superadmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.CreateCampusEventRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.CreateDormAdminRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.CreateDormitoryRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.DormAdminDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.SuperAdminDormitoryDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.UpdateCampusEventRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.UpdateDormAdminRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.UpdateDormitoryRequestDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/superadmin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Super Admin", description = "Campus-wide administration (FR-PORTAL-05, UC-ADM-05, UC-ADM-06, UC-EVT-03)")
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    @GetMapping("/dormitories")
    @Operation(summary = "List dormitories with laundry parameters")
    public ResponseEntity<ApiResponse<List<SuperAdminDormitoryDto>>> listDormitories() {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.listDormitories()));
    }

    @PostMapping("/dormitories")
    @Operation(summary = "Create dormitory")
    public ResponseEntity<ApiResponse<SuperAdminDormitoryDto>> createDormitory(
            @Valid @RequestBody CreateDormitoryRequestDto request
    ) {
        SuperAdminDormitoryDto created = superAdminService.createDormitory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Dormitory created"));
    }

    @PatchMapping("/dormitories/{id}")
    @Operation(summary = "Update dormitory")
    public ResponseEntity<ApiResponse<SuperAdminDormitoryDto>> updateDormitory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDormitoryRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.updateDormitory(id, request)));
    }

    @GetMapping("/dorm-admins")
    @Operation(summary = "List dormitory administrators")
    public ResponseEntity<ApiResponse<List<DormAdminDto>>> listDormAdmins() {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.listDormAdmins()));
    }

    @PostMapping("/dorm-admins")
    @Operation(summary = "Create dormitory administrator account")
    public ResponseEntity<ApiResponse<DormAdminDto>> createDormAdmin(
            @Valid @RequestBody CreateDormAdminRequestDto request
    ) {
        DormAdminDto created = superAdminService.createDormAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Dormitory administrator created"));
    }

    @PatchMapping("/dorm-admins/{id}")
    @Operation(summary = "Update dormitory administrator (status, dormitory, profile)")
    public ResponseEntity<ApiResponse<DormAdminDto>> updateDormAdmin(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDormAdminRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.updateDormAdmin(id, request)));
    }

    @GetMapping("/events")
    @Operation(summary = "List campus-wide official notices")
    public ResponseEntity<ApiResponse<List<DormEventDto>>> listCampusEvents() {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.listCampusEvents()));
    }

    @PostMapping("/events")
    @Operation(summary = "Publish campus-wide official notice")
    public ResponseEntity<ApiResponse<DormEventDto>> createCampusEvent(
            @AuthenticationPrincipal User author,
            @Valid @RequestBody CreateCampusEventRequestDto request
    ) {
        DormEventDto created = superAdminService.createCampusEvent(author, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Campus notice published"));
    }

    @PatchMapping("/events/{id}")
    @Operation(summary = "Update campus-wide official notice")
    public ResponseEntity<ApiResponse<DormEventDto>> updateCampusEvent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCampusEventRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.updateCampusEvent(id, request)));
    }

    @DeleteMapping("/events/{id}")
    @Operation(summary = "Delete campus-wide official notice")
    public ResponseEntity<ApiResponse<Void>> deleteCampusEvent(@PathVariable UUID id) {
        superAdminService.deleteCampusEvent(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Campus notice deleted"));
    }
}

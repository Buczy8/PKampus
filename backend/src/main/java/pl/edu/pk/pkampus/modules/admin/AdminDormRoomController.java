package pl.edu.pk.pkampus.modules.admin;

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
import pl.edu.pk.pkampus.modules.admin.dto.CreateDormRoomRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.DormRoomDto;
import pl.edu.pk.pkampus.modules.admin.dto.UpdateDormRoomRequestDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/dorm-rooms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DORM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin — Dorm Rooms", description = "Residential rooms in the dormitory (meldunek / room_assignments)")
public class AdminDormRoomController {

    private final AdminDormRoomService adminDormRoomService;

    @GetMapping
    @Operation(summary = "List residential rooms for the admin's dormitory")
    public ResponseEntity<ApiResponse<List<DormRoomDto>>> list(@AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(ApiResponse.ok(adminDormRoomService.list(admin)));
    }

    @PostMapping
    @Operation(summary = "Create a residential room in the admin's dormitory")
    public ResponseEntity<ApiResponse<DormRoomDto>> create(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody CreateDormRoomRequestDto request
    ) {
        DormRoomDto created = adminDormRoomService.create(admin, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Room created"));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a residential room in the admin's dormitory")
    public ResponseEntity<ApiResponse<DormRoomDto>> update(
            @AuthenticationPrincipal User admin,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDormRoomRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminDormRoomService.update(admin, id, request)));
    }
}

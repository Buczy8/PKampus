package pl.edu.pk.pkampus.modules.rooms;

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
import pl.edu.pk.pkampus.modules.rooms.dto.CreateThematicRoomRequestDto;
import pl.edu.pk.pkampus.modules.rooms.dto.ThematicRoomDto;
import pl.edu.pk.pkampus.modules.rooms.dto.UpdateThematicRoomRequestDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/thematic-rooms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DORM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin — Thematic Rooms", description = "Dormitory admin thematic room configuration (FR-ROOM-01 / UC-ADM-02)")
public class AdminThematicRoomController {

    private final ThematicRoomService thematicRoomService;

    @GetMapping
    @Operation(summary = "List thematic rooms for the admin's dormitory")
    public ResponseEntity<ApiResponse<List<ThematicRoomDto>>> list(@AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(ApiResponse.ok(thematicRoomService.listForAdmin(admin)));
    }

    @PostMapping
    @Operation(summary = "Create a thematic room in the admin's dormitory")
    public ResponseEntity<ApiResponse<ThematicRoomDto>> create(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody CreateThematicRoomRequestDto request
    ) {
        ThematicRoomDto created = thematicRoomService.create(admin, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Thematic room created"));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a thematic room in the admin's dormitory")
    public ResponseEntity<ApiResponse<ThematicRoomDto>> update(
            @AuthenticationPrincipal User admin,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateThematicRoomRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(thematicRoomService.update(admin, id, request)));
    }
}

package pl.edu.pk.pkampus.modules.events;

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
import pl.edu.pk.pkampus.modules.events.dto.CreateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
import pl.edu.pk.pkampus.modules.events.dto.UpdateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DORM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin — Events", description = "Dormitory-scoped official notices (FR-EVENT-01)")
public class AdminDormEventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "List official notices for the admin's dormitory")
    public ResponseEntity<ApiResponse<List<DormEventDto>>> list(@AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.listForAdmin(admin)));
    }

    @PostMapping
    @Operation(summary = "Publish an official notice for the admin's dormitory")
    public ResponseEntity<ApiResponse<DormEventDto>> create(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody CreateDormEventRequestDto request
    ) {
        DormEventDto created = eventService.createForAdmin(admin, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Dorm notice published"));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an official notice in the admin's dormitory")
    public ResponseEntity<ApiResponse<DormEventDto>> update(
            @AuthenticationPrincipal User admin,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDormEventRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.updateForAdmin(admin, id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an official notice in the admin's dormitory")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User admin,
            @PathVariable UUID id
    ) {
        eventService.deleteForAdmin(admin, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Dorm notice deleted"));
    }
}

package pl.edu.pk.pkampus.modules.events;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Events", description = "Official campus and dormitory notices (FR-EVENT-01 / FR-EVENT-02)")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(
            summary = "Official notices feed",
            description = "Returns dormitory-scoped and campus-wide official notices visible to the caller."
    )
    public ResponseEntity<ApiResponse<List<DormEventDto>>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.listVisible(user)));
    }

    @GetMapping("/banner")
    @Operation(
            summary = "Active pinned CRITICAL banner",
            description = "Returns the highest-priority active pinned CRITICAL notice for the caller's dormitory or campus-wide."
    )
    public ResponseEntity<ApiResponse<DormEventDto>> getBanner(@AuthenticationPrincipal User user) {
        return eventService.findActiveBanner(user)
                .map(dto -> ResponseEntity.ok(ApiResponse.ok(dto)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok(null)));
    }
}

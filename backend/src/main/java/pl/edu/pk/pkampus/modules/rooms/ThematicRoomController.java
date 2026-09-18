package pl.edu.pk.pkampus.modules.rooms;

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
import pl.edu.pk.pkampus.modules.rooms.dto.ThematicRoomDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Thematic Rooms", description = "Resident catalog of thematic rooms (FR-ROOM-01)")
public class ThematicRoomController {

    private final ThematicRoomService thematicRoomService;

    @GetMapping
    @Operation(summary = "List available thematic rooms in the resident's dormitory")
    public ResponseEntity<ApiResponse<List<ThematicRoomDto>>> listCatalog(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(thematicRoomService.listAvailableForResident(user)));
    }
}

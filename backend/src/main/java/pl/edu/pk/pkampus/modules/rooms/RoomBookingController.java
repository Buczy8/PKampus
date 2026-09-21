package pl.edu.pk.pkampus.modules.rooms;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.rooms.dto.CreateRoomBookingRequestDto;
import pl.edu.pk.pkampus.modules.rooms.dto.RoomAvailabilityDto;
import pl.edu.pk.pkampus.modules.rooms.dto.RoomBookingDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Room Bookings", description = "Resident thematic room reservations (FR-ROOM-03 / UC-ROOM-01)")
public class RoomBookingController {

    private final RoomBookingService roomBookingService;

    @GetMapping("/{id}/availability")
    @Operation(summary = "Busy intervals for a thematic room")
    public ResponseEntity<ApiResponse<RoomAvailabilityDto>> availability(
            @AuthenticationPrincipal User user,
            @PathVariable("id") UUID roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(roomBookingService.availability(user, roomId, from, to)));
    }

    @GetMapping("/bookings/me")
    @Operation(summary = "My active thematic room bookings")
    public ResponseEntity<ApiResponse<List<RoomBookingDto>>> myBookings(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(roomBookingService.listMyBookings(user)));
    }

    @PostMapping("/bookings")
    @Operation(summary = "Create a thematic room booking")
    public ResponseEntity<ApiResponse<RoomBookingDto>> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateRoomBookingRequestDto request
    ) {
        RoomBookingDto created = roomBookingService.createBooking(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Room booking confirmed"));
    }

    @DeleteMapping("/bookings/{id}")
    @Operation(summary = "Cancel own thematic room booking before start")
    public ResponseEntity<ApiResponse<RoomBookingDto>> cancel(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                roomBookingService.cancelBooking(user, id),
                "Room booking cancelled"));
    }
}

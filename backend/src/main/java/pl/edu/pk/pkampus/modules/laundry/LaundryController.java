package pl.edu.pk.pkampus.modules.laundry;

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
import pl.edu.pk.pkampus.modules.laundry.dto.CreateLaundryBookingRequestDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryBookingDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryScheduleResponseDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/laundry")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Laundry", description = "Resident laundry schedule and bookings (FR-LAUND-02..04)")
public class LaundryController {

    private final LaundryService laundryService;

    @GetMapping("/schedule")
    @Operation(
            summary = "Laundry slot schedule",
            description = "Returns machines and generated slots for the caller's dormitory (up to 7 days ahead, Europe/Warsaw)."
    )
    public ResponseEntity<ApiResponse<LaundryScheduleResponseDto>> getSchedule(
            @AuthenticationPrincipal User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(laundryService.getSchedule(user, from, to)));
    }

    @GetMapping("/bookings/me")
    @Operation(summary = "My active laundry bookings", description = "CONFIRMED and KEY_ISSUED bookings for the current resident.")
    public ResponseEntity<ApiResponse<List<LaundryBookingDto>>> myBookings(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(laundryService.listMyBookings(user)));
    }

    @PostMapping("/bookings")
    @Operation(
            summary = "Book a laundry slot",
            description = "Creates a CONFIRMED booking. Enforces one booking per day, max 2 in a 7-day window from the reservation date, and overlap conflict (HTTP 409)."
    )
    public ResponseEntity<ApiResponse<LaundryBookingDto>> createBooking(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateLaundryBookingRequestDto request
    ) {
        LaundryBookingDto booking = laundryService.bookSlot(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(booking, "Laundry booking confirmed"));
    }

    @DeleteMapping("/bookings/{id}")
    @Operation(
            summary = "Cancel own laundry booking",
            description = "Cancels a CONFIRMED booking before start_time (FR-LAUND-04)."
    )
    public ResponseEntity<ApiResponse<LaundryBookingDto>> cancelBooking(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        LaundryBookingDto booking = laundryService.cancelBooking(user, id);
        return ResponseEntity.ok(ApiResponse.ok(booking, "Laundry booking cancelled"));
    }
}

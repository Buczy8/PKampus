package pl.edu.pk.pkampus.modules.receptionist;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.common.PagedResponse;
import pl.edu.pk.pkampus.modules.board.PostCategory;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.events.dto.CreateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
import pl.edu.pk.pkampus.modules.events.dto.UpdateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.issues.IssueCategory;
import pl.edu.pk.pkampus.modules.issues.IssueStatus;
import pl.edu.pk.pkampus.modules.issues.IssueUrgency;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryScheduleResponseDto;
import pl.edu.pk.pkampus.modules.profile.ProfileCardService;
import pl.edu.pk.pkampus.modules.profile.dto.CardDayDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskLaundryBookingDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskLaundryMachineDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskRoomBookingDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskThematicRoomDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.MachineBreakdownRequestDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.MachineBreakdownResponseDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.ReceptionistDeskDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.RoomMaintenanceRequestDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.RoomMaintenanceResponseDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.StaffIssueDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.StaffRoomScheduleResponseDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.UpdateIssueStatusRequestDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/receptionist")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'DORM_ADMIN', 'SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Receptionist Desk", description = "Porter desk, laundry moderation, key handling (FR-PORTAL / FR-LAUND-05/06)")
public class ReceptionistController {

    private final ReceptionistService receptionistService;
    private final ProfileCardService profileCardService;

    @GetMapping("/desk")
    @Operation(summary = "Today's desk snapshot for the porter's dormitory")
    public ResponseEntity<ApiResponse<ReceptionistDeskDto>> desk(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(receptionistService.getDesk(user)));
    }

    @GetMapping("/card-day")
    @Operation(summary = "Today's card verification code and color for visual checks at the desk")
    public ResponseEntity<ApiResponse<CardDayDto>> cardDay() {
        return ResponseEntity.ok(ApiResponse.ok(profileCardService.getCardDay()));
    }

    @GetMapping("/laundry/schedule")
    @Operation(summary = "Laundry schedule for staff (with booking labels on occupied slots)")
    public ResponseEntity<ApiResponse<LaundryScheduleResponseDto>> laundrySchedule(
            @AuthenticationPrincipal User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(receptionistService.getLaundrySchedule(user, from, to)));
    }

    @PostMapping("/laundry/{id}/cancel")
    @Operation(summary = "Cancel a CONFIRMED laundry booking in the porter's dormitory")
    public ResponseEntity<ApiResponse<DeskLaundryBookingDto>> cancelLaundry(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.cancelLaundryBooking(user, id),
                "Laundry booking cancelled"));
    }

    @PostMapping("/laundry/machines/{id}/breakdown")
    @Operation(summary = "Mark laundry machine OUT_OF_ORDER (ADR-06 cascade + auto-issue)")
    public ResponseEntity<ApiResponse<MachineBreakdownResponseDto>> machineBreakdown(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody MachineBreakdownRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.reportMachineBreakdown(user, id, request.reason()),
                "Laundry machine marked out of order"));
    }

    @PostMapping("/laundry/machines/{id}/restore")
    @Operation(summary = "Restore laundry machine to AVAILABLE")
    public ResponseEntity<ApiResponse<DeskLaundryMachineDto>> restoreMachine(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.restoreMachine(user, id),
                "Laundry machine restored"));
    }

    @PostMapping("/laundry/{id}/issue-key")
    @Operation(summary = "Issue laundry key (CONFIRMED → KEY_ISSUED)")
    public ResponseEntity<ApiResponse<DeskLaundryBookingDto>> issueLaundryKey(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.issueLaundryKey(user, id),
                "Laundry key issued"));
    }

    @PostMapping("/laundry/{id}/return-key")
    @Operation(summary = "Return laundry key (KEY_ISSUED → COMPLETED)")
    public ResponseEntity<ApiResponse<DeskLaundryBookingDto>> returnLaundryKey(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.returnLaundryKey(user, id),
                "Laundry key returned"));
    }

    @GetMapping("/rooms/schedule")
    @Operation(summary = "Thematic room schedule for staff (bookings with resident labels)")
    public ResponseEntity<ApiResponse<StaffRoomScheduleResponseDto>> roomSchedule(
            @AuthenticationPrincipal User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(receptionistService.getRoomSchedule(user, from, to)));
    }

    @PostMapping("/rooms/{id}/cancel")
    @Operation(summary = "Cancel a CONFIRMED thematic room booking")
    public ResponseEntity<ApiResponse<DeskRoomBookingDto>> cancelRoom(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.cancelRoomBooking(user, id),
                "Room booking cancelled"));
    }

    @PostMapping("/rooms/{id}/maintenance")
    @Operation(summary = "Mark thematic room MAINTENANCE (ADR-06 cascade + auto-issue)")
    public ResponseEntity<ApiResponse<RoomMaintenanceResponseDto>> roomMaintenance(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody RoomMaintenanceRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.reportRoomMaintenance(user, id, request.reason()),
                "Thematic room marked under maintenance"));
    }

    @PostMapping("/rooms/{id}/restore")
    @Operation(summary = "Restore thematic room to AVAILABLE")
    public ResponseEntity<ApiResponse<DeskThematicRoomDto>> restoreRoom(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.restoreRoom(user, id),
                "Thematic room restored"));
    }

    @PostMapping("/rooms/{id}/issue-key")
    @Operation(summary = "Issue thematic room key (CONFIRMED → KEY_ISSUED)")
    public ResponseEntity<ApiResponse<DeskRoomBookingDto>> issueRoomKey(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.issueRoomKey(user, id),
                "Room key issued"));
    }

    @PostMapping("/rooms/{id}/return-key")
    @Operation(summary = "Return thematic room key (KEY_ISSUED → COMPLETED)")
    public ResponseEntity<ApiResponse<DeskRoomBookingDto>> returnRoomKey(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.returnRoomKey(user, id),
                "Room key returned"));
    }

    @GetMapping("/issues")
    @Operation(summary = "List maintenance issues for the porter's dormitory (FR-ISSUE-03)")
    public ResponseEntity<ApiResponse<List<StaffIssueDto>>> listIssues(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) List<IssueStatus> status,
            @RequestParam(required = false) IssueCategory category,
            @RequestParam(required = false) IssueUrgency urgency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String roomNumber,
            @RequestParam(required = false) Integer floor
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.listIssues(
                        user, status, category, urgency, from, to, roomNumber, floor)));
    }

    @GetMapping("/issues/{id}")
    @Operation(summary = "Issue details with presigned photo URL")
    public ResponseEntity<ApiResponse<StaffIssueDto>> getIssue(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(receptionistService.getIssue(user, id)));
    }

    @PatchMapping("/issues/{id}/status")
    @Operation(summary = "Update issue status and staff notes (FR-ISSUE-04)")
    public ResponseEntity<ApiResponse<StaffIssueDto>> updateIssueStatus(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIssueStatusRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.updateIssueStatus(
                        user, id, request.status(), request.staffNotes()),
                "Issue status updated"));
    }

    @GetMapping("/events")
    @Operation(summary = "List official notices for the porter's dormitory (FR-EVENT-01)")
    public ResponseEntity<ApiResponse<List<DormEventDto>>> listEvents(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(receptionistService.listEvents(user)));
    }

    @PostMapping("/events")
    @Operation(summary = "Publish an official notice for the porter's dormitory")
    public ResponseEntity<ApiResponse<DormEventDto>> createEvent(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateDormEventRequestDto request
    ) {
        DormEventDto created = receptionistService.createEvent(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Dorm notice published"));
    }

    @PatchMapping("/events/{id}")
    @Operation(summary = "Update an official notice in the porter's dormitory")
    public ResponseEntity<ApiResponse<DormEventDto>> updateEvent(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDormEventRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.updateEvent(user, id, request)));
    }

    @DeleteMapping("/events/{id}")
    @Operation(summary = "Delete an official notice in the porter's dormitory")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        receptionistService.deleteEvent(user, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Dorm notice deleted"));
    }

    @GetMapping("/posts")
    @Operation(summary = "List dormitory-scoped board posts for moderation")
    public ResponseEntity<ApiResponse<PagedResponse<PostDto>>> listPosts(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) PostCategory category,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.listBoardPosts(user, category, status, page, size)));
    }

    @PostMapping("/posts/{id}/remove")
    @Operation(summary = "Remove a dormitory board post (REMOVED_MODERATOR)")
    public ResponseEntity<ApiResponse<PostDto>> removePost(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.removeBoardPost(user, id),
                "Post removed by moderator"));
    }

    @GetMapping("/posts/{id}/comments")
    @Operation(summary = "List comments under a dormitory board post")
    public ResponseEntity<ApiResponse<List<CommentDto>>> listPostComments(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receptionistService.listBoardComments(user, id)));
    }

    @PostMapping("/comments/{id}/remove")
    @Operation(summary = "Soft-delete a board comment as moderator")
    public ResponseEntity<ApiResponse<Void>> removeComment(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        receptionistService.removeBoardComment(user, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Comment removed by moderator"));
    }
}

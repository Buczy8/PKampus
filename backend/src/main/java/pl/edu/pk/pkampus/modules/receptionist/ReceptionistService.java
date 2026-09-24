package pl.edu.pk.pkampus.modules.receptionist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import pl.edu.pk.pkampus.mail.ResourceSchedulePage;
import pl.edu.pk.pkampus.modules.booking.BookingAutoCancelledEvent;
import pl.edu.pk.pkampus.common.PagedResponse;
import pl.edu.pk.pkampus.modules.board.PostCategory;
import pl.edu.pk.pkampus.modules.board.PostModerationService;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.events.EventService;
import pl.edu.pk.pkampus.modules.events.dto.CreateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
import pl.edu.pk.pkampus.modules.events.dto.UpdateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.issues.Issue;
import pl.edu.pk.pkampus.modules.issues.IssueCategory;
import pl.edu.pk.pkampus.modules.issues.IssueRepository;
import pl.edu.pk.pkampus.modules.issues.IssueService;
import pl.edu.pk.pkampus.modules.issues.IssueStatus;
import pl.edu.pk.pkampus.modules.issues.IssueUrgency;
import pl.edu.pk.pkampus.modules.laundry.LaundryBooking;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachine;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineStatus;
import pl.edu.pk.pkampus.modules.laundry.LaundryService;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryScheduleResponseDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskLaundryBookingDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskLaundryMachineDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskOpenIssueDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskRoomBookingDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.MachineBreakdownResponseDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.ReceptionistDeskDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.RoomMaintenanceResponseDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.StaffIssueDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.StaffRoomBookingSlotDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.StaffRoomDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.StaffRoomScheduleDayDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.StaffRoomScheduleResponseDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskThematicRoomDto;
import pl.edu.pk.pkampus.modules.rooms.RoomBooking;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingRepository;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingStatus;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoom;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoomRepository;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoomStatus;
import pl.edu.pk.pkampus.modules.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceptionistService {

    public static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter SLOT_LABEL =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(WARSAW);

    private static final Set<LaundryBookingStatus> DESK_LAUNDRY_STATUSES =
            EnumSet.of(LaundryBookingStatus.CONFIRMED, LaundryBookingStatus.KEY_ISSUED);

    private static final Set<RoomBookingStatus> DESK_ROOM_STATUSES =
            EnumSet.of(RoomBookingStatus.CONFIRMED, RoomBookingStatus.KEY_ISSUED);

    private static final Set<IssueStatus> OPEN_ISSUE_STATUSES = EnumSet.of(
            IssueStatus.NEW,
            IssueStatus.ASSIGNED_TO_MAINTENANCE,
            IssueStatus.IN_PROGRESS,
            IssueStatus.PARTS_REQUIRED
    );

    private static final int STAFF_SCHEDULE_MAX_DAYS = 7;

    private final LaundryBookingRepository laundryBookingRepository;
    private final LaundryMachineRepository laundryMachineRepository;
    private final LaundryService laundryService;
    private final RoomBookingRepository roomBookingRepository;
    private final ThematicRoomRepository thematicRoomRepository;
    private final IssueRepository issueRepository;
    private final IssueService issueService;
    private final EventService eventService;
    private final PostModerationService postModerationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public ReceptionistDeskDto getDesk(User actor) {
        UUID dormitoryId = requireActorDormitoryId(actor);

        LocalDate today = LocalDate.now(WARSAW);
        Instant dayStart = today.atStartOfDay(WARSAW).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(WARSAW).toInstant();

        List<DeskLaundryBookingDto> laundry = laundryBookingRepository
                .findDeskBookingsForDormitoryDay(dormitoryId, dayStart, dayEnd, DESK_LAUNDRY_STATUSES)
                .stream()
                .map(this::toLaundryDto)
                .toList();

        List<DeskRoomBookingDto> rooms = roomBookingRepository
                .findDeskBookingsForDormitoryDay(dormitoryId, dayStart, dayEnd, DESK_ROOM_STATUSES)
                .stream()
                .map(this::toRoomDto)
                .toList();

        List<DeskOpenIssueDto> openIssues = issueRepository
                .findByDormitoryIdAndStatusInOrderByCreatedAtDesc(dormitoryId, OPEN_ISSUE_STATUSES)
                .stream()
                .map(this::toOpenIssueDto)
                .toList();

        return new ReceptionistDeskDto(laundry, rooms, openIssues.size(), openIssues);
    }

    @Transactional(readOnly = true)
    public List<StaffIssueDto> listIssues(
            User actor,
            Collection<IssueStatus> statuses,
            IssueCategory category,
            IssueUrgency urgency,
            LocalDate from,
            LocalDate to,
            String roomNumber,
            Integer floor
    ) {
        UUID dormitoryId = requireActorDormitoryId(actor);
        return issueService.listStaffIssues(
                dormitoryId, statuses, category, urgency, from, to, roomNumber, floor);
    }

    @Transactional(readOnly = true)
    public StaffIssueDto getIssue(User actor, UUID issueId) {
        return issueService.getStaffIssue(requireActorDormitoryId(actor), issueId);
    }

    @Transactional
    public StaffIssueDto updateIssueStatus(
            User actor,
            UUID issueId,
            IssueStatus status,
            String staffNotes
    ) {
        return issueService.updateStaffIssueStatus(
                requireActorDormitoryId(actor), issueId, status, staffNotes);
    }

    @Transactional(readOnly = true)
    public List<DormEventDto> listEvents(User actor) {
        return eventService.listForStaff(actor);
    }

    @Transactional
    public DormEventDto createEvent(User actor, CreateDormEventRequestDto request) {
        return eventService.createForStaff(actor, request);
    }

    @Transactional
    public DormEventDto updateEvent(User actor, UUID id, UpdateDormEventRequestDto request) {
        return eventService.updateForStaff(actor, id, request);
    }

    @Transactional
    public void deleteEvent(User actor, UUID id) {
        eventService.deleteForStaff(actor, id);
    }

    public PagedResponse<PostDto> listBoardPosts(User actor, PostCategory category, String status, int page, int size) {
        return postModerationService.listForStaff(actor, category, status, page, size);
    }

    public PostDto removeBoardPost(User actor, UUID postId) {
        return postModerationService.removePostAsModerator(actor, postId);
    }

    public PagedResponse<CommentDto> listBoardComments(User actor, UUID postId, int page, int size) {
        return postModerationService.listCommentsForStaff(actor, postId, page, size);
    }

    public void removeBoardComment(User actor, UUID commentId) {
        postModerationService.removeCommentAsModerator(actor, commentId);
    }

    @Transactional(readOnly = true)
    public LaundryScheduleResponseDto getLaundrySchedule(User actor, LocalDate from, LocalDate to) {
        Dormitory dorm = requireActorDormitory(actor);
        return laundryService.getStaffSchedule(dorm, from, to);
    }

    @Transactional(readOnly = true)
    public StaffRoomScheduleResponseDto getRoomSchedule(User actor, LocalDate from, LocalDate to) {
        Dormitory dorm = requireActorDormitory(actor);
        validateStaffScheduleRange(from, to);

        List<ThematicRoom> rooms =
                thematicRoomRepository.findAllByDormitoryIdOrderByNameAsc(dorm.getId());

        Instant rangeStart = from.atStartOfDay(WARSAW).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(WARSAW).toInstant();

        List<RoomBooking> bookings = roomBookingRepository.findActiveInDormitoryRange(
                dorm.getId(), rangeStart, rangeEnd, DESK_ROOM_STATUSES);

        List<StaffRoomDto> roomDtos = rooms.stream()
                .map(r -> new StaffRoomDto(
                        r.getId(),
                        r.getName(),
                        r.getStatus(),
                        r.getOpeningTime(),
                        r.getClosingTime(),
                        r.getMaxCapacity(),
                        r.isSpansMidnight()
                ))
                .toList();

        List<StaffRoomScheduleDayDto> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            Instant dayStart = date.atStartOfDay(WARSAW).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(WARSAW).toInstant();
            List<StaffRoomBookingSlotDto> dayBookings = bookings.stream()
                    .filter(b -> b.getStartTime().isBefore(dayEnd) && b.getEndTime().isAfter(dayStart))
                    .map(this::toStaffRoomBookingSlot)
                    .toList();
            days.add(new StaffRoomScheduleDayDto(date, dayBookings));
        }

        return new StaffRoomScheduleResponseDto(roomDtos, days);
    }

    @Transactional
    public DeskRoomBookingDto cancelRoomBooking(User actor, UUID bookingId) {
        RoomBooking booking = requireRoomInDorm(actor, bookingId);
        if (booking.getStatus() != RoomBookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Only CONFIRMED room bookings can be cancelled by staff");
        }
        Instant now = Instant.now();
        boolean lateOrPast = booking.getStartTime().isBefore(now);
        booking.setStatus(lateOrPast ? RoomBookingStatus.AUTO_CANCELLED_15MIN : RoomBookingStatus.CANCELLED_USER);
        RoomBooking saved = roomBookingRepository.save(booking);

        if (lateOrPast) {
            User resident = booking.getUser();
            eventPublisher.publishEvent(new BookingAutoCancelledEvent(
                    resident.getEmail(),
                    resident.getFirstName(),
                    "Room " + booking.getRoom().getName(),
                    booking.getStartTime(),
                    ResourceSchedulePage.ROOMS
            ));
        }

        return toRoomDto(saved);
    }

    @Transactional
    public RoomMaintenanceResponseDto reportRoomMaintenance(User actor, UUID roomId, String reason) {
        Dormitory dorm = requireActorDormitory(actor);
        ThematicRoom room = thematicRoomRepository.findByIdAndDormitoryId(roomId, dorm.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Thematic room not found"));

        if (room.getStatus() == ThematicRoomStatus.MAINTENANCE) {
            throw new BusinessRuleException("Thematic room is already under maintenance");
        }

        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty()) {
            throw new BusinessRuleException("Maintenance reason is required");
        }

        Instant now = Instant.now();
        room.setStatus(ThematicRoomStatus.MAINTENANCE);
        thematicRoomRepository.save(room);

        List<RoomBooking> futureConfirmed = roomBookingRepository.findFutureByRoomIdAndStatus(
                room.getId(), RoomBookingStatus.CONFIRMED, now);

        for (RoomBooking booking : futureConfirmed) {
            booking.setStatus(RoomBookingStatus.CANCELLED_ROOM_MAINTENANCE);
            roomBookingRepository.save(booking);
        }

        String issueDescription = "Auto: thematic room \"%s\" under maintenance. %s"
                .formatted(room.getName(), trimmedReason);

        Issue issue = Issue.builder()
                .reporter(actor)
                .dormitory(dorm)
                .commonAreaName("inne")
                .category(IssueCategory.OTHER)
                .urgency(IssueUrgency.URGENT)
                .description(issueDescription)
                .status(IssueStatus.NEW)
                .build();
        Issue savedIssue = issueRepository.save(issue);

        for (RoomBooking booking : futureConfirmed) {
            User resident = booking.getUser();
            eventPublisher.publishEvent(new RoomMaintenanceNoticeEvent(
                    resident.getEmail(),
                    resident.getFirstName(),
                    room.getName(),
                    booking.getStartTime()
            ));
        }

        log.info("Staff {} marked room {} MAINTENANCE; cancelled {}; issue {}",
                actor.getEmail(), room.getId(), futureConfirmed.size(), savedIssue.getId());

        return new RoomMaintenanceResponseDto(
                room.getId(),
                futureConfirmed.size(),
                savedIssue.getId()
        );
    }

    @Transactional
    public DeskThematicRoomDto restoreRoom(User actor, UUID roomId) {
        Dormitory dorm = requireActorDormitory(actor);
        ThematicRoom room = thematicRoomRepository.findByIdAndDormitoryId(roomId, dorm.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Thematic room not found"));

        if (room.getStatus() != ThematicRoomStatus.MAINTENANCE) {
            throw new BusinessRuleException("Thematic room is not under maintenance");
        }

        room.setStatus(ThematicRoomStatus.AVAILABLE);
        ThematicRoom saved = thematicRoomRepository.save(room);
        return new DeskThematicRoomDto(saved.getId(), saved.getName(), saved.getStatus());
    }

    @Transactional
    public DeskLaundryBookingDto cancelLaundryBooking(User actor, UUID bookingId) {
        LaundryBooking booking = requireLaundryInDorm(actor, bookingId);
        if (booking.getStatus() != LaundryBookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Only CONFIRMED laundry bookings can be cancelled by staff");
        }
        Instant now = Instant.now();
        boolean lateOrPast = booking.getStartTime().isBefore(now);
        booking.setStatus(lateOrPast ? LaundryBookingStatus.AUTO_CANCELLED_15MIN : LaundryBookingStatus.CANCELLED_USER);
        LaundryBooking saved = laundryBookingRepository.save(booking);

        if (lateOrPast) {
            User resident = booking.getUser();
            eventPublisher.publishEvent(new BookingAutoCancelledEvent(
                    resident.getEmail(),
                    resident.getFirstName(),
                    "Laundry machine " + booking.getMachine().getMachineIdentifier(),
                    booking.getStartTime(),
                    ResourceSchedulePage.LAUNDRY
            ));
        }

        return toLaundryDto(saved);
    }

    @Transactional
    public MachineBreakdownResponseDto reportMachineBreakdown(User actor, UUID machineId, String reason) {
        Dormitory dorm = requireActorDormitory(actor);
        LaundryMachine machine = laundryMachineRepository.findByIdAndDormitoryId(machineId, dorm.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Laundry machine not found"));

        if (machine.getStatus() == LaundryMachineStatus.OUT_OF_ORDER) {
            throw new BusinessRuleException("Laundry machine is already out of order");
        }

        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty()) {
            throw new BusinessRuleException("Breakdown reason is required");
        }

        Instant now = Instant.now();
        machine.setStatus(LaundryMachineStatus.OUT_OF_ORDER);
        machine.setNotes(trimmedReason);
        laundryMachineRepository.save(machine);

        List<LaundryBooking> futureConfirmed = laundryBookingRepository.findFutureByMachineIdAndStatus(
                machine.getId(), LaundryBookingStatus.CONFIRMED, now);

        for (LaundryBooking booking : futureConfirmed) {
            booking.setStatus(LaundryBookingStatus.CANCELLED_MACHINE_OUT_OF_ORDER);
            laundryBookingRepository.save(booking);
        }

        String issueDescription = "Auto: laundry machine \"%s\" out of order. %s"
                .formatted(machine.getMachineIdentifier(), trimmedReason);

        Issue issue = Issue.builder()
                .reporter(actor)
                .dormitory(dorm)
                .commonAreaName("pralnia")
                .category(IssueCategory.OTHER)
                .urgency(IssueUrgency.URGENT)
                .description(issueDescription)
                .status(IssueStatus.NEW)
                .build();
        Issue savedIssue = issueRepository.save(issue);

        for (LaundryBooking booking : futureConfirmed) {
            User resident = booking.getUser();
            eventPublisher.publishEvent(new LaundryBreakdownNoticeEvent(
                    resident.getEmail(),
                    resident.getFirstName(),
                    machine.getMachineIdentifier(),
                    booking.getStartTime()
            ));
        }

        log.info("Staff {} marked machine {} OUT_OF_ORDER; cancelled {}; issue {}",
                actor.getEmail(), machine.getId(), futureConfirmed.size(), savedIssue.getId());

        return new MachineBreakdownResponseDto(
                machine.getId(),
                futureConfirmed.size(),
                savedIssue.getId()
        );
    }

    @Transactional
    public DeskLaundryMachineDto restoreMachine(User actor, UUID machineId) {
        Dormitory dorm = requireActorDormitory(actor);
        LaundryMachine machine = laundryMachineRepository.findByIdAndDormitoryId(machineId, dorm.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Laundry machine not found"));

        if (machine.getStatus() != LaundryMachineStatus.OUT_OF_ORDER) {
            throw new BusinessRuleException("Laundry machine is not out of order");
        }

        machine.setStatus(LaundryMachineStatus.AVAILABLE);
        machine.setNotes(null);
        LaundryMachine saved = laundryMachineRepository.save(machine);
        return toMachineDto(saved);
    }

    @Transactional
    public DeskLaundryBookingDto issueLaundryKey(User actor, UUID bookingId) {
        LaundryBooking booking = requireLaundryInDorm(actor, bookingId);
        if (booking.getStatus() != LaundryBookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Only CONFIRMED laundry bookings can receive a key");
        }
        Instant now = Instant.now();
        booking.setStatus(LaundryBookingStatus.KEY_ISSUED);
        booking.setKeyIssuedAt(now);
        return toLaundryDto(laundryBookingRepository.save(booking));
    }

    @Transactional
    public DeskLaundryBookingDto returnLaundryKey(User actor, UUID bookingId) {
        LaundryBooking booking = requireLaundryInDorm(actor, bookingId);
        if (booking.getStatus() != LaundryBookingStatus.KEY_ISSUED) {
            throw new BusinessRuleException("Only KEY_ISSUED laundry bookings can return a key");
        }
        Instant now = Instant.now();
        booking.setStatus(LaundryBookingStatus.COMPLETED);
        booking.setKeyReturnedAt(now);
        return toLaundryDto(laundryBookingRepository.save(booking));
    }

    @Transactional
    public DeskRoomBookingDto issueRoomKey(User actor, UUID bookingId) {
        RoomBooking booking = requireRoomInDorm(actor, bookingId);
        if (booking.getStatus() != RoomBookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Only CONFIRMED room bookings can receive a key");
        }
        Instant now = Instant.now();
        booking.setStatus(RoomBookingStatus.KEY_ISSUED);
        booking.setKeyIssuedAt(now);
        return toRoomDto(roomBookingRepository.save(booking));
    }

    @Transactional
    public DeskRoomBookingDto returnRoomKey(User actor, UUID bookingId) {
        RoomBooking booking = requireRoomInDorm(actor, bookingId);
        if (booking.getStatus() != RoomBookingStatus.KEY_ISSUED) {
            throw new BusinessRuleException("Only KEY_ISSUED room bookings can return a key");
        }
        Instant now = Instant.now();
        booking.setStatus(RoomBookingStatus.COMPLETED);
        booking.setKeyReturnedAt(now);
        return toRoomDto(roomBookingRepository.save(booking));
    }

    private LaundryBooking requireLaundryInDorm(User actor, UUID bookingId) {
        UUID dormitoryId = requireActorDormitoryId(actor);
        return laundryBookingRepository.findByIdWithDetails(bookingId)
                .filter(b -> b.getMachine().getDormitory().getId().equals(dormitoryId))
                .orElseThrow(() -> new ResourceNotFoundException("Laundry booking not found"));
    }

    private RoomBooking requireRoomInDorm(User actor, UUID bookingId) {
        UUID dormitoryId = requireActorDormitoryId(actor);
        return roomBookingRepository.findByIdWithDetails(bookingId)
                .filter(b -> b.getRoom().getDormitory().getId().equals(dormitoryId))
                .orElseThrow(() -> new ResourceNotFoundException("Room booking not found"));
    }

    private Dormitory requireActorDormitory(User actor) {
        Dormitory dorm = actor.getDormitory();
        if (dorm == null || dorm.getId() == null) {
            throw new BusinessRuleException("Staff user is not assigned to a dormitory");
        }
        return dorm;
    }

    private UUID requireActorDormitoryId(User actor) {
        return requireActorDormitory(actor).getId();
    }

    private DeskLaundryBookingDto toLaundryDto(LaundryBooking booking) {
        User resident = booking.getUser();
        return new DeskLaundryBookingDto(
                booking.getId(),
                booking.getMachine().getId(),
                booking.getMachine().getMachineIdentifier(),
                resident.getId(),
                resident.getFirstName(),
                resident.getLastName(),
                resident.getDeclaredRoomNumber(),
                resident.getPhoneNumber(),
                toOffset(booking.getStartTime()),
                toOffset(booking.getEndTime()),
                booking.getStatus(),
                toOffset(booking.getKeyIssuedAt())
        );
    }

    private DeskRoomBookingDto toRoomDto(RoomBooking booking) {
        User resident = booking.getUser();
        return new DeskRoomBookingDto(
                booking.getId(),
                booking.getRoom().getId(),
                booking.getRoom().getName(),
                resident.getId(),
                resident.getFirstName(),
                resident.getLastName(),
                resident.getDeclaredRoomNumber(),
                resident.getPhoneNumber(),
                booking.getParticipantsCount(),
                toOffset(booking.getStartTime()),
                toOffset(booking.getEndTime()),
                booking.getStatus(),
                toOffset(booking.getKeyIssuedAt())
        );
    }

    private DeskLaundryMachineDto toMachineDto(LaundryMachine machine) {
        return new DeskLaundryMachineDto(
                machine.getId(),
                machine.getMachineIdentifier(),
                machine.getFloorLocation(),
                machine.getStatus(),
                machine.getNotes()
        );
    }

    private StaffRoomBookingSlotDto toStaffRoomBookingSlot(RoomBooking booking) {
        User resident = booking.getUser();
        String label = resident.getFirstName() + " " + resident.getLastName();
        if (resident.getDeclaredRoomNumber() != null && !resident.getDeclaredRoomNumber().isBlank()) {
            label += " • pok. " + resident.getDeclaredRoomNumber();
        }
        return new StaffRoomBookingSlotDto(
                booking.getId(),
                booking.getRoom().getId(),
                toOffset(booking.getStartTime()),
                toOffset(booking.getEndTime()),
                booking.getStatus(),
                label,
                booking.getParticipantsCount()
        );
    }

    private void validateStaffScheduleRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessRuleException("from and to dates are required");
        }
        if (to.isBefore(from)) {
            throw new BusinessRuleException("to must be on or after from");
        }
        LocalDate today = LocalDate.now(WARSAW);
        if (from.isBefore(today)) {
            throw new BusinessRuleException("from cannot be before today");
        }
        if (to.isAfter(today.plusDays(STAFF_SCHEDULE_MAX_DAYS))) {
            throw new BusinessRuleException(
                    "Schedule may only cover up to " + STAFF_SCHEDULE_MAX_DAYS + " days ahead");
        }
    }

    private DeskOpenIssueDto toOpenIssueDto(Issue issue) {
        String locationLabel;
        if (issue.getRoom() != null) {
            locationLabel = "Pokój " + issue.getRoom().getRoomNumber();
        } else {
            String commonArea = issue.getCommonAreaName();
            locationLabel = commonArea == null || commonArea.isBlank()
                    ? "—"
                    : capitalizeCommonArea(commonArea);
        }
        return new DeskOpenIssueDto(
                issue.getId(),
                locationLabel,
                issue.getCategory(),
                issue.getUrgency(),
                issue.getDescription(),
                issue.getStatus(),
                toOffset(issue.getCreatedAt())
        );
    }

    private static String capitalizeCommonArea(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static OffsetDateTime toOffset(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(WARSAW).toOffsetDateTime();
    }
}

package pl.edu.pk.pkampus.modules.receptionist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.common.PagedResponse;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.board.PostCategory;
import pl.edu.pk.pkampus.modules.board.PostModerationService;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.events.DormEventPriority;
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
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskRoomBookingDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskThematicRoomDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.MachineBreakdownResponseDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.ReceptionistDeskDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.RoomMaintenanceResponseDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.StaffIssueDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.StaffRoomScheduleResponseDto;
import pl.edu.pk.pkampus.modules.rooms.RoomBooking;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingRepository;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingStatus;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoom;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoomRepository;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoomStatus;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReceptionistService POJO/Mockito unit tests (AAA)")
class ReceptionistServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Mock
    private LaundryBookingRepository laundryBookingRepository;

    @Mock
    private LaundryMachineRepository laundryMachineRepository;

    @Mock
    private LaundryService laundryService;

    @Mock
    private RoomBookingRepository roomBookingRepository;

    @Mock
    private ThematicRoomRepository thematicRoomRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private IssueService issueService;

    @Mock
    private EventService eventService;

    @Mock
    private PostModerationService postModerationService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ReceptionistService service;

    private Dormitory dorm;
    private User staff;
    private User resident;
    private LaundryMachine machine;
    private ThematicRoom thematicRoom;

    @BeforeEach
    void setUp() {
        dorm = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Portiernia")
                .code("PRT")
                .build();

        staff = User.builder()
                .id(UUID.randomUUID())
                .email("portier@pk.edu.pl")
                .firstName("Marian")
                .lastName("Portier")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build();

        resident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .firstName("Piotr")
                .lastName("Student")
                .declaredRoomNumber("101")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build();

        machine = LaundryMachine.builder()
                .id(UUID.randomUUID())
                .dormitory(dorm)
                .machineIdentifier("Pralka P1")
                .floorLocation("Parter")
                .status(LaundryMachineStatus.AVAILABLE)
                .build();

        thematicRoom = ThematicRoom.builder()
                .id(UUID.randomUUID())
                .dormitory(dorm)
                .name("Salka Cichej Nauki")
                .status(ThematicRoomStatus.AVAILABLE)
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(22, 0))
                .maxCapacity(10)
                .build();
    }

    @Nested
    @DisplayName("getDesk tests")
    class GetDeskTests {

        @Test
        @DisplayName("Should throw BusinessRuleException when staff has no dormitory")
        void getDeskThrowsWhenNoDorm() {
            // Arrange
            staff.setDormitory(null);

            // Act & Assert
            assertThrows(BusinessRuleException.class, () -> service.getDesk(staff));
        }

        @Test
        @DisplayName("Should return today's desk snapshot")
        void getDeskSuccess() {
            // Arrange
            when(laundryBookingRepository.findDeskBookingsForDormitoryDay(eq(dorm.getId()), any(), any(), any()))
                    .thenReturn(List.of());
            when(roomBookingRepository.findDeskBookingsForDormitoryDay(eq(dorm.getId()), any(), any(), any()))
                    .thenReturn(List.of());
            when(issueRepository.findByDormitoryIdAndStatusInOrderByCreatedAtDesc(eq(dorm.getId()), any()))
                    .thenReturn(List.of());

            // Act
            ReceptionistDeskDto desk = service.getDesk(staff);

            // Assert
            assertNotNull(desk);
            assertEquals(0, desk.laundry().size());
            assertEquals(0, desk.rooms().size());
            assertEquals(0, desk.openIssuesCount());
        }
    }

    @Nested
    @DisplayName("Laundry booking moderation tests")
    class LaundryModerationTests {

        @Test
        @DisplayName("cancelLaundryBooking cancels late booking to AUTO_CANCELLED_15MIN and sends email")
        void cancelLaundryBookingLate() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            Instant pastStart = Instant.now().minus(20, ChronoUnit.MINUTES);
            LaundryBooking booking = LaundryBooking.builder()
                    .id(bookingId)
                    .machine(machine)
                    .user(resident)
                    .startTime(pastStart)
                    .endTime(pastStart.plus(90, ChronoUnit.MINUTES))
                    .status(LaundryBookingStatus.CONFIRMED)
                    .build();

            when(laundryBookingRepository.findByIdWithDetails(bookingId)).thenReturn(Optional.of(booking));
            when(laundryBookingRepository.save(booking)).thenReturn(booking);

            // Act
            DeskLaundryBookingDto result = service.cancelLaundryBooking(staff, bookingId);

            // Assert
            assertNotNull(result);
            assertEquals(LaundryBookingStatus.AUTO_CANCELLED_15MIN, booking.getStatus());
            verify(emailService).sendBookingAutoCancelled15MinEmail(
                    eq(resident.getEmail()), eq(resident.getFirstName()), any(), any(), eq("laundry"));
        }

        @Test
        @DisplayName("cancelLaundryBooking cancels future booking to CANCELLED_USER without 15min email")
        void cancelLaundryBookingFuture() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            Instant futureStart = Instant.now().plus(2, ChronoUnit.HOURS);
            LaundryBooking booking = LaundryBooking.builder()
                    .id(bookingId)
                    .machine(machine)
                    .user(resident)
                    .startTime(futureStart)
                    .endTime(futureStart.plus(90, ChronoUnit.MINUTES))
                    .status(LaundryBookingStatus.CONFIRMED)
                    .build();

            when(laundryBookingRepository.findByIdWithDetails(bookingId)).thenReturn(Optional.of(booking));
            when(laundryBookingRepository.save(booking)).thenReturn(booking);

            // Act
            DeskLaundryBookingDto result = service.cancelLaundryBooking(staff, bookingId);

            // Assert
            assertNotNull(result);
            assertEquals(LaundryBookingStatus.CANCELLED_USER, booking.getStatus());
        }

        @Test
        @DisplayName("issueLaundryKey transitions status from CONFIRMED to KEY_ISSUED")
        void issueLaundryKeySuccess() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            LaundryBooking booking = LaundryBooking.builder()
                    .id(bookingId)
                    .machine(machine)
                    .user(resident)
                    .startTime(Instant.now().plus(1, ChronoUnit.HOURS))
                    .endTime(Instant.now().plus(3, ChronoUnit.HOURS))
                    .status(LaundryBookingStatus.CONFIRMED)
                    .build();

            when(laundryBookingRepository.findByIdWithDetails(bookingId)).thenReturn(Optional.of(booking));
            when(laundryBookingRepository.save(booking)).thenReturn(booking);

            // Act
            DeskLaundryBookingDto result = service.issueLaundryKey(staff, bookingId);

            // Assert
            assertNotNull(result);
            assertEquals(LaundryBookingStatus.KEY_ISSUED, booking.getStatus());
            assertNotNull(booking.getKeyIssuedAt());
        }

        @Test
        @DisplayName("returnLaundryKey transitions status from KEY_ISSUED to COMPLETED")
        void returnLaundryKeySuccess() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            LaundryBooking booking = LaundryBooking.builder()
                    .id(bookingId)
                    .machine(machine)
                    .user(resident)
                    .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                    .endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                    .status(LaundryBookingStatus.KEY_ISSUED)
                    .build();

            when(laundryBookingRepository.findByIdWithDetails(bookingId)).thenReturn(Optional.of(booking));
            when(laundryBookingRepository.save(booking)).thenReturn(booking);

            // Act
            DeskLaundryBookingDto result = service.returnLaundryKey(staff, bookingId);

            // Assert
            assertNotNull(result);
            assertEquals(LaundryBookingStatus.COMPLETED, booking.getStatus());
            assertNotNull(booking.getKeyReturnedAt());
        }

        @Test
        @DisplayName("reportMachineBreakdown marks machine OUT_OF_ORDER, cancels bookings, creates issue and sends emails")
        void reportMachineBreakdownSuccess() {
            // Arrange
            UUID machineId = machine.getId();
            LaundryBooking futureBooking = LaundryBooking.builder()
                    .id(UUID.randomUUID())
                    .machine(machine)
                    .user(resident)
                    .startTime(Instant.now().plus(2, ChronoUnit.HOURS))
                    .endTime(Instant.now().plus(4, ChronoUnit.HOURS))
                    .status(LaundryBookingStatus.CONFIRMED)
                    .build();

            when(laundryMachineRepository.findByIdAndDormitoryId(machineId, dorm.getId()))
                    .thenReturn(Optional.of(machine));
            when(laundryBookingRepository.findFutureByMachineIdAndStatus(eq(machineId), eq(LaundryBookingStatus.CONFIRMED), any()))
                    .thenReturn(List.of(futureBooking));
            when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> {
                Issue issue = inv.getArgument(0);
                issue.setId(UUID.randomUUID());
                return issue;
            });

            // Act
            MachineBreakdownResponseDto response = service.reportMachineBreakdown(staff, machineId, "Wyciek wody");

            // Assert
            assertNotNull(response);
            assertEquals(LaundryMachineStatus.OUT_OF_ORDER, machine.getStatus());
            assertEquals("Wyciek wody", machine.getNotes());
            assertEquals(1, response.cancelledCount());
            assertEquals(LaundryBookingStatus.CANCELLED_MACHINE_OUT_OF_ORDER, futureBooking.getStatus());
            verify(emailService).sendLaundryMachineBreakdownEmail(
                    eq(resident.getEmail()), eq(resident.getFirstName()), eq(machine.getMachineIdentifier()), any());
        }

        @Test
        @DisplayName("restoreMachine restores machine to AVAILABLE and clears notes")
        void restoreMachineSuccess() {
            // Arrange
            UUID machineId = machine.getId();
            machine.setStatus(LaundryMachineStatus.OUT_OF_ORDER);
            machine.setNotes("Uszkodzona uszczelka");

            when(laundryMachineRepository.findByIdAndDormitoryId(machineId, dorm.getId()))
                    .thenReturn(Optional.of(machine));
            when(laundryMachineRepository.save(machine)).thenReturn(machine);

            // Act
            DeskLaundryMachineDto result = service.restoreMachine(staff, machineId);

            // Assert
            assertNotNull(result);
            assertEquals(LaundryMachineStatus.AVAILABLE, machine.getStatus());
            assertNull(machine.getNotes());
        }
    }

    @Nested
    @DisplayName("Thematic room moderation tests")
    class RoomModerationTests {

        @Test
        @DisplayName("cancelRoomBooking cancels late booking to AUTO_CANCELLED_15MIN and sends email")
        void cancelRoomBookingLate() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            Instant pastStart = Instant.now().minus(20, ChronoUnit.MINUTES);
            RoomBooking booking = RoomBooking.builder()
                    .id(bookingId)
                    .room(thematicRoom)
                    .user(resident)
                    .startTime(pastStart)
                    .endTime(pastStart.plus(60, ChronoUnit.MINUTES))
                    .status(RoomBookingStatus.CONFIRMED)
                    .build();

            when(roomBookingRepository.findByIdWithDetails(bookingId)).thenReturn(Optional.of(booking));
            when(roomBookingRepository.save(booking)).thenReturn(booking);

            // Act
            DeskRoomBookingDto result = service.cancelRoomBooking(staff, bookingId);

            // Assert
            assertNotNull(result);
            assertEquals(RoomBookingStatus.AUTO_CANCELLED_15MIN, booking.getStatus());
            verify(emailService).sendBookingAutoCancelled15MinEmail(
                    eq(resident.getEmail()), eq(resident.getFirstName()), any(), any(), eq("rooms"));
        }

        @Test
        @DisplayName("issueRoomKey transitions status from CONFIRMED to KEY_ISSUED")
        void issueRoomKeySuccess() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            RoomBooking booking = RoomBooking.builder()
                    .id(bookingId)
                    .room(thematicRoom)
                    .user(resident)
                    .startTime(Instant.now().plus(1, ChronoUnit.HOURS))
                    .endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                    .status(RoomBookingStatus.CONFIRMED)
                    .build();

            when(roomBookingRepository.findByIdWithDetails(bookingId)).thenReturn(Optional.of(booking));
            when(roomBookingRepository.save(booking)).thenReturn(booking);

            // Act
            DeskRoomBookingDto result = service.issueRoomKey(staff, bookingId);

            // Assert
            assertNotNull(result);
            assertEquals(RoomBookingStatus.KEY_ISSUED, booking.getStatus());
            assertNotNull(booking.getKeyIssuedAt());
        }

        @Test
        @DisplayName("returnRoomKey transitions status from KEY_ISSUED to COMPLETED")
        void returnRoomKeySuccess() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            RoomBooking booking = RoomBooking.builder()
                    .id(bookingId)
                    .room(thematicRoom)
                    .user(resident)
                    .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                    .endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                    .status(RoomBookingStatus.KEY_ISSUED)
                    .build();

            when(roomBookingRepository.findByIdWithDetails(bookingId)).thenReturn(Optional.of(booking));
            when(roomBookingRepository.save(booking)).thenReturn(booking);

            // Act
            DeskRoomBookingDto result = service.returnRoomKey(staff, bookingId);

            // Assert
            assertNotNull(result);
            assertEquals(RoomBookingStatus.COMPLETED, booking.getStatus());
            assertNotNull(booking.getKeyReturnedAt());
        }

        @Test
        @DisplayName("reportRoomMaintenance marks room MAINTENANCE, cancels bookings, creates issue and sends emails")
        void reportRoomMaintenanceSuccess() {
            // Arrange
            UUID roomId = thematicRoom.getId();
            RoomBooking futureBooking = RoomBooking.builder()
                    .id(UUID.randomUUID())
                    .room(thematicRoom)
                    .user(resident)
                    .startTime(Instant.now().plus(2, ChronoUnit.HOURS))
                    .endTime(Instant.now().plus(4, ChronoUnit.HOURS))
                    .status(RoomBookingStatus.CONFIRMED)
                    .build();

            when(thematicRoomRepository.findByIdAndDormitoryId(roomId, dorm.getId()))
                    .thenReturn(Optional.of(thematicRoom));
            when(roomBookingRepository.findFutureByRoomIdAndStatus(eq(roomId), eq(RoomBookingStatus.CONFIRMED), any()))
                    .thenReturn(List.of(futureBooking));
            when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> {
                Issue issue = inv.getArgument(0);
                issue.setId(UUID.randomUUID());
                return issue;
            });

            // Act
            RoomMaintenanceResponseDto response = service.reportRoomMaintenance(staff, roomId, "Malowanie ścian");

            // Assert
            assertNotNull(response);
            assertEquals(ThematicRoomStatus.MAINTENANCE, thematicRoom.getStatus());
            assertEquals(1, response.cancelledCount());
            assertEquals(RoomBookingStatus.CANCELLED_ROOM_MAINTENANCE, futureBooking.getStatus());
            verify(emailService).sendRoomMaintenanceEmail(
                    eq(resident.getEmail()), eq(resident.getFirstName()), eq(thematicRoom.getName()), any());
        }

        @Test
        @DisplayName("restoreRoom restores room to AVAILABLE")
        void restoreRoomSuccess() {
            // Arrange
            UUID roomId = thematicRoom.getId();
            thematicRoom.setStatus(ThematicRoomStatus.MAINTENANCE);

            when(thematicRoomRepository.findByIdAndDormitoryId(roomId, dorm.getId()))
                    .thenReturn(Optional.of(thematicRoom));
            when(thematicRoomRepository.save(thematicRoom)).thenReturn(thematicRoom);

            // Act
            DeskThematicRoomDto result = service.restoreRoom(staff, roomId);

            // Assert
            assertNotNull(result);
            assertEquals(ThematicRoomStatus.AVAILABLE, thematicRoom.getStatus());
        }
    }

    @Nested
    @DisplayName("Staff schedule tests")
    class ScheduleTests {

        @Test
        @DisplayName("getLaundrySchedule delegates to laundryService.getStaffSchedule")
        void getLaundryScheduleDelegates() {
            // Arrange
            LocalDate from = LocalDate.now(WARSAW);
            LocalDate to = from.plusDays(1);
            LaundryScheduleResponseDto mockResponse = new LaundryScheduleResponseDto(
                    LocalTime.of(7, 0), LocalTime.of(22, 0), 90, List.of(), List.of()
            );

            when(laundryService.getStaffSchedule(dorm, from, to)).thenReturn(mockResponse);

            // Act
            LaundryScheduleResponseDto result = service.getLaundrySchedule(staff, from, to);

            // Assert
            assertEquals(mockResponse, result);
            verify(laundryService).getStaffSchedule(dorm, from, to);
        }

        @Test
        @DisplayName("getRoomSchedule throws BusinessRuleException on invalid date range")
        void getRoomScheduleThrowsOnInvalidDateRange() {
            // Arrange
            LocalDate today = LocalDate.now(WARSAW);

            // Act & Assert
            assertThrows(BusinessRuleException.class, () -> service.getRoomSchedule(staff, null, today));
            assertThrows(BusinessRuleException.class, () -> service.getRoomSchedule(staff, today.plusDays(2), today));
            assertThrows(BusinessRuleException.class, () -> service.getRoomSchedule(staff, today.minusDays(1), today));
            assertThrows(BusinessRuleException.class, () -> service.getRoomSchedule(staff, today, today.plusDays(8)));
        }

        @Test
        @DisplayName("getRoomSchedule returns thematic rooms and day bookings")
        void getRoomScheduleSuccess() {
            // Arrange
            LocalDate from = LocalDate.now(WARSAW).plusDays(1);
            LocalDate to = from;

            when(thematicRoomRepository.findAllByDormitoryIdOrderByNameAsc(dorm.getId()))
                    .thenReturn(List.of(thematicRoom));
            when(roomBookingRepository.findActiveInDormitoryRange(eq(dorm.getId()), any(), any(), any()))
                    .thenReturn(List.of());

            // Act
            StaffRoomScheduleResponseDto result = service.getRoomSchedule(staff, from, to);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.rooms().size());
            assertEquals(1, result.days().size());
        }
    }

    @Nested
    @DisplayName("Service delegation tests")
    class DelegationTests {

        @Test
        @DisplayName("listIssues, getIssue, updateIssueStatus delegate to issueService")
        void issueDelegations() {
            // Arrange
            UUID issueId = UUID.randomUUID();
            when(issueService.listStaffIssues(eq(dorm.getId()), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(issueService.getStaffIssue(dorm.getId(), issueId)).thenReturn(null);
            when(issueService.updateStaffIssueStatus(dorm.getId(), issueId, IssueStatus.RESOLVED, "Naprawione"))
                    .thenReturn(null);

            // Act
            service.listIssues(staff, null, null, null, null, null, null, null);
            service.getIssue(staff, issueId);
            service.updateIssueStatus(staff, issueId, IssueStatus.RESOLVED, "Naprawione");

            // Assert
            verify(issueService).listStaffIssues(eq(dorm.getId()), any(), any(), any(), any(), any(), any(), any());
            verify(issueService).getStaffIssue(dorm.getId(), issueId);
            verify(issueService).updateStaffIssueStatus(dorm.getId(), issueId, IssueStatus.RESOLVED, "Naprawione");
        }

        @Test
        @DisplayName("listEvents, createEvent, updateEvent, deleteEvent delegate to eventService")
        void eventDelegations() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            CreateDormEventRequestDto createDto = new CreateDormEventRequestDto("Tytuł", "Opis", DormEventPriority.INFO, Instant.now(), null);
            UpdateDormEventRequestDto updateDto = new UpdateDormEventRequestDto("Nowy", null, null, null, null);

            when(eventService.listForStaff(staff)).thenReturn(List.of());
            when(eventService.createForStaff(staff, createDto)).thenReturn(null);
            when(eventService.updateForStaff(staff, eventId, updateDto)).thenReturn(null);

            // Act
            service.listEvents(staff);
            service.createEvent(staff, createDto);
            service.updateEvent(staff, eventId, updateDto);
            service.deleteEvent(staff, eventId);

            // Assert
            verify(eventService).listForStaff(staff);
            verify(eventService).createForStaff(staff, createDto);
            verify(eventService).updateForStaff(staff, eventId, updateDto);
            verify(eventService).deleteForStaff(staff, eventId);
        }

        @Test
        @DisplayName("listBoardPosts, removeBoardPost, listBoardComments, removeBoardComment delegate to postModerationService")
        void postDelegations() {
            // Arrange
            UUID postId = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();

            when(postModerationService.listForStaff(staff, PostCategory.GENERAL, "ALL", 0, 20)).thenReturn(PagedResponse.of(List.of(), 0, 20, 0));
            when(postModerationService.removePostAsModerator(staff, postId)).thenReturn(null);
            when(postModerationService.listCommentsForStaff(staff, postId)).thenReturn(List.of());

            // Act
            service.listBoardPosts(staff, PostCategory.GENERAL, "ALL", 0, 20);
            service.removeBoardPost(staff, postId);
            service.listBoardComments(staff, postId);
            service.removeBoardComment(staff, commentId);

            // Assert
            verify(postModerationService).listForStaff(staff, PostCategory.GENERAL, "ALL", 0, 20);
            verify(postModerationService).removePostAsModerator(staff, postId);
            verify(postModerationService).listCommentsForStaff(staff, postId);
            verify(postModerationService).removeCommentAsModerator(staff, commentId);
        }
    }
}

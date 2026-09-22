package pl.edu.pk.pkampus.modules.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryBooking;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachine;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineStatus;
import pl.edu.pk.pkampus.modules.receptionist.ReceptionistService;
import pl.edu.pk.pkampus.modules.rooms.RoomBooking;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingRepository;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingStatus;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoom;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoomRepository;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoomStatus;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Booking 15-minute auto-cancellation integration tests (BR-02 / NFR-REL-03)")
class BookingAutoCancellationIntegrationTest {

    @Autowired
    private BookingAutoCancellationService autoCancellationService;

    @Autowired
    private ReceptionistService receptionistService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private LaundryMachineRepository laundryMachineRepository;

    @Autowired
    private LaundryBookingRepository laundryBookingRepository;

    @Autowired
    private ThematicRoomRepository thematicRoomRepository;

    @Autowired
    private RoomBookingRepository roomBookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailService emailService;

    @MockBean
    private MinioStorageService minioStorageService;

    private Dormitory dormitory;
    private User resident;
    private User receptionist;
    private LaundryMachine machine;
    private ThematicRoom room;

    @BeforeEach
    void setUp() {
        dormitory = dormitoryRepository.save(Dormitory.builder()
                .name("DS-1 DS-AutoCancel")
                .code("DS1-AC")
                .address("ul. Skarżyńskiego 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(6, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        resident = userRepository.save(User.builder()
                .email("student.autocancel@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .firstName("Kamil")
                .lastName("Kowalski")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dormitory)
                .declaredRoomNumber("201")
                .phoneNumber("+48123456789")
                .avatarUrl("avatars/kamil.jpg")
                .build());

        receptionist = userRepository.save(User.builder()
                .email("porter.autocancel@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .firstName("Marian")
                .lastName("Portier")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dormitory)
                .declaredRoomNumber("PORTIER")
                .phoneNumber("+48987654321")
                .avatarUrl("avatars/marian.jpg")
                .build());

        machine = laundryMachineRepository.save(LaundryMachine.builder()
                .dormitory(dormitory)
                .machineIdentifier("PRALKA-TEST-1")
                .floorLocation("Parter")
                .status(LaundryMachineStatus.AVAILABLE)
                .build());

        room = thematicRoomRepository.save(ThematicRoom.builder()
                .dormitory(dormitory)
                .name("Salka Bilardowa AutoCancel")
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .maxCapacity(6)
                .maxDurationHours(4)
                .spansMidnight(false)
                .status(ThematicRoomStatus.AVAILABLE)
                .build());
    }

    @Test
    @DisplayName("Should cancel CONFIRMED laundry booking past 15-minute threshold")
    void shouldCancelUnclaimedLaundryBookingAfter15Minutes() {
        Instant pastStart = Instant.now().minus(20, ChronoUnit.MINUTES);
        Instant pastEnd = pastStart.plus(90, ChronoUnit.MINUTES);

        LaundryBooking booking = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine)
                .user(resident)
                .startTime(pastStart)
                .endTime(pastEnd)
                .status(LaundryBookingStatus.CONFIRMED)
                .build());

        int cancelledCount = autoCancellationService.cancelAllExpiredBookings(15);

        assertThat(cancelledCount).isEqualTo(1);
        LaundryBooking updated = laundryBookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(LaundryBookingStatus.AUTO_CANCELLED_15MIN);

        verify(emailService).sendBookingAutoCancelled15MinEmail(
                eq(resident.getEmail()),
                eq(resident.getFirstName()),
                eq("Laundry machine " + machine.getMachineIdentifier()),
                anyString(),
                eq("laundry")
        );
    }

    @Test
    @DisplayName("Should NOT cancel CONFIRMED laundry booking within 15-minute grace period")
    void shouldNotCancelUnclaimedLaundryBookingWithin15Minutes() {
        Instant recentStart = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant recentEnd = recentStart.plus(90, ChronoUnit.MINUTES);

        LaundryBooking booking = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine)
                .user(resident)
                .startTime(recentStart)
                .endTime(recentEnd)
                .status(LaundryBookingStatus.CONFIRMED)
                .build());

        int cancelledCount = autoCancellationService.cancelAllExpiredBookings(15);

        assertThat(cancelledCount).isEqualTo(0);
        LaundryBooking updated = laundryBookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(LaundryBookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Should NOT cancel laundry booking if key was already issued")
    void shouldNotCancelLaundryBookingWhenKeyAlreadyIssued() {
        Instant pastStart = Instant.now().minus(25, ChronoUnit.MINUTES);
        Instant pastEnd = pastStart.plus(90, ChronoUnit.MINUTES);

        LaundryBooking booking = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine)
                .user(resident)
                .startTime(pastStart)
                .endTime(pastEnd)
                .keyIssuedAt(pastStart.plus(5, ChronoUnit.MINUTES))
                .status(LaundryBookingStatus.KEY_ISSUED)
                .build());

        int cancelledCount = autoCancellationService.cancelAllExpiredBookings(15);

        assertThat(cancelledCount).isEqualTo(0);
        LaundryBooking updated = laundryBookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(LaundryBookingStatus.KEY_ISSUED);
    }

    @Test
    @DisplayName("Should cancel CONFIRMED room booking past 15-minute threshold")
    void shouldCancelUnclaimedRoomBookingAfter15Minutes() {
        Instant pastStart = Instant.now().minus(20, ChronoUnit.MINUTES);
        Instant pastEnd = pastStart.plus(120, ChronoUnit.MINUTES);

        RoomBooking booking = roomBookingRepository.save(RoomBooking.builder()
                .room(room)
                .user(resident)
                .startTime(pastStart)
                .endTime(pastEnd)
                .participantsCount(4)
                .purpose("Gra w bilard")
                .status(RoomBookingStatus.CONFIRMED)
                .build());

        int cancelledCount = autoCancellationService.cancelAllExpiredBookings(15);

        assertThat(cancelledCount).isEqualTo(1);
        RoomBooking updated = roomBookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RoomBookingStatus.AUTO_CANCELLED_15MIN);

        verify(emailService).sendBookingAutoCancelled15MinEmail(
                eq(resident.getEmail()),
                eq(resident.getFirstName()),
                eq("Room " + room.getName()),
                anyString(),
                eq("rooms")
        );
    }

    @Test
    @DisplayName("Should NOT cancel CONFIRMED room booking within 15-minute grace period")
    void shouldNotCancelUnclaimedRoomBookingWithin15Minutes() {
        Instant recentStart = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant recentEnd = recentStart.plus(120, ChronoUnit.MINUTES);

        RoomBooking booking = roomBookingRepository.save(RoomBooking.builder()
                .room(room)
                .user(resident)
                .startTime(recentStart)
                .endTime(recentEnd)
                .participantsCount(3)
                .purpose("Spotkanie koła")
                .status(RoomBookingStatus.CONFIRMED)
                .build());

        int cancelledCount = autoCancellationService.cancelAllExpiredBookings(15);

        assertThat(cancelledCount).isEqualTo(0);
        RoomBooking updated = roomBookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RoomBookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("When receptionist cancels past-start booking, status transitions to AUTO_CANCELLED_15MIN")
    void shouldSetAutoCancelledStatusWhenStaffCancelsLateBooking() {
        Instant pastStart = Instant.now().minus(16, ChronoUnit.MINUTES);
        Instant pastEnd = pastStart.plus(90, ChronoUnit.MINUTES);

        LaundryBooking booking = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine)
                .user(resident)
                .startTime(pastStart)
                .endTime(pastEnd)
                .status(LaundryBookingStatus.CONFIRMED)
                .build());

        receptionistService.cancelLaundryBooking(receptionist, booking.getId());

        LaundryBooking updated = laundryBookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(LaundryBookingStatus.AUTO_CANCELLED_15MIN);

        verify(emailService).sendBookingAutoCancelled15MinEmail(
                eq(resident.getEmail()),
                eq(resident.getFirstName()),
                eq("Laundry machine " + machine.getMachineIdentifier()),
                anyString(),
                eq("laundry")
        );
    }
}

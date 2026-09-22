package pl.edu.pk.pkampus.modules.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.laundry.LaundryBooking;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachine;
import pl.edu.pk.pkampus.modules.rooms.RoomBooking;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingRepository;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingStatus;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoom;
import pl.edu.pk.pkampus.modules.user.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingAutoCancellationService unit tests (AAA)")
class BookingAutoCancellationServiceTest {

    @Mock
    private LaundryBookingRepository laundryBookingRepository;

    @Mock
    private RoomBookingRepository roomBookingRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private BookingAutoCancellationService bookingAutoCancellationService;

    private User resident;
    private LaundryMachine laundryMachine;
    private ThematicRoom thematicRoom;
    private Instant now;

    @BeforeEach
    void setUp() {
        resident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .firstName("Kamil")
                .lastName("Nowak")
                .build();

        laundryMachine = LaundryMachine.builder()
                .id(UUID.randomUUID())
                .machineIdentifier("PRALKA-01")
                .build();

        thematicRoom = ThematicRoom.builder()
                .id(UUID.randomUUID())
                .name("Salka Muzyczna")
                .build();

        now = Instant.now();
    }

    @Nested
    @DisplayName("cancelExpiredLaundryBookings")
    class CancelExpiredLaundryBookings {

        @Test
        @DisplayName("Should cancel expired laundry bookings, set AUTO_CANCELLED_15MIN and send email")
        void cancelExpiredLaundrySuccess() {
            // Arrange
            Instant startTime = now.minus(25, ChronoUnit.MINUTES);
            LaundryBooking booking = LaundryBooking.builder()
                    .id(UUID.randomUUID())
                    .user(resident)
                    .machine(laundryMachine)
                    .startTime(startTime)
                    .endTime(startTime.plus(90, ChronoUnit.MINUTES))
                    .status(LaundryBookingStatus.CONFIRMED)
                    .build();

            when(laundryBookingRepository.findExpiredUnclaimed(eq(LaundryBookingStatus.CONFIRMED), any(Instant.class)))
                    .thenReturn(List.of(booking));

            // Act
            int cancelled = bookingAutoCancellationService.cancelExpiredLaundryBookings(now, 15);

            // Assert
            assertEquals(1, cancelled);
            assertEquals(LaundryBookingStatus.AUTO_CANCELLED_15MIN, booking.getStatus());
            verify(laundryBookingRepository).save(booking);
            verify(emailService).sendBookingAutoCancelled15MinEmail(
                    eq("student@pk.edu.pl"),
                    eq("Kamil"),
                    eq("Laundry machine PRALKA-01"),
                    anyString(),
                    eq("laundry")
            );
        }

        @Test
        @DisplayName("Should return 0 and perform no saves or emails when no laundry bookings expired")
        void cancelExpiredLaundryEmpty() {
            // Arrange
            when(laundryBookingRepository.findExpiredUnclaimed(eq(LaundryBookingStatus.CONFIRMED), any(Instant.class)))
                    .thenReturn(List.of());

            // Act
            int cancelled = bookingAutoCancellationService.cancelExpiredLaundryBookings(now, 15);

            // Assert
            assertEquals(0, cancelled);
            verify(laundryBookingRepository, never()).save(any());
            verify(emailService, never()).sendBookingAutoCancelled15MinEmail(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("cancelExpiredRoomBookings")
    class CancelExpiredRoomBookings {

        @Test
        @DisplayName("Should cancel expired room bookings, set AUTO_CANCELLED_15MIN and send email")
        void cancelExpiredRoomSuccess() {
            // Arrange
            Instant startTime = now.minus(20, ChronoUnit.MINUTES);
            RoomBooking booking = RoomBooking.builder()
                    .id(UUID.randomUUID())
                    .user(resident)
                    .room(thematicRoom)
                    .startTime(startTime)
                    .endTime(startTime.plus(120, ChronoUnit.MINUTES))
                    .status(RoomBookingStatus.CONFIRMED)
                    .build();

            when(roomBookingRepository.findExpiredUnclaimed(eq(RoomBookingStatus.CONFIRMED), any(Instant.class)))
                    .thenReturn(List.of(booking));

            // Act
            int cancelled = bookingAutoCancellationService.cancelExpiredRoomBookings(now, 15);

            // Assert
            assertEquals(1, cancelled);
            assertEquals(RoomBookingStatus.AUTO_CANCELLED_15MIN, booking.getStatus());
            verify(roomBookingRepository).save(booking);
            verify(emailService).sendBookingAutoCancelled15MinEmail(
                    eq("student@pk.edu.pl"),
                    eq("Kamil"),
                    eq("Room Salka Muzyczna"),
                    anyString(),
                    eq("rooms")
            );
        }

        @Test
        @DisplayName("Should return 0 and perform no saves or emails when no room bookings expired")
        void cancelExpiredRoomEmpty() {
            // Arrange
            when(roomBookingRepository.findExpiredUnclaimed(eq(RoomBookingStatus.CONFIRMED), any(Instant.class)))
                    .thenReturn(List.of());

            // Act
            int cancelled = bookingAutoCancellationService.cancelExpiredRoomBookings(now, 15);

            // Assert
            assertEquals(0, cancelled);
            verify(roomBookingRepository, never()).save(any());
            verify(emailService, never()).sendBookingAutoCancelled15MinEmail(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("cancelAllExpiredBookings")
    class CancelAllExpiredBookings {

        @Test
        @DisplayName("Should invoke both laundry and room cancellations and aggregate counts")
        void cancelAllExpiredSuccess() {
            // Arrange
            when(laundryBookingRepository.findExpiredUnclaimed(eq(LaundryBookingStatus.CONFIRMED), any(Instant.class)))
                    .thenReturn(List.of());
            when(roomBookingRepository.findExpiredUnclaimed(eq(RoomBookingStatus.CONFIRMED), any(Instant.class)))
                    .thenReturn(List.of());

            // Act
            int total = bookingAutoCancellationService.cancelAllExpiredBookings(15);

            // Assert
            assertEquals(0, total);
            verify(laundryBookingRepository).findExpiredUnclaimed(eq(LaundryBookingStatus.CONFIRMED), any(Instant.class));
            verify(roomBookingRepository).findExpiredUnclaimed(eq(RoomBookingStatus.CONFIRMED), any(Instant.class));
        }
    }
}

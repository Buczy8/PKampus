package pl.edu.pk.pkampus.modules.booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.mail.ResourceSchedulePage;
import pl.edu.pk.pkampus.modules.laundry.LaundryBooking;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;
import pl.edu.pk.pkampus.modules.rooms.RoomBooking;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingRepository;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingStatus;
import pl.edu.pk.pkampus.modules.user.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingAutoCancellationService {

    private final LaundryBookingRepository laundryBookingRepository;
    private final RoomBookingRepository roomBookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public int cancelExpiredLaundryBookings(Instant now, int thresholdMinutes) {
        Instant cutoff = now.minus(thresholdMinutes, ChronoUnit.MINUTES);
        List<LaundryBooking> expired =
                laundryBookingRepository.findExpiredUnclaimed(LaundryBookingStatus.CONFIRMED, cutoff);

        for (LaundryBooking booking : expired) {
            booking.setStatus(LaundryBookingStatus.AUTO_CANCELLED_15MIN);
            laundryBookingRepository.save(booking);

            User resident = booking.getUser();
            String machineLabel = booking.getMachine().getMachineIdentifier();
            eventPublisher.publishEvent(new BookingAutoCancelledEvent(
                    resident.getEmail(),
                    resident.getFirstName(),
                    "Laundry machine " + machineLabel,
                    booking.getStartTime(),
                    ResourceSchedulePage.LAUNDRY
            ));
        }

        return expired.size();
    }

    @Transactional
    public int cancelExpiredRoomBookings(Instant now, int thresholdMinutes) {
        Instant cutoff = now.minus(thresholdMinutes, ChronoUnit.MINUTES);
        List<RoomBooking> expired =
                roomBookingRepository.findExpiredUnclaimed(RoomBookingStatus.CONFIRMED, cutoff);

        for (RoomBooking booking : expired) {
            booking.setStatus(RoomBookingStatus.AUTO_CANCELLED_15MIN);
            roomBookingRepository.save(booking);

            User resident = booking.getUser();
            String roomLabel = booking.getRoom().getName();
            eventPublisher.publishEvent(new BookingAutoCancelledEvent(
                    resident.getEmail(),
                    resident.getFirstName(),
                    "Room " + roomLabel,
                    booking.getStartTime(),
                    ResourceSchedulePage.ROOMS
            ));
        }

        return expired.size();
    }

    @Transactional
    public int cancelAllExpiredBookings(int thresholdMinutes) {
        Instant now = Instant.now();
        int laundryCount = cancelExpiredLaundryBookings(now, thresholdMinutes);
        int roomCount = cancelExpiredRoomBookings(now, thresholdMinutes);
        int total = laundryCount + roomCount;

        if (total > 0) {
            log.info("Auto-cancelled {} unclaimed bookings past {}-min rule (laundry: {}, rooms: {})",
                    total, thresholdMinutes, laundryCount, roomCount);
        }

        return total;
    }
}

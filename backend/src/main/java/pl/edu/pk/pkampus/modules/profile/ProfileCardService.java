package pl.edu.pk.pkampus.modules.profile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.common.util.AcademicYear;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.profile.dto.CardDayDto;
import pl.edu.pk.pkampus.modules.profile.dto.ResidentCardDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileCardService {

    private static final int AVATAR_PRESIGN_MINUTES = 30;

    private final UserRepository userRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final CardVerificationService cardVerificationService;
    private final MinioStorageService minioStorageService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ResidentCardDto getResidentCard(User principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != UserRole.RESIDENT) {
            throw new BusinessRuleException("Resident card is only available to residents");
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new AccountStatusException("ACCOUNT_BLOCKED");
        }
        if (user.getStatus() == UserStatus.CHECKED_OUT) {
            throw new AccountStatusException("ACCOUNT_CHECKED_OUT");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountStatusException("ACCOUNT_NOT_ACTIVE");
        }

        String roomNumber = roomAssignmentRepository.findByUserIdAndIsActiveTrue(user.getId())
                .map(ra -> ra.getRoom().getRoomNumber())
                .orElse(user.getDeclaredRoomNumber());

        String avatarPresigned = null;
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
            try {
                avatarPresigned = minioStorageService.getAvatarPresignedUrl(
                        user.getAvatarUrl(), AVATAR_PRESIGN_MINUTES);
            } catch (Exception e) {
                log.warn("Failed to presign avatar for card of user {}", user.getId(), e);
            }
        }

        CardDayToken day = cardVerificationService.todaysToken();
        Instant now = Instant.now(clock);

        String dormitoryName = user.getDormitory() != null ? user.getDormitory().getName() : null;
        String dormitoryCode = user.getDormitory() != null ? user.getDormitory().getCode() : null;

        return ResidentCardDto.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .dormitoryName(dormitoryName)
                .dormitoryCode(dormitoryCode)
                .roomNumber(roomNumber)
                .academicYear(AcademicYear.current(LocalDate.now(clock.withZone(CardVerificationService.WARSAW))))
                .avatarUrl(avatarPresigned)
                .status(user.getStatus().name())
                .dayCode(day.dayCode())
                .dayColorHex(day.dayColorHex())
                .dayColorName(day.dayColorName())
                .serverTime(now)
                .build();
    }

    public CardDayDto getCardDay() {
        CardDayToken day = cardVerificationService.todaysToken();
        return CardDayDto.builder()
                .dayCode(day.dayCode())
                .dayColorHex(day.dayColorHex())
                .dayColorName(day.dayColorName())
                .validDate(day.validDate())
                .serverTime(Instant.now(clock))
                .build();
    }
}

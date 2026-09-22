package pl.edu.pk.pkampus.modules.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.profile.dto.CardDayDto;
import pl.edu.pk.pkampus.modules.profile.dto.ResidentCardDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileCardService POJO/Mockito unit tests (AAA)")
class ProfileCardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomAssignmentRepository roomAssignmentRepository;

    @Mock
    private CardVerificationService cardVerificationService;

    @Mock
    private MinioStorageService minioStorageService;

    private Clock fixedClock;
    private ProfileCardService profileCardService;

    private Dormitory dorm;
    private User resident;
    private CardDayToken dayToken;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), CardVerificationService.WARSAW);
        profileCardService = new ProfileCardService(
                userRepository,
                roomAssignmentRepository,
                cardVerificationService,
                minioStorageService,
                fixedClock
        );

        dorm = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Alfa")
                .code("ALF")
                .build();

        resident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .firstName("Jan")
                .lastName("Kowalski")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber("101A")
                .build();

        dayToken = new CardDayToken("A1B2C3", "#2563EB", "Niebieski", "2026-10-15");
    }

    @Test
    @DisplayName("getResidentCard throws ResourceNotFoundException when user is not found")
    void getResidentCardThrowsWhenUserNotFound() {
        // Arrange
        when(userRepository.findById(resident.getId())).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> profileCardService.getResidentCard(resident));
        assertTrue(ex.getMessage().contains("User not found"));
    }

    @Test
    @DisplayName("getResidentCard throws BusinessRuleException when user is not RESIDENT")
    void getResidentCardThrowsWhenNotResident() {
        // Arrange
        resident.setRole(UserRole.RECEPTIONIST);
        when(userRepository.findById(resident.getId())).thenReturn(Optional.of(resident));

        // Act & Assert
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> profileCardService.getResidentCard(resident));
        assertTrue(ex.getMessage().contains("only available to residents"));
    }

    @Test
    @DisplayName("getResidentCard throws AccountStatusException ACCOUNT_BLOCKED when status is BLOCKED")
    void getResidentCardThrowsWhenBlocked() {
        // Arrange
        resident.setStatus(UserStatus.BLOCKED);
        when(userRepository.findById(resident.getId())).thenReturn(Optional.of(resident));

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class,
                () -> profileCardService.getResidentCard(resident));
        assertEquals("ACCOUNT_BLOCKED", ex.getMessage());
    }

    @Test
    @DisplayName("getResidentCard throws AccountStatusException ACCOUNT_CHECKED_OUT when status is CHECKED_OUT")
    void getResidentCardThrowsWhenCheckedOut() {
        // Arrange
        resident.setStatus(UserStatus.CHECKED_OUT);
        when(userRepository.findById(resident.getId())).thenReturn(Optional.of(resident));

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class,
                () -> profileCardService.getResidentCard(resident));
        assertEquals("ACCOUNT_CHECKED_OUT", ex.getMessage());
    }

    @Test
    @DisplayName("getResidentCard throws AccountStatusException ACCOUNT_NOT_ACTIVE when status is PENDING_APPROVAL")
    void getResidentCardThrowsWhenNotActive() {
        // Arrange
        resident.setStatus(UserStatus.PENDING_APPROVAL);
        when(userRepository.findById(resident.getId())).thenReturn(Optional.of(resident));

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class,
                () -> profileCardService.getResidentCard(resident));
        assertEquals("ACCOUNT_NOT_ACTIVE", ex.getMessage());
    }

    @Test
    @DisplayName("getResidentCard returns card using declaredRoomNumber when no active room assignment exists")
    void getResidentCardWithDeclaredRoom() {
        // Arrange
        when(userRepository.findById(resident.getId())).thenReturn(Optional.of(resident));
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId())).thenReturn(Optional.empty());
        when(cardVerificationService.todaysToken()).thenReturn(dayToken);

        // Act
        ResidentCardDto card = profileCardService.getResidentCard(resident);

        // Assert
        assertNotNull(card);
        assertEquals("Jan", card.getFirstName());
        assertEquals("Kowalski", card.getLastName());
        assertEquals("DS Alfa", card.getDormitoryName());
        assertEquals("ALF", card.getDormitoryCode());
        assertEquals("101A", card.getRoomNumber());
        assertEquals("2026/2027", card.getAcademicYear());
        assertEquals("ACTIVE", card.getStatus());
        assertEquals("A1B2C3", card.getDayCode());
        assertEquals("#2563EB", card.getDayColorHex());
        assertEquals("Niebieski", card.getDayColorName());
        assertEquals(fixedClock.instant(), card.getServerTime());
        assertNull(card.getAvatarUrl());
    }

    @Test
    @DisplayName("getResidentCard returns card using active room assignment when available")
    void getResidentCardWithAssignedRoom() {
        // Arrange
        Room room = Room.builder()
                .id(UUID.randomUUID())
                .roomNumber("205B")
                .build();
        RoomAssignment assignment = RoomAssignment.builder()
                .id(UUID.randomUUID())
                .user(resident)
                .room(room)
                .isActive(true)
                .build();

        when(userRepository.findById(resident.getId())).thenReturn(Optional.of(resident));
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId())).thenReturn(Optional.of(assignment));
        when(cardVerificationService.todaysToken()).thenReturn(dayToken);

        // Act
        ResidentCardDto card = profileCardService.getResidentCard(resident);

        // Assert
        assertNotNull(card);
        assertEquals("205B", card.getRoomNumber());
    }

    @Test
    @DisplayName("getResidentCard presigns avatar URL when avatar is present")
    void getResidentCardWithAvatar() {
        // Arrange
        resident.setAvatarUrl("avatars/user-123.jpg");
        when(userRepository.findById(resident.getId())).thenReturn(Optional.of(resident));
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId())).thenReturn(Optional.empty());
        when(cardVerificationService.todaysToken()).thenReturn(dayToken);
        when(minioStorageService.getAvatarPresignedUrl("avatars/user-123.jpg", 30))
                .thenReturn("https://minio.pkampus.pl/avatars/user-123.jpg?token=abc");

        // Act
        ResidentCardDto card = profileCardService.getResidentCard(resident);

        // Assert
        assertNotNull(card);
        assertEquals("https://minio.pkampus.pl/avatars/user-123.jpg?token=abc", card.getAvatarUrl());
    }

    @Test
    @DisplayName("getResidentCard handles avatar presign exception gracefully")
    void getResidentCardHandlesPresignException() {
        // Arrange
        resident.setAvatarUrl("avatars/corrupted.jpg");
        when(userRepository.findById(resident.getId())).thenReturn(Optional.of(resident));
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId())).thenReturn(Optional.empty());
        when(cardVerificationService.todaysToken()).thenReturn(dayToken);
        when(minioStorageService.getAvatarPresignedUrl(eq("avatars/corrupted.jpg"), anyInt()))
                .thenThrow(new RuntimeException("MinIO unavailable"));

        // Act
        ResidentCardDto card = profileCardService.getResidentCard(resident);

        // Assert
        assertNotNull(card);
        assertNull(card.getAvatarUrl());
    }

    @Test
    @DisplayName("getResidentCard handles user without dormitory")
    void getResidentCardWithoutDormitory() {
        // Arrange
        resident.setDormitory(null);
        when(userRepository.findById(resident.getId())).thenReturn(Optional.of(resident));
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId())).thenReturn(Optional.empty());
        when(cardVerificationService.todaysToken()).thenReturn(dayToken);

        // Act
        ResidentCardDto card = profileCardService.getResidentCard(resident);

        // Assert
        assertNotNull(card);
        assertNull(card.getDormitoryName());
        assertNull(card.getDormitoryCode());
    }

    @Test
    @DisplayName("getCardDay returns CardDayDto with token data and serverTime")
    void getCardDayReturnsToken() {
        // Arrange
        when(cardVerificationService.todaysToken()).thenReturn(dayToken);

        // Act
        CardDayDto cardDay = profileCardService.getCardDay();

        // Assert
        assertNotNull(cardDay);
        assertEquals("A1B2C3", cardDay.getDayCode());
        assertEquals("#2563EB", cardDay.getDayColorHex());
        assertEquals("Niebieski", cardDay.getDayColorName());
        assertEquals("2026-10-15", cardDay.getValidDate());
        assertEquals(fixedClock.instant(), cardDay.getServerTime());
    }
}

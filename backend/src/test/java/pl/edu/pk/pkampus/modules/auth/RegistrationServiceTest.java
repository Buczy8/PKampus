package pl.edu.pk.pkampus.modules.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyEmailResponseDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.token.EmailTokenPayload;
import pl.edu.pk.pkampus.security.token.SignedEmailTokenService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DormitoryRepository dormitoryRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MinioStorageService minioStorageService;

    @Mock
    private SignedEmailTokenService signedEmailTokenService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RegistrationService registrationService;

    private Dormitory testDormitory;
    private User testUser;
    private RegisterRequestDto registerDto;
    private MockMultipartFile testPhoto;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<RegisterResponseDto> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        UUID dormId = UUID.randomUUID();
        testDormitory = Dormitory.builder()
                .id(dormId)
                .name("DS-1")
                .code("DS1")
                .build();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .passwordHash("hashedPass123!")
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("+48123456789")
                .avatarUrl("avatar-uuid.jpg")
                .role(UserRole.RESIDENT)
                .status(UserStatus.PENDING_EMAIL)
                .dormitory(testDormitory)
                .declaredRoomNumber("101")
                .build();

        registerDto = RegisterRequestDto.builder()
                .email("student@pk.edu.pl")
                .password("StrongPassword123!")
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("+48123456789")
                .dormitoryId(dormId)
                .declaredRoomNumber("101")
                .build();

        testPhoto = new MockMultipartFile(
                "photo",
                "avatar.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );
    }

    // ==========================================
    // Registration Tests
    // ==========================================

    @Test
    void shouldRegisterResidentSuccessfully() {
        // Arrange
        when(userRepository.existsByEmail("student@pk.edu.pl")).thenReturn(false);
        when(dormitoryRepository.findById(registerDto.getDormitoryId())).thenReturn(Optional.of(testDormitory));
        when(minioStorageService.validateAndDetectImageType(testPhoto)).thenReturn("image/jpeg");
        when(minioStorageService.uploadAvatar(testPhoto, "image/jpeg")).thenReturn("avatar-uuid.jpg");
        when(passwordEncoder.encode(registerDto.getPassword())).thenReturn("hashedPass123!");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        RegisterResponseDto response = registrationService.registerResident(registerDto, testPhoto);

        // Assert
        assertNotNull(response);
        assertEquals("student@pk.edu.pl", response.getEmail());
        assertEquals(RegistrationService.REGISTRATION_SUCCESS_MESSAGE, response.getMessage());
        ArgumentCaptor<ResidentRegisteredEvent> eventCaptor =
                ArgumentCaptor.forClass(ResidentRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(testUser.getId(), eventCaptor.getValue().userId());
        assertEquals("student@pk.edu.pl", eventCaptor.getValue().email());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldCompensateAndRemoveUploadedAvatarWhenDatabaseSaveFails() {
        // Arrange
        when(userRepository.existsByEmail("student@pk.edu.pl")).thenReturn(false);
        when(dormitoryRepository.findById(registerDto.getDormitoryId())).thenReturn(Optional.of(testDormitory));
        when(minioStorageService.validateAndDetectImageType(testPhoto)).thenReturn("image/jpeg");
        when(minioStorageService.uploadAvatar(testPhoto, "image/jpeg")).thenReturn("avatar-uuid.jpg");
        when(passwordEncoder.encode(registerDto.getPassword())).thenReturn("hashedPass123!");
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("DB error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> registrationService.registerResident(registerDto, testPhoto));
        verify(minioStorageService).removeAvatar("avatar-uuid.jpg");
    }

    @Test
    void shouldHandleAntiEnumerationWhenEmailAlreadyExists() {
        // Arrange
        when(minioStorageService.validateAndDetectImageType(testPhoto)).thenReturn("image/jpeg");
        when(userRepository.existsByEmail("student@pk.edu.pl")).thenReturn(true);

        // Act
        RegisterResponseDto response = registrationService.registerResident(registerDto, testPhoto);

        // Assert
        assertNotNull(response);
        assertEquals("student@pk.edu.pl", response.getEmail());
        assertEquals(RegistrationService.REGISTRATION_SUCCESS_MESSAGE, response.getMessage());
        verify(passwordEncoder).encode(registerDto.getPassword());
        verify(minioStorageService).validateAndDetectImageType(testPhoto);
        verify(minioStorageService, never()).uploadAvatar(any(), any());
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldThrowWhenDormitoryNotFoundDuringRegistration() {
        // Arrange
        when(minioStorageService.validateAndDetectImageType(testPhoto)).thenReturn("image/jpeg");
        when(userRepository.existsByEmail("student@pk.edu.pl")).thenReturn(false);
        when(dormitoryRepository.findById(registerDto.getDormitoryId())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> registrationService.registerResident(registerDto, testPhoto));
        verify(minioStorageService, never()).uploadAvatar(any(), any());
    }

    // ==========================================
    // Email Verification Tests
    // ==========================================

    @Test
    void shouldVerifyEmailSuccessfully() {
        // Arrange
        EmailTokenPayload payload = new EmailTokenPayload(testUser.getId(), testUser.getEmail(), Instant.now().plusSeconds(3600));
        when(signedEmailTokenService.verifyToken("valid-token")).thenReturn(payload);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        // Act
        VerifyEmailResponseDto response = registrationService.verifyEmail("valid-token");

        // Assert
        assertEquals(UserStatus.PENDING_APPROVAL, response.getStatus());
        assertEquals(UserStatus.PENDING_APPROVAL, testUser.getStatus());
        verify(userRepository).save(testUser);
    }

    @Test
    void shouldVerifyEmailWithoutChangingStatusWhenAlreadyActive() {
        // Arrange
        testUser.setStatus(UserStatus.ACTIVE);
        EmailTokenPayload payload = new EmailTokenPayload(testUser.getId(), testUser.getEmail(), Instant.now().plusSeconds(3600));
        when(signedEmailTokenService.verifyToken("valid-token")).thenReturn(payload);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        // Act
        VerifyEmailResponseDto response = registrationService.verifyEmail("valid-token");

        // Assert
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        assertEquals(UserStatus.ACTIVE, testUser.getStatus());
        verify(userRepository, never()).save(testUser);
    }

    @Test
    void shouldRejectVerifyEmailWhenUserNotFound() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        EmailTokenPayload payload = new EmailTokenPayload(unknownId, "unknown@pk.edu.pl", Instant.now().plusSeconds(3600));
        when(signedEmailTokenService.verifyToken("valid-token")).thenReturn(payload);
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> registrationService.verifyEmail("valid-token"));
    }

    @Test
    void shouldRejectVerifyEmailWhenEmailMismatched() {
        // Arrange
        EmailTokenPayload payload = new EmailTokenPayload(testUser.getId(), "different@pk.edu.pl", Instant.now().plusSeconds(3600));
        when(signedEmailTokenService.verifyToken("valid-token")).thenReturn(payload);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> registrationService.verifyEmail("valid-token"));
        assertTrue(ex.getMessage().contains("Email address mismatch"));
    }
}

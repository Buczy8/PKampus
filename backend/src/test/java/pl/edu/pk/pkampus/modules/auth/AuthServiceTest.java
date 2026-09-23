package pl.edu.pk.pkampus.modules.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.auth.AuthService;
import pl.edu.pk.pkampus.modules.auth.PasswordResetToken;
import pl.edu.pk.pkampus.modules.auth.PasswordResetTokenRepository;
import pl.edu.pk.pkampus.modules.auth.dto.AuthResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.ChangePasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.ForgotPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.ResetPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyEmailResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyResetTokenResponseDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;
import pl.edu.pk.pkampus.security.jwt.AuthenticatedUserCache;
import pl.edu.pk.pkampus.security.jwt.JwtService;
import pl.edu.pk.pkampus.security.jwt.RefreshTokenService;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;
import pl.edu.pk.pkampus.security.token.EmailTokenPayload;
import pl.edu.pk.pkampus.security.token.SignedEmailTokenService;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DormitoryRepository dormitoryRepository;

    @Mock
    private RoomAssignmentRepository roomAssignmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MinioStorageService minioStorageService;

    @Mock
    private SignedEmailTokenService signedEmailTokenService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @Mock
    private AuthenticatedUserCache authenticatedUserCache;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private AuthService authService;

    private Dormitory testDormitory;
    private User testUser;
    private RegisterRequestDto registerDto;
    private MockMultipartFile testPhoto;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenAnswer(invocation -> Instant.now());
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
        when(signedEmailTokenService.generateToken(testUser.getId(), testUser.getEmail())).thenReturn("signed-token-xyz");

        // Act
        RegisterResponseDto response = authService.registerResident(registerDto, testPhoto);

        // Assert
        assertNotNull(response);
        assertEquals("student@pk.edu.pl", response.getEmail());
        assertEquals(AuthService.REGISTRATION_SUCCESS_MESSAGE, response.getMessage());
        verify(emailService).sendVerificationEmail(eq("student@pk.edu.pl"), eq("signed-token-xyz"));
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
        assertThrows(RuntimeException.class, () -> authService.registerResident(registerDto, testPhoto));
        verify(minioStorageService).removeAvatar("avatar-uuid.jpg");
    }

    @Test
    void shouldHandleAntiEnumerationWhenEmailAlreadyExists() {
        // Arrange
        when(minioStorageService.validateAndDetectImageType(testPhoto)).thenReturn("image/jpeg");
        when(userRepository.existsByEmail("student@pk.edu.pl")).thenReturn(true);

        // Act
        RegisterResponseDto response = authService.registerResident(registerDto, testPhoto);

        // Assert
        assertNotNull(response);
        assertEquals("student@pk.edu.pl", response.getEmail());
        assertEquals(AuthService.REGISTRATION_SUCCESS_MESSAGE, response.getMessage());
        verify(passwordEncoder).encode(registerDto.getPassword());
        verify(minioStorageService).validateAndDetectImageType(testPhoto);
        verify(minioStorageService, never()).uploadAvatar(any(), any());
        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void shouldThrowWhenDormitoryNotFoundDuringRegistration() {
        // Arrange
        when(minioStorageService.validateAndDetectImageType(testPhoto)).thenReturn("image/jpeg");
        when(userRepository.existsByEmail("student@pk.edu.pl")).thenReturn(false);
        when(dormitoryRepository.findById(registerDto.getDormitoryId())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> authService.registerResident(registerDto, testPhoto));
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
        VerifyEmailResponseDto response = authService.verifyEmail("valid-token");

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
        VerifyEmailResponseDto response = authService.verifyEmail("valid-token");

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
        assertThrows(ResourceNotFoundException.class, () -> authService.verifyEmail("valid-token"));
    }

    @Test
    void shouldRejectVerifyEmailWhenEmailMismatched() {
        // Arrange
        EmailTokenPayload payload = new EmailTokenPayload(testUser.getId(), "different@pk.edu.pl", Instant.now().plusSeconds(3600));
        when(signedEmailTokenService.verifyToken("valid-token")).thenReturn(payload);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> authService.verifyEmail("valid-token"));
        assertTrue(ex.getMessage().contains("Email address mismatch"));
    }

    // ==========================================
    // Login Tests
    // ==========================================

    @Test
    void shouldLoginSuccessfullyWhenActive() {
        // Arrange
        testUser.setStatus(UserStatus.ACTIVE);
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "Password123!");

        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(testUser.getId())).thenReturn(Optional.empty());
        when(jwtService.generateToken(testUser, "101")).thenReturn("valid-jwt-token");
        when(jwtService.getExpirationMinutes()).thenReturn(15L);
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("valid-refresh-token");
        when(refreshTokenService.getRefreshExpirationSeconds()).thenReturn(604800L);

        // Act
        AuthResponseDto response = authService.login(loginDto);

        // Assert
        assertNotNull(response);
        assertEquals("valid-jwt-token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900L, response.getExpiresInSeconds());
        assertEquals("valid-refresh-token", response.getRefreshToken());
        assertEquals(604800L, response.getRefreshExpiresInSeconds());
        assertEquals("student@pk.edu.pl", response.getUser().getEmail());
        verify(tokenRevocationService).clearRevocation(testUser.getId());
        verify(refreshTokenService).revokeAllUserTokens(testUser.getId());
    }

    @Test
    void shouldLoginSuccessfullyWhenMustChangePassword() {
        // Arrange
        testUser.setStatus(UserStatus.MUST_CHANGE_PASSWORD);
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "Password123!");

        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(testUser.getId())).thenReturn(Optional.empty());
        when(jwtService.generateToken(testUser, "101")).thenReturn("valid-jwt-token");
        when(jwtService.getExpirationMinutes()).thenReturn(15L);
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("valid-refresh-token");
        when(refreshTokenService.getRefreshExpirationSeconds()).thenReturn(604800L);

        // Act
        AuthResponseDto response = authService.login(loginDto);

        // Assert
        assertEquals(UserStatus.MUST_CHANGE_PASSWORD, response.getUser().getStatus());
        assertEquals("valid-jwt-token", response.getToken());
    }

    @Test
    void shouldLoginWithAssignedRoomNumberWhenActiveRoomAssignmentExists() {
        // Arrange
        testUser.setStatus(UserStatus.ACTIVE);
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "Password123!");
        Room room = Room.builder().roomNumber("205").build();
        RoomAssignment assignment = RoomAssignment.builder().room(room).isActive(true).build();

        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(testUser.getId())).thenReturn(Optional.of(assignment));
        when(jwtService.generateToken(testUser, "205")).thenReturn("valid-jwt-token");
        when(jwtService.getExpirationMinutes()).thenReturn(15L);
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("valid-refresh-token");
        when(refreshTokenService.getRefreshExpirationSeconds()).thenReturn(604800L);

        // Act
        AuthResponseDto response = authService.login(loginDto);

        // Assert
        assertNotNull(response);
        assertEquals("205", response.getUser().getRoomNumber());
        verify(jwtService).generateToken(testUser, "205");
    }

    @Test
    void shouldRejectLoginWhenUserNotFound() {
        // Arrange
        LoginRequestDto loginDto = new LoginRequestDto("nonexistent@pk.edu.pl", "Password123!");
        when(userRepository.findByEmail("nonexistent@pk.edu.pl")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authService.login(loginDto));
    }

    @Test
    void shouldRejectLoginWhenPasswordIsIncorrect() {
        // Arrange
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "WrongPassword!");
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword!", testUser.getPasswordHash())).thenReturn(false);

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authService.login(loginDto));
    }

    @Test
    void shouldRejectLoginWhenStatusIsPendingEmail() {
        // Arrange
        testUser.setStatus(UserStatus.PENDING_EMAIL);
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "Password123!");
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> authService.login(loginDto));
        assertTrue(ex.getMessage().contains("confirm your email address"));
    }

    @Test
    void shouldRejectLoginWhenStatusIsPendingApproval() {
        // Arrange
        testUser.setStatus(UserStatus.PENDING_APPROVAL);
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "Password123!");
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> authService.login(loginDto));
        assertTrue(ex.getMessage().contains("awaiting residency approval"));
    }

    @Test
    void shouldRejectLoginWhenStatusIsBlocked() {
        // Arrange
        testUser.setStatus(UserStatus.BLOCKED);
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "Password123!");
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> authService.login(loginDto));
        assertTrue(ex.getMessage().contains("suspended"));
    }

    @Test
    void shouldRejectLoginWhenStatusIsCheckedOut() {
        // Arrange
        testUser.setStatus(UserStatus.CHECKED_OUT);
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "Password123!");
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> authService.login(loginDto));
        assertTrue(ex.getMessage().contains("expired (checked out)"));
    }

    // ==========================================
    // Token Refresh Tests
    // ==========================================

    @Test
    void shouldRefreshTokenSuccessfully() {
        // Arrange
        testUser.setStatus(UserStatus.ACTIVE);
        when(refreshTokenService.rotateRefreshToken("raw-refresh-token"))
                .thenReturn(new RefreshTokenService.RefreshTokenResult("new-raw-token", testUser));
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(testUser.getId())).thenReturn(Optional.empty());
        when(jwtService.generateToken(testUser, "101")).thenReturn("new-jwt-token");
        when(jwtService.getExpirationMinutes()).thenReturn(15L);
        when(refreshTokenService.getRefreshExpirationSeconds()).thenReturn(604800L);

        // Act
        AuthResponseDto response = authService.refreshToken("raw-refresh-token");

        // Assert
        assertNotNull(response);
        assertEquals("new-jwt-token", response.getToken());
        assertEquals("new-raw-token", response.getRefreshToken());
        assertEquals(604800L, response.getRefreshExpiresInSeconds());
        verify(refreshTokenService).rotateRefreshToken("raw-refresh-token");
    }

    // ==========================================
    // Logout Tests
    // ==========================================

    @Test
    void shouldLogoutSuccessfully() {
        // Arrange
        when(refreshTokenService.revokeRefreshToken("some-refresh-token"))
                .thenReturn(Optional.of(testUser.getId()));

        // Act
        authService.logout(testUser.getId(), "some-refresh-token");

        // Assert
        verify(tokenRevocationService).revokeUser(testUser.getId());
        verify(refreshTokenService).revokeRefreshToken("some-refresh-token");
        verify(tokenRevocationService, never()).blacklistAccessToken(any());
    }

    @Test
    void shouldLogoutWithRefreshOnlyAndBlacklistAccess() {
        // Arrange
        when(refreshTokenService.revokeRefreshToken("some-refresh-token"))
                .thenReturn(Optional.of(testUser.getId()));

        // Act
        authService.logout(null, "some-refresh-token");

        // Assert
        verify(tokenRevocationService, never()).revokeUser(any());
        verify(tokenRevocationService).blacklistAccessToken(testUser.getId());
    }

    @Test
    void shouldRejectLogoutWithoutCredentials() {
        // Arrange & Act & Assert
        assertThrows(IllegalArgumentException.class, () -> authService.logout(null, null));
        assertThrows(IllegalArgumentException.class, () -> authService.logout(null, "  "));
    }

    // ==========================================
    // Current User Profile Tests
    // ==========================================

    @Test
    void shouldGetCurrentUserProfileWithDeclaredRoom() {
        // Arrange
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(testUser.getId())).thenReturn(Optional.empty());

        // Act
        UserProfileDto profile = authService.getCurrentUserProfile(testUser.getId());

        // Assert
        assertNotNull(profile);
        assertEquals(testUser.getId(), profile.getId());
        assertEquals("101", profile.getRoomNumber());
        assertEquals("DS-1", profile.getDormitoryName());
    }

    @Test
    void shouldGetCurrentUserProfileWithAssignedRoom() {
        // Arrange
        Room room = Room.builder().roomNumber("303").build();
        RoomAssignment assignment = RoomAssignment.builder().room(room).isActive(true).build();
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(testUser.getId())).thenReturn(Optional.of(assignment));

        // Act
        UserProfileDto profile = authService.getCurrentUserProfile(testUser.getId());

        // Assert
        assertNotNull(profile);
        assertEquals("303", profile.getRoomNumber());
    }

    @Test
    void shouldRejectGetCurrentUserProfileWhenUserNotFound() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> authService.getCurrentUserProfile(unknownId));
    }

    // ==========================================
    // Change Password Tests
    // ==========================================

    @Test
    void shouldChangePasswordAndActivateMustChangePasswordAccount() {
        // Arrange
        testUser.setStatus(UserStatus.MUST_CHANGE_PASSWORD);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches("NewPassword1!", testUser.getPasswordHash())).thenReturn(false);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("new-hash");
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(testUser.getId())).thenReturn(Optional.empty());

        // Act
        var profile = authService.changePassword(
                testUser,
                ChangePasswordRequestDto.builder()
                        .currentPassword("Password123!")
                        .newPassword("NewPassword1!")
                        .build()
        );

        // Assert
        assertEquals(UserStatus.ACTIVE, testUser.getStatus());
        assertEquals(UserStatus.ACTIVE, profile.getStatus());
        assertEquals("new-hash", testUser.getPasswordHash());
        verify(authenticatedUserCache).invalidate(testUser.getId());
    }

    @Test
    void shouldChangePasswordWithoutChangingActiveStatus() {
        // Arrange
        testUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches("NewPassword1!", testUser.getPasswordHash())).thenReturn(false);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("new-hash");
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(testUser.getId())).thenReturn(Optional.empty());

        // Act
        var profile = authService.changePassword(
                testUser,
                ChangePasswordRequestDto.builder()
                        .currentPassword("Password123!")
                        .newPassword("NewPassword1!")
                        .build()
        );

        // Assert
        assertEquals(UserStatus.ACTIVE, testUser.getStatus());
        assertEquals(UserStatus.ACTIVE, profile.getStatus());
    }

    @Test
    void shouldRejectChangePasswordWhenCurrentPasswordWrong() {
        // Arrange
        testUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Wrong!", testUser.getPasswordHash())).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> authService.changePassword(
                testUser,
                ChangePasswordRequestDto.builder()
                        .currentPassword("Wrong!")
                        .newPassword("NewPassword1!")
                        .build()
        ));
    }

    @Test
    void shouldRejectChangePasswordWhenNewPasswordEqualsCurrent() {
        // Arrange
        testUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("SamePassword1!", testUser.getPasswordHash())).thenReturn(true);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.changePassword(
                testUser,
                ChangePasswordRequestDto.builder()
                        .currentPassword("SamePassword1!")
                        .newPassword("SamePassword1!")
                        .build()
        ));
        assertTrue(ex.getMessage().contains("different from the current password"));
    }

    @Test
    void shouldRejectChangePasswordWhenUserNotFound() {
        // Arrange
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> authService.changePassword(
                testUser,
                ChangePasswordRequestDto.builder()
                        .currentPassword("Password123!")
                        .newPassword("NewPassword1!")
                        .build()
        ));
    }

    @Test
    void shouldRejectChangePasswordWhenStatusIsBlocked() {
        // Arrange
        testUser.setStatus(UserStatus.BLOCKED);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(AccountStatusException.class, () -> authService.changePassword(
                testUser,
                ChangePasswordRequestDto.builder()
                        .currentPassword("Password123!")
                        .newPassword("NewPassword1!")
                        .build()
        ));
    }

    // ==========================================
    // Forgot Password Tests
    // ==========================================

    @Test
    void shouldInitiatePasswordResetSuccessfullyForActiveUser() {
        // Arrange
        testUser.setStatus(UserStatus.ACTIVE);
        ForgotPasswordRequestDto dto = new ForgotPasswordRequestDto("student@pk.edu.pl");
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));

        // Act
        String message = authService.initiatePasswordReset(dto);

        // Assert
        assertEquals(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE, message);
        verify(passwordResetTokenRepository).invalidateAllActiveForUser(eq(testUser.getId()), any(Instant.class));
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertEquals(testUser, savedToken.getUser());
        assertNotNull(savedToken.getTokenHash());
        assertTrue(savedToken.getExpiresAt().isAfter(Instant.now()));
        verify(emailService).sendPasswordResetEmail(eq("student@pk.edu.pl"), eq("Jan"), anyString());
    }

    @Test
    void shouldHandleAntiEnumerationWhenPasswordResetRequestedForNonExistentEmail() {
        // Arrange
        ForgotPasswordRequestDto dto = new ForgotPasswordRequestDto("nonexistent@pk.edu.pl");
        when(userRepository.findByEmail("nonexistent@pk.edu.pl")).thenReturn(Optional.empty());

        // Act
        String message = authService.initiatePasswordReset(dto);

        // Assert
        assertEquals(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE, message);
        verify(passwordResetTokenRepository, never()).invalidateAllActiveForUser(any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void shouldHandleAntiEnumerationWhenPasswordResetRequestedForBlockedUser() {
        // Arrange
        testUser.setStatus(UserStatus.BLOCKED);
        ForgotPasswordRequestDto dto = new ForgotPasswordRequestDto("student@pk.edu.pl");
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));

        // Act
        String message = authService.initiatePasswordReset(dto);

        // Assert
        assertEquals(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE, message);
        verify(passwordResetTokenRepository, never()).invalidateAllActiveForUser(any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void shouldHandleAntiEnumerationWhenPasswordResetRequestedForCheckedOutUser() {
        // Arrange
        testUser.setStatus(UserStatus.CHECKED_OUT);
        ForgotPasswordRequestDto dto = new ForgotPasswordRequestDto("student@pk.edu.pl");
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));

        // Act
        String message = authService.initiatePasswordReset(dto);

        // Assert
        assertEquals(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE, message);
        verify(passwordResetTokenRepository, never()).invalidateAllActiveForUser(any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    // ==========================================
    // Verify Password Reset Token Tests
    // ==========================================

    @Test
    void shouldVerifyResetTokenSuccessfully() {
        // Arrange
        String rawToken = "abcd1234ef567890abcd1234ef567890abcd1234ef567890abcd1234ef567890";
        String tokenHash = hashSha256(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act
        VerifyResetTokenResponseDto response = authService.verifyResetToken(rawToken);

        // Assert
        assertNotNull(response);
        assertTrue(response.isValid());
        assertEquals("s***t@pk.edu.pl", response.getMaskedEmail());
    }

    @Test
    void shouldVerifyResetTokenWithShortEmailPrefix() {
        // Arrange
        User shortEmailUser = User.builder()
                .id(UUID.randomUUID())
                .email("ab@pk.edu.pl")
                .build();
        String rawToken = "my-token-123";
        String tokenHash = hashSha256(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(shortEmailUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act
        VerifyResetTokenResponseDto response = authService.verifyResetToken(rawToken);

        // Assert
        assertNotNull(response);
        assertTrue(response.isValid());
        assertEquals("a***@pk.edu.pl", response.getMaskedEmail());
    }

    @Test
    void shouldRejectVerifyResetTokenWhenTokenIsBlankOrNull() {
        // Arrange & Act & Assert
        assertThrows(IllegalArgumentException.class, () -> authService.verifyResetToken(null));
        assertThrows(IllegalArgumentException.class, () -> authService.verifyResetToken("   "));
    }

    @Test
    void shouldRejectVerifyResetTokenWhenTokenNotFound() {
        // Arrange
        String rawToken = "unknown-token";
        String tokenHash = hashSha256(rawToken);
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> authService.verifyResetToken(rawToken));
    }

    @Test
    void shouldRejectVerifyResetTokenWhenTokenExpired() {
        // Arrange
        String rawToken = "expired-token";
        String tokenHash = hashSha256(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.verifyResetToken(rawToken));
        assertTrue(ex.getMessage().contains("expired"));
    }

    // ==========================================
    // Reset Password Tests
    // ==========================================

    @Test
    void shouldResetPasswordSuccessfully() {
        // Arrange
        testUser.setStatus(UserStatus.ACTIVE);
        String rawToken = "valid-token-for-reset";
        String tokenHash = hashSha256(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPass1234!")).thenReturn("newEncodedHash");

        // Act
        authService.resetPassword(dto);

        // Assert
        assertEquals("newEncodedHash", testUser.getPasswordHash());
        assertEquals(UserStatus.ACTIVE, testUser.getStatus());
        assertNotNull(resetToken.getUsedAt());
        verify(userRepository).save(testUser);
        verify(passwordResetTokenRepository).save(resetToken);
        verify(tokenRevocationService).revokeUser(testUser.getId());
    }

    @Test
    void shouldResetPasswordAndActivateMustChangePasswordUser() {
        // Arrange
        testUser.setStatus(UserStatus.MUST_CHANGE_PASSWORD);
        String rawToken = "valid-token-for-reset";
        String tokenHash = hashSha256(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPass1234!")).thenReturn("newEncodedHash");

        // Act
        authService.resetPassword(dto);

        // Assert
        assertEquals(UserStatus.ACTIVE, testUser.getStatus());
        verify(userRepository).save(testUser);
        verify(passwordResetTokenRepository).save(resetToken);
        verify(tokenRevocationService).revokeUser(testUser.getId());
    }

    @Test
    void shouldRejectResetPasswordWhenTokenNotFound() {
        // Arrange
        String rawToken = "nonexistent-token";
        String tokenHash = hashSha256(rawToken);
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> authService.resetPassword(dto));
    }

    @Test
    void shouldRejectResetPasswordWhenTokenExpired() {
        // Arrange
        String rawToken = "expired-token";
        String tokenHash = hashSha256(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(dto));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectResetPasswordWhenUserIsBlocked() {
        // Arrange
        testUser.setStatus(UserStatus.BLOCKED);
        String rawToken = "valid-token";
        String tokenHash = hashSha256(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> authService.resetPassword(dto));
        assertTrue(ex.getMessage().contains("suspended or checked out"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectResetPasswordWhenUserIsCheckedOut() {
        // Arrange
        testUser.setStatus(UserStatus.CHECKED_OUT);
        String rawToken = "valid-token";
        String tokenHash = hashSha256(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> authService.resetPassword(dto));
        assertTrue(ex.getMessage().contains("suspended or checked out"));
        verify(userRepository, never()).save(any());
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private static String hashSha256(String input) {
        return ResetTokenSupport.sha256Hex(input);
    }
}

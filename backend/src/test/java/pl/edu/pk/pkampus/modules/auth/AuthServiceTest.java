package pl.edu.pk.pkampus.modules.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.auth.dto.AuthResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.ChangePasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
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
    private RoomAssignmentRepository roomAssignmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @Mock
    private AuthenticatedUserCache authenticatedUserCache;

    @InjectMocks
    private AuthService authService;

    private Dormitory testDormitory;
    private User testUser;

    @BeforeEach
    void setUp() {
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
        when(jwtService.generateToken(testUser)).thenReturn("valid-jwt-token");
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
        when(jwtService.generateToken(testUser)).thenReturn("valid-jwt-token");
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
        when(jwtService.generateToken(testUser)).thenReturn("valid-jwt-token");
        when(jwtService.getExpirationMinutes()).thenReturn(15L);
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("valid-refresh-token");
        when(refreshTokenService.getRefreshExpirationSeconds()).thenReturn(604800L);

        // Act
        AuthResponseDto response = authService.login(loginDto);

        // Assert
        assertNotNull(response);
        assertEquals("205", response.getUser().getRoomNumber());
        verify(jwtService).generateToken(testUser);
    }

    @Test
    void shouldRejectLoginWhenUserNotFound() {
        // Arrange
        LoginRequestDto loginDto = new LoginRequestDto("nonexistent@pk.edu.pl", "Password123!");
        when(userRepository.findByEmail("nonexistent@pk.edu.pl")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authService.login(loginDto));
        // Timing equalization: unknown emails cost a hash verification like existing ones
        verify(passwordEncoder).matches(eq("Password123!"), anyString());
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
        when(jwtService.generateToken(testUser)).thenReturn("new-jwt-token");
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
        verify(tokenRevocationService).revokeUser(testUser.getId());
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
        verify(tokenRevocationService).revokeUser(testUser.getId());
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

}

package pl.edu.pk.pkampus.modules.auth.passwordreset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.auth.dto.ForgotPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.ResetPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyResetTokenResponseDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;

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
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @Mock
    private Clock clock;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private Dormitory testDormitory;
    private User testUser;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenAnswer(invocation -> Instant.now());
        testDormitory = Dormitory.builder()
                .id(UUID.randomUUID())
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
    // Forgot Password Tests
    // ==========================================

    @Test
    void shouldInitiatePasswordResetSuccessfullyForActiveUser() {
        // Arrange
        testUser.setStatus(UserStatus.ACTIVE);
        ForgotPasswordRequestDto dto = new ForgotPasswordRequestDto("student@pk.edu.pl");
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));

        // Act
        passwordResetService.initiatePasswordReset(dto);

        // Assert
        verify(passwordResetTokenRepository).invalidateAllActiveForUser(eq(testUser.getId()), any(Instant.class));
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertEquals(testUser, savedToken.getUser());
        assertNotNull(savedToken.getTokenHash());
        assertTrue(savedToken.getExpiresAt().isAfter(Instant.now()));
        ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("student@pk.edu.pl", eventCaptor.getValue().email());
        assertEquals("Jan", eventCaptor.getValue().firstName());
        assertNotNull(eventCaptor.getValue().rawToken());
    }

    @Test
    void shouldHandleAntiEnumerationWhenPasswordResetRequestedForNonExistentEmail() {
        // Arrange
        ForgotPasswordRequestDto dto = new ForgotPasswordRequestDto("nonexistent@pk.edu.pl");
        when(userRepository.findByEmail("nonexistent@pk.edu.pl")).thenReturn(Optional.empty());

        // Act
        passwordResetService.initiatePasswordReset(dto);

        // Assert
        verify(passwordResetTokenRepository, never()).invalidateAllActiveForUser(any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldHandleAntiEnumerationWhenPasswordResetRequestedForBlockedUser() {
        // Arrange
        testUser.setStatus(UserStatus.BLOCKED);
        ForgotPasswordRequestDto dto = new ForgotPasswordRequestDto("student@pk.edu.pl");
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));

        // Act
        passwordResetService.initiatePasswordReset(dto);

        // Assert
        verify(passwordResetTokenRepository, never()).invalidateAllActiveForUser(any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldHandleAntiEnumerationWhenPasswordResetRequestedForCheckedOutUser() {
        // Arrange
        testUser.setStatus(UserStatus.CHECKED_OUT);
        ForgotPasswordRequestDto dto = new ForgotPasswordRequestDto("student@pk.edu.pl");
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));

        // Act
        passwordResetService.initiatePasswordReset(dto);

        // Assert
        verify(passwordResetTokenRepository, never()).invalidateAllActiveForUser(any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ==========================================
    // Verify Password Reset Token Tests
    // ==========================================

    @Test
    void shouldVerifyResetTokenSuccessfully() {
        // Arrange
        String rawToken = "abcd1234ef567890abcd1234ef567890abcd1234ef567890abcd1234ef567890";
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act
        VerifyResetTokenResponseDto response = passwordResetService.verifyResetToken(rawToken);

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
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(shortEmailUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act
        VerifyResetTokenResponseDto response = passwordResetService.verifyResetToken(rawToken);

        // Assert
        assertNotNull(response);
        assertTrue(response.isValid());
        assertEquals("a***@pk.edu.pl", response.getMaskedEmail());
    }

    @Test
    void shouldRejectVerifyResetTokenWhenTokenIsBlankOrNull() {
        // Arrange & Act & Assert
        assertThrows(IllegalArgumentException.class, () -> passwordResetService.verifyResetToken(null));
        assertThrows(IllegalArgumentException.class, () -> passwordResetService.verifyResetToken("   "));
    }

    @Test
    void shouldRejectVerifyResetTokenWhenTokenNotFound() {
        // Arrange
        String rawToken = "unknown-token";
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> passwordResetService.verifyResetToken(rawToken));
    }

    @Test
    void shouldRejectVerifyResetTokenWhenTokenExpired() {
        // Arrange
        String rawToken = "expired-token";
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> passwordResetService.verifyResetToken(rawToken));
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
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordResetTokenRepository.markUsedIfUnused(eq(resetToken.getId()), any(Instant.class))).thenReturn(1);
        when(passwordEncoder.encode("NewPass1234!")).thenReturn("newEncodedHash");

        // Act
        passwordResetService.resetPassword(dto);

        // Assert
        assertEquals("newEncodedHash", testUser.getPasswordHash());
        assertEquals(UserStatus.ACTIVE, testUser.getStatus());
        verify(userRepository).save(testUser);
        verify(passwordResetTokenRepository).markUsedIfUnused(eq(resetToken.getId()), any(Instant.class));
        verify(tokenRevocationService).revokeUser(testUser.getId());
    }

    @Test
    void shouldResetPasswordAndActivateMustChangePasswordUser() {
        // Arrange
        testUser.setStatus(UserStatus.MUST_CHANGE_PASSWORD);
        String rawToken = "valid-token-for-reset";
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordResetTokenRepository.markUsedIfUnused(eq(resetToken.getId()), any(Instant.class))).thenReturn(1);
        when(passwordEncoder.encode("NewPass1234!")).thenReturn("newEncodedHash");

        // Act
        passwordResetService.resetPassword(dto);

        // Assert
        assertEquals(UserStatus.ACTIVE, testUser.getStatus());
        verify(userRepository).save(testUser);
        verify(passwordResetTokenRepository).markUsedIfUnused(eq(resetToken.getId()), any(Instant.class));
        verify(tokenRevocationService).revokeUser(testUser.getId());
    }

    @Test
    void shouldRejectResetPasswordWhenTokenNotFound() {
        // Arrange
        String rawToken = "nonexistent-token";
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> passwordResetService.resetPassword(dto));
    }

    @Test
    void shouldRejectResetPasswordWhenTokenExpired() {
        // Arrange
        String rawToken = "expired-token";
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> passwordResetService.resetPassword(dto));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectResetPasswordWhenUserIsBlocked() {
        // Arrange
        testUser.setStatus(UserStatus.BLOCKED);
        String rawToken = "valid-token";
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> passwordResetService.resetPassword(dto));
        assertTrue(ex.getMessage().contains("not eligible for password reset"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectResetPasswordWhenUserIsCheckedOut() {
        // Arrange
        testUser.setStatus(UserStatus.CHECKED_OUT);
        String rawToken = "valid-token";
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act & Assert
        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> passwordResetService.resetPassword(dto));
        assertTrue(ex.getMessage().contains("not eligible for password reset"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectResetPasswordWhenTokenConsumedConcurrently() {
        // Arrange
        testUser.setStatus(UserStatus.ACTIVE);
        String rawToken = "consumed-token";
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(rawToken, "NewPass1234!");
        when(passwordResetTokenRepository.findActiveByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordResetTokenRepository.markUsedIfUnused(eq(resetToken.getId()), any(Instant.class))).thenReturn(0);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> passwordResetService.resetPassword(dto));
        verify(userRepository, never()).save(any());
        verify(tokenRevocationService, never()).revokeUser(any());
    }
}

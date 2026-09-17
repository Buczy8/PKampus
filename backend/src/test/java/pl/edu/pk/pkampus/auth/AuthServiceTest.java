package pl.edu.pk.pkampus.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.edu.pk.pkampus.modules.auth.AuthService;
import pl.edu.pk.pkampus.modules.auth.dto.AuthResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyEmailResponseDto;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.security.token.SignedEmailTokenService;
import pl.edu.pk.pkampus.security.token.EmailTokenPayload;
import pl.edu.pk.pkampus.security.jwt.JwtService;
import pl.edu.pk.pkampus.mail.EmailService;

import java.time.Instant;
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
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private Dormitory testDormitory;
    private User testUser;
    private RegisterRequestDto registerDto;
    private MockMultipartFile testPhoto;

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

    @Test
    void shouldRegisterResidentSuccessfully() {
        when(userRepository.existsByEmail("student@pk.edu.pl")).thenReturn(false);
        when(dormitoryRepository.findById(registerDto.getDormitoryId())).thenReturn(Optional.of(testDormitory));
        when(minioStorageService.uploadAvatar(testPhoto)).thenReturn("avatar-uuid.jpg");
        when(passwordEncoder.encode(registerDto.getPassword())).thenReturn("hashedPass123!");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(signedEmailTokenService.generateToken(testUser.getId(), testUser.getEmail())).thenReturn("signed-token-xyz");

        RegisterResponseDto response = authService.registerResident(registerDto, testPhoto);

        assertNotNull(response);
        assertEquals("student@pk.edu.pl", response.getEmail());
        verify(emailService).sendVerificationEmail(eq("student@pk.edu.pl"), eq("signed-token-xyz"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldHandleAntiEnumerationWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("student@pk.edu.pl")).thenReturn(true);

        RegisterResponseDto response = authService.registerResident(registerDto, testPhoto);

        assertNotNull(response);
        verify(minioStorageService, never()).uploadAvatar(any());
        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void shouldVerifyEmailSuccessfully() {
        EmailTokenPayload payload = new EmailTokenPayload(testUser.getId(), testUser.getEmail(), Instant.now().plusSeconds(3600));
        when(signedEmailTokenService.verifyToken("valid-token")).thenReturn(payload);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        VerifyEmailResponseDto response = authService.verifyEmail("valid-token");

        assertEquals(UserStatus.PENDING_APPROVAL, response.getStatus());
        assertEquals(UserStatus.PENDING_APPROVAL, testUser.getStatus());
        verify(userRepository).save(testUser);
    }

    @Test
    void shouldLoginSuccessfullyWhenActive() {
        testUser.setStatus(UserStatus.ACTIVE);
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "Password123!");

        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(testUser.getId())).thenReturn(Optional.empty());
        when(jwtService.generateToken(testUser, "101")).thenReturn("valid-jwt-token");
        when(jwtService.getExpirationMinutes()).thenReturn(15L);

        AuthResponseDto response = authService.login(loginDto);

        assertNotNull(response);
        assertEquals("valid-jwt-token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900L, response.getExpiresInSeconds());
        assertEquals("student@pk.edu.pl", response.getUser().getEmail());
    }

    @Test
    void shouldRejectLoginWhenPasswordIsIncorrect() {
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "WrongPassword!");

        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword!", testUser.getPasswordHash())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(loginDto));
    }

    @Test
    void shouldRejectLoginWhenStatusIsPendingApproval() {
        testUser.setStatus(UserStatus.PENDING_APPROVAL);
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "Password123!");

        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);

        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> authService.login(loginDto));
        assertTrue(ex.getMessage().contains("oczekuje na weryfikację meldunku"));
    }

    @Test
    void shouldRejectLoginWhenStatusIsBlocked() {
        testUser.setStatus(UserStatus.BLOCKED);
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "Password123!");

        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", testUser.getPasswordHash())).thenReturn(true);

        AccountStatusException ex = assertThrows(AccountStatusException.class, () -> authService.login(loginDto));
        assertTrue(ex.getMessage().contains("zablokowane"));
    }
}

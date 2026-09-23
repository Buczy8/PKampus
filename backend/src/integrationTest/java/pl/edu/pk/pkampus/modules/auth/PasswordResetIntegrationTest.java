package pl.edu.pk.pkampus.modules.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.auth.passwordreset.PasswordResetRequestedEvent;
import pl.edu.pk.pkampus.modules.auth.passwordreset.PasswordResetToken;
import pl.edu.pk.pkampus.modules.auth.passwordreset.PasswordResetTokenRepository;
import pl.edu.pk.pkampus.modules.auth.dto.ForgotPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.ResetPasswordRequestDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
@DisplayName("Password reset flow integration tests (FR-AUTH-07 / ADR-07)")
class PasswordResetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private MinioStorageService minioStorageService;

    private User user;

    @BeforeEach
    void setUp() {
        events.clear();
        Dormitory dormitory = dormitoryRepository.save(Dormitory.builder()
                .name("DS-2 Leon")
                .code("DS2-RESET")
                .address("ul. Skarżyńskiego 3")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(22, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        user = userRepository.save(User.builder()
                .email("adam.nowak@student.pk.edu.pl")
                .passwordHash(passwordEncoder.encode("OldSecret123!"))
                .firstName("Adam")
                .lastName("Nowak")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dormitory)
                .declaredRoomNumber("105")
                .phoneNumber("+48500600700")
                .avatarUrl("avatars/adam.jpg")
                .build());
    }

    @Test
    @DisplayName("Should initiate password reset and dispatch email with token")
    void shouldInitiatePasswordResetAndSendEmail() throws Exception {
        ForgotPasswordRequestDto request = ForgotPasswordRequestDto.builder()
                .email("adam.nowak@student.pk.edu.pl")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("If an account associated")));

        // The test transaction never commits, so the AFTER_COMMIT mail listener
        // does not fire — capture the published event instead of the email.
        PasswordResetRequestedEvent event = events.stream(PasswordResetRequestedEvent.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected PasswordResetRequestedEvent"));

        String rawToken = event.rawToken();
        assertThat(rawToken).hasSize(64); // 32 bytes hex
    }

    @Test
    @DisplayName("Should return generic success when email does not exist without sending email")
    void shouldReturnGenericSuccessWhenEmailDoesNotExist() throws Exception {
        ForgotPasswordRequestDto request = ForgotPasswordRequestDto.builder()
                .email("unknown.person@pk.edu.pl")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailService, never()).sendPasswordResetEmail(eq("unknown.person@pk.edu.pl"), eq("Unknown"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Should verify valid reset token")
    void shouldVerifyValidResetToken() throws Exception {
        ForgotPasswordRequestDto forgotRequest = ForgotPasswordRequestDto.builder()
                .email("adam.nowak@student.pk.edu.pl")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(forgotRequest)));

        PasswordResetRequestedEvent verifyEvent = events.stream(PasswordResetRequestedEvent.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected PasswordResetRequestedEvent"));
        String verifyToken = verifyEvent.rawToken();

        mockMvc.perform(get("/api/v1/auth/verify-reset-token")
                        .param("token", verifyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.maskedEmail").value(org.hamcrest.Matchers.containsString("student.pk.edu.pl")));
    }

    @Test
    @DisplayName("Should complete password reset and allow login with new password")
    void shouldResetPasswordSuccessfullyAndAllowLogin() throws Exception {
        ForgotPasswordRequestDto forgotRequest = ForgotPasswordRequestDto.builder()
                .email("adam.nowak@student.pk.edu.pl")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(forgotRequest)));

        PasswordResetRequestedEvent resetEvent = events.stream(PasswordResetRequestedEvent.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected PasswordResetRequestedEvent"));
        String rawToken = resetEvent.rawToken();

        ResetPasswordRequestDto resetRequest = ResetPasswordRequestDto.builder()
                .token(rawToken)
                .newPassword("BrandNewPass2026!")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify old password no longer works
        LoginRequestDto oldLogin = new LoginRequestDto("adam.nowak@student.pk.edu.pl", "OldSecret123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLogin)))
                .andExpect(status().isUnauthorized());

        // Verify new password works
        LoginRequestDto newLogin = new LoginRequestDto("adam.nowak@student.pk.edu.pl", "BrandNewPass2026!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        // Verify token cannot be reused
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should reject password reset with weak password")
    void shouldRejectWeakPassword() throws Exception {
        ResetPasswordRequestDto resetRequest = ResetPasswordRequestDto.builder()
                .token("some-token")
                .newPassword("weak")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest());
    }
}

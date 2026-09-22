package pl.edu.pk.pkampus.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.edu.pk.pkampus.modules.auth.AuthController;
import pl.edu.pk.pkampus.modules.auth.AuthService;
import pl.edu.pk.pkampus.modules.auth.dto.AuthResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.ChangePasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.ForgotPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RefreshTokenRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.ResetPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyEmailResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyResetTokenResponseDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;
import pl.edu.pk.pkampus.security.config.SecurityConfig;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private pl.edu.pk.pkampus.security.config.MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==========================================
    // Register
    // ==========================================

    @Test
    void shouldRegisterResidentViaMultipart() throws Exception {
        // Arrange
        RegisterRequestDto dto = RegisterRequestDto.builder()
                .email("student@pk.edu.pl")
                .password("Password123!")
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("+48123456789")
                .dormitoryId(UUID.randomUUID())
                .declaredRoomNumber("101")
                .build();

        MockMultipartFile dataPart = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(dto)
        );

        MockMultipartFile photoPart = new MockMultipartFile(
                "photo",
                "avatar.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        when(authService.registerResident(any(), any()))
                .thenReturn(new RegisterResponseDto("Registration successful", "student@pk.edu.pl"));

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/auth/register")
                        .file(dataPart)
                        .file(photoPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("student@pk.edu.pl"));
    }

    // ==========================================
    // Email Verification
    // ==========================================

    @Test
    void shouldVerifyEmailSuccessfully() throws Exception {
        // Arrange
        when(authService.verifyEmail("valid-token"))
                .thenReturn(new VerifyEmailResponseDto("Email address confirmed successfully", UserStatus.PENDING_APPROVAL));

        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
    }

    // ==========================================
    // Login
    // ==========================================

    @Test
    void shouldLoginSuccessfully() throws Exception {
        // Arrange
        LoginRequestDto loginDto = new LoginRequestDto("student@pk.edu.pl", "Password123!");

        UserProfileDto profile = UserProfileDto.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();

        AuthResponseDto response = AuthResponseDto.builder()
                .token("jwt-mock-token")
                .tokenType("Bearer")
                .expiresInSeconds(900)
                .user(profile)
                .build();

        when(authService.login(any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt-mock-token"))
                .andExpect(jsonPath("$.data.user.email").value("student@pk.edu.pl"));
    }

    @Test
    void shouldRejectLoginWhenValidationFails() throws Exception {
        // Arrange: missing password and invalid email
        LoginRequestDto invalidDto = new LoginRequestDto("not-an-email", "");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==========================================
    // Refresh Token
    // ==========================================

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        // Arrange
        RefreshTokenRequestDto request = new RefreshTokenRequestDto("raw-refresh-token-123");
        UserProfileDto profile = UserProfileDto.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .build();
        AuthResponseDto response = AuthResponseDto.builder()
                .token("new-jwt-token")
                .tokenType("Bearer")
                .refreshToken("new-refresh-token")
                .expiresInSeconds(900)
                .refreshExpiresInSeconds(604800L)
                .user(profile)
                .build();

        when(authService.refreshToken("raw-refresh-token-123")).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token refreshed successfully"))
                .andExpect(jsonPath("$.data.token").value("new-jwt-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    void shouldRejectRefreshTokenWhenTokenIsBlank() throws Exception {
        // Arrange
        RefreshTokenRequestDto request = new RefreshTokenRequestDto("");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==========================================
    // Logout
    // ==========================================

    @Test
    void shouldLogoutSuccessfullyWithAuthenticatedUserAndRefreshToken() throws Exception {
        // Arrange
        User user = User.builder().id(UUID.randomUUID()).email("student@pk.edu.pl").build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        RefreshTokenRequestDto request = new RefreshTokenRequestDto("raw-refresh-token");
        doNothing().when(authService).logout(user.getId(), "raw-refresh-token");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(authService).logout(user.getId(), "raw-refresh-token");
    }

    @Test
    void shouldLogoutSuccessfullyWithoutRequestBody() throws Exception {
        // Arrange
        User user = User.builder().id(UUID.randomUUID()).email("student@pk.edu.pl").build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        doNothing().when(authService).logout(user.getId(), null);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout(user.getId(), null);
    }

    // ==========================================
    // Get Current User (/me)
    // ==========================================

    @Test
    void shouldGetCurrentUserProfile() throws Exception {
        // Arrange
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .firstName("Jan")
                .lastName("Kowalski")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .declaredRoomNumber("101")
                .build();

        UserProfileDto profile = UserProfileDto.builder()
                .id(user.getId())
                .email("student@pk.edu.pl")
                .firstName("Jan")
                .lastName("Kowalski")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .roomNumber("101")
                .build();

        when(authService.getCurrentUserProfile(user.getId())).thenReturn(profile);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("student@pk.edu.pl"))
                .andExpect(jsonPath("$.data.firstName").value("Jan"));
    }

    @Test
    void shouldReturnUnauthorizedWhenUserNotAuthenticatedOnMe() throws Exception {
        // Arrange: no authentication in SecurityContextHolder

        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User is not authenticated"));
    }

    // ==========================================
    // Change Password
    // ==========================================

    @Test
    void shouldChangePasswordSuccessfully() throws Exception {
        // Arrange
        User user = User.builder().id(UUID.randomUUID()).email("student@pk.edu.pl").build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        ChangePasswordRequestDto request = ChangePasswordRequestDto.builder()
                .currentPassword("OldPassword123!")
                .newPassword("NewPassword123!")
                .build();

        UserProfileDto profile = UserProfileDto.builder()
                .id(user.getId())
                .email("student@pk.edu.pl")
                .status(UserStatus.ACTIVE)
                .build();

        when(authService.changePassword(eq(user), any(ChangePasswordRequestDto.class))).thenReturn(profile);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password updated successfully"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldReturnUnauthorizedWhenUserNotAuthenticatedOnChangePassword() throws Exception {
        // Arrange: no authentication in SecurityContextHolder
        ChangePasswordRequestDto request = ChangePasswordRequestDto.builder()
                .currentPassword("OldPassword123!")
                .newPassword("NewPassword123!")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User is not authenticated"));
    }

    @Test
    void shouldRejectChangePasswordWhenValidationFails() throws Exception {
        // Arrange
        User user = User.builder().id(UUID.randomUUID()).email("student@pk.edu.pl").build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Password too short / invalid pattern
        ChangePasswordRequestDto request = ChangePasswordRequestDto.builder()
                .currentPassword("")
                .newPassword("short")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==========================================
    // Forgot Password
    // ==========================================

    @Test
    void shouldForgotPasswordSuccessfully() throws Exception {
        // Arrange
        ForgotPasswordRequestDto request = new ForgotPasswordRequestDto("student@pk.edu.pl");
        when(authService.initiatePasswordReset(any())).thenReturn(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE));
    }

    @Test
    void shouldRejectForgotPasswordWhenEmailInvalid() throws Exception {
        // Arrange
        ForgotPasswordRequestDto request = new ForgotPasswordRequestDto("invalid-email");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==========================================
    // Verify Reset Token
    // ==========================================

    @Test
    void shouldVerifyResetTokenSuccessfully() throws Exception {
        // Arrange
        VerifyResetTokenResponseDto response = new VerifyResetTokenResponseDto(true, "s***t@pk.edu.pl");
        when(authService.verifyResetToken("test-token")).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/verify-reset-token").param("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token is valid"))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.maskedEmail").value("s***t@pk.edu.pl"));
    }

    // ==========================================
    // Reset Password
    // ==========================================

    @Test
    void shouldResetPasswordSuccessfully() throws Exception {
        // Arrange
        ResetPasswordRequestDto request = new ResetPasswordRequestDto("raw-token-123", "NewPassword123!");
        doNothing().when(authService).resetPassword(any(ResetPasswordRequestDto.class));

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password has been successfully reset. You can now log in."));

        verify(authService).resetPassword(any(ResetPasswordRequestDto.class));
    }

    @Test
    void shouldRejectResetPasswordWhenValidationFails() throws Exception {
        // Arrange: password too weak (no digit, no special char)
        ResetPasswordRequestDto request = new ResetPasswordRequestDto("raw-token-123", "weak");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}

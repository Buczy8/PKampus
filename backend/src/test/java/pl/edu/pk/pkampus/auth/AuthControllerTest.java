package pl.edu.pk.pkampus.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import pl.edu.pk.pkampus.modules.auth.AuthController;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.modules.auth.dto.AuthResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyEmailResponseDto;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.modules.auth.AuthService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private pl.edu.pk.pkampus.security.config.MustChangePasswordFilter mustChangePasswordFilter;

    @MockBean
    private pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter authRateLimitFilter;

    @MockBean
    private UserRepository userRepository;

    @Test
    void shouldRegisterResidentViaMultipart() throws Exception {
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

        mockMvc.perform(multipart("/api/v1/auth/register")
                        .file(dataPart)
                        .file(photoPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("student@pk.edu.pl"));
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
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

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt-mock-token"))
                .andExpect(jsonPath("$.data.user.email").value("student@pk.edu.pl"));
    }

    @Test
    void shouldVerifyEmailSuccessfully() throws Exception {
        when(authService.verifyEmail("valid-token"))
                .thenReturn(new VerifyEmailResponseDto("Email address confirmed successfully", UserStatus.PENDING_APPROVAL));

        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
    }

    @Test
    void shouldGetCurrentUserProfile() throws Exception {
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

        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value("student@pk.edu.pl"))
                    .andExpect(jsonPath("$.data.firstName").value("Jan"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}

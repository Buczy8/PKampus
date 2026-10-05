package pl.edu.pk.pkampus.modules.user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.MustChangePasswordFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UserController slice tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("GET /api/v1/users/me")
    class GetCurrentUserTests {

        @Test
        @DisplayName("Returns 200 with UserProfileDto when user is authenticated")
        void getCurrentUserReturns200() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("student@pk.edu.pl")
                    .role(UserRole.RESIDENT)
                    .status(UserStatus.ACTIVE)
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
            );

            UserProfileDto profileDto = UserProfileDto.builder()
                    .id(userId)
                    .email("student@pk.edu.pl")
                    .firstName("Adam")
                    .lastName("Nowak")
                    .role(UserRole.RESIDENT)
                    .status(UserStatus.ACTIVE)
                    .roomNumber("101")
                    .createdAt(Instant.now())
                    .build();

            when(userService.getUserProfile(userId)).thenReturn(profileDto);

            // Act & Assert
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(userId.toString()))
                    .andExpect(jsonPath("$.data.email").value("student@pk.edu.pl"))
                    .andExpect(jsonPath("$.data.roomNumber").value("101"));

            verify(userService).getUserProfile(userId);
        }

        @Test
        @DisplayName("Returns 401 when principal is null")
        void getCurrentUserReturns401WhenNotAuthenticated() throws Exception {
            // Arrange
            SecurityContextHolder.clearContext();

            // Act & Assert
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("User is not authenticated"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/{id}")
    class GetUserByIdTests {

        @Test
        @DisplayName("Returns 404 because arbitrary profile lookup is not exposed")
        void getUserByIdIsNotExposed() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();

            // Act & Assert (no mapping exists; the service is never consulted)
            mockMvc.perform(get("/api/v1/users/{id}", userId))
                    .andExpect(status().isNotFound());

            verifyNoInteractions(userService);
        }
    }
}

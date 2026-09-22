package pl.edu.pk.pkampus.security.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.edu.pk.pkampus.modules.auth.AuthService;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SecurityFilterChain and Security Configuration Integration Tests")
class SecurityFilterChainIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private AuthService authService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==========================================
    // Public Endpoints (PermitAll)
    // ==========================================

    @Test
    @DisplayName("Anonymous user can access /api/v1/health")
    void shouldAllowAnonymousAccessToHealth() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Anonymous user can access /api/v1/dormitories")
    void shouldAllowAnonymousAccessToDormitories() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/dormitories"))
                .andExpect(status().isOk());
    }

    // ==========================================
    // Anonymous Access to Protected Endpoints (401 via JsonAuthenticationEntryPoint)
    // ==========================================

    @Test
    @DisplayName("Anonymous user accessing protected endpoint receives 401 with JsonAuthenticationEntryPoint envelope")
    void shouldReturn401WithJsonEnvelopeWhenAnonymousAccessesProtectedEndpoint() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/issues"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    // ==========================================
    // RBAC Authorization (403 via JsonAccessDeniedHandler)
    // ==========================================

    @Test
    @WithMockUser(roles = "RESIDENT")
    @DisplayName("User with RESIDENT role accessing /api/v1/admin/** receives 403 with JsonAccessDeniedHandler envelope")
    void shouldReturn403WhenResidentAccessesAdminEndpoint() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    @DisplayName("User with RECEPTIONIST role accessing /api/v1/superadmin/** receives 403")
    void shouldReturn403WhenReceptionistAccessesSuperAdminEndpoint() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/superadmin/audit-logs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));
    }

    // ==========================================
    // MustChangePasswordFilter in Security Filter Chain
    // ==========================================

    @Test
    @DisplayName("User in MUST_CHANGE_PASSWORD status is blocked with 403 on business endpoints")
    void shouldBlockMustChangePasswordUserOnProtectedBusinessEndpoint() throws Exception {
        // Arrange
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.MUST_CHANGE_PASSWORD)
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Act & Assert
        mockMvc.perform(get("/api/v1/issues"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Password change required before accessing this resource"));
    }

    @Test
    @DisplayName("User in MUST_CHANGE_PASSWORD status is allowed to access /api/v1/auth/me")
    void shouldAllowMustChangePasswordUserToAccessAuthMe() throws Exception {
        // Arrange
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.MUST_CHANGE_PASSWORD)
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserProfileDto profile = UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .status(UserStatus.MUST_CHANGE_PASSWORD)
                .build();
        when(authService.getCurrentUserProfile(user.getId())).thenReturn(profile);

        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("student@pk.edu.pl"));
    }
}

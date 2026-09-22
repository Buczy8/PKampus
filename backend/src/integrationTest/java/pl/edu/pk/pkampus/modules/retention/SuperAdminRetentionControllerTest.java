package pl.edu.pk.pkampus.modules.retention;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.edu.pk.pkampus.modules.retention.dto.DataRetentionReportDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.MustChangePasswordFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuperAdminRetentionController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SuperAdminRetentionController slice tests")
class SuperAdminRetentionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataRetentionService dataRetentionService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    private User superAdmin;

    @BeforeEach
    void setUp() {
        superAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("superadmin@pk.edu.pl")
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(superAdmin, null, superAdmin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/superadmin/retention/run executes retention and returns 200 OK")
    void runRetentionReturns200() throws Exception {
        DataRetentionReportDto report = DataRetentionReportDto.builder()
                .executedAt(Instant.now())
                .issuePhotosRemovedCount(3)
                .postsRemovedCount(2)
                .commentsRemovedCount(1)
                .laundryBookingsPurgedCount(4)
                .roomBookingsPurgedCount(5)
                .usersAnonymizedCount(1)
                .executionDurationMs(120)
                .build();

        when(dataRetentionService.runRetentionTasks()).thenReturn(report);

        mockMvc.perform(post("/api/v1/superadmin/retention/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.issuePhotosRemovedCount").value(3))
                .andExpect(jsonPath("$.data.postsRemovedCount").value(2))
                .andExpect(jsonPath("$.data.executionDurationMs").value(120))
                .andExpect(jsonPath("$.message").value("GDPR / RODO data retention completed successfully"));

        verify(dataRetentionService).runRetentionTasks();
    }
}
